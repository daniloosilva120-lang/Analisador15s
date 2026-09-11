package com.d4niboy.analisador;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RealTimeFeed {

    /*
     * Configuracao portatil do Chrome DevTools.
     * O padrao continua sendo 127.0.0.1:9222.
     */
    private static final String CHROME_HOST =
            System.getProperty("analisador.chrome.host", "127.0.0.1");

    private static final int CHROME_PORT =
            Integer.getInteger("analisador.chrome.port", 9222);

    private final RoboBotao roboBotao =
            new RoboBotao();

    private final QuoteStream quoteStream =
            new QuoteStream();

    private CandleBuilder15s candleBuilder =
            new CandleBuilder15s();

    private final Analyzer15s analyzer =
            new Analyzer15s();

    /*
     * Coleta janelas de 10 candles para formar
     * o banco de treinamento da IA.
     */
    private final CandleDatasetCollector datasetCollector =
            new CandleDatasetCollector();

    /*
     * O PaperTradeTracker não controla dinheiro.
     * Ele serve somente para acompanhar ACERTO/ERRO
     * das diferentes expirações.
     */
    private final PaperTradeTracker paperTracker =
            new PaperTradeTracker();

    private final HttpClient client =
            HttpClient.newHttpClient();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final AtomicBoolean reconectando =
            new AtomicBoolean(false);

    private final AtomicInteger proximoId =
            new AtomicInteger(1000);

    private final Set<Integer> requisicoesDom =
            ConcurrentHashMap.newKeySet();

    private volatile WebSocket socket;

    private volatile boolean executando = true;

    private volatile String ativoAtual;

    private volatile Integer payoutAtual;

    private volatile String tipoContaAtual =
            "DESCONHECIDO";

    /*
     * Saldo REALMENTE lido da interface da plataforma.
     *
     * Não é banca virtual.
     */
    private volatile double saldoAtual = 0.0;

    private volatile String saldoTextoAtual =
            "N/D";

    private static final int PAYOUT_MINIMO =
            80;

    /*
     * Configuração visual da plataforma por tipo de conta.
     *
     * DEMO:
     * - gráfico em 30s
     * - expiração em 15s
     *
     * REAL:
     * - gráfico em 1m
     * - expiração em 1m
     *
     * A conta REAL continua apenas recebendo/exibindo sinais.
     * O clique automático do RoboBotao fica restrito à DEMO.
     */
    private static final String VELA_DEMO = "30s";
    private static final String EXPIRACAO_DEMO = "00:15";
    private static final String VELA_REAL = "1m";
    private static final String EXPIRACAO_REAL = "01:00";

    private static final Pattern DEBUG =
            Pattern.compile(
                    "\"webSocketDebuggerUrl\"\\s*:\\s*\"([^\"]+)\""
            );

    private static final Pattern OPCODE =
            Pattern.compile(
                    "\"opcode\"\\s*:\\s*(\\d+)"
            );

    private static final Pattern ID =
            Pattern.compile(
                    "\"id\"\\s*:\\s*(\\d+)"
            );

    private static final Pattern VALUE =
            Pattern.compile(
                    "\"value\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
            );

    private static final Pattern PAYOUT =
            Pattern.compile(
                    "(\\d{1,3})\\s*%"
            );

    public void iniciar() {
        conectar();
    }

    private void conectar() {

        if (!executando) {
            return;
        }

        try {

            String url =
                    localizarChrome();

            if (url == null) {
                agendarReconexao();
                return;
            }

            socket =
                    client
                            .newWebSocketBuilder()
                            .buildAsync(
                                    URI.create(url),
                                    new Listener()
                            )
                            .join();

            socket.sendText(
                    "{\"id\":1,\"method\":\"Network.enable\"}",
                    true
            );

            socket.sendText(
                    "{\"id\":2,\"method\":\"Runtime.enable\"}",
                    true
            );

            reconectando.set(false);

            iniciarLeituraInterface();

        } catch (Throwable e) {

            erro(e);
            agendarReconexao();
        }
    }

    private void agendarReconexao() {

        if (
                reconectando.compareAndSet(
                        false,
                        true
                )
        ) {

            scheduler.schedule(
                    this::conectar,
                    5,
                    TimeUnit.SECONDS
            );
        }
    }

    private void erro(
            Throwable e
    ) {

        String mensagem =
                e != null
                        ? e.getMessage()
                        : null;

        System.err.println(
                "ERRO: "
                        + (
                        mensagem != null
                                ? mensagem
                                : "erro desconhecido"
                )
        );
    }

    private String localizarChrome()
            throws Exception {

        HttpRequest requisicao =
                HttpRequest
                        .newBuilder()
                        .uri(
                                URI.create(
                                        "http://" + CHROME_HOST + ":" + CHROME_PORT + "/json/list"
                                )
                        )
                        .GET()
                        .build();

        HttpResponse<String> resposta =
                client.send(
                        requisicao,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (resposta.statusCode() != 200) {
            return null;
        }

        String json =
                resposta.body();

        Pattern alvo =
                Pattern.compile(
                        "\\{(?:(?!\\}\\s*,\\s*\\{).)*?"
                                + "\\\"type\\\"\\s*:\\s*\\\"page\\\""
                                + "(?:(?!\\}\\s*,\\s*\\{).)*?\\}",
                        Pattern.DOTALL
                );

        Matcher matcher =
                alvo.matcher(json);

        while (matcher.find()) {

            String objeto =
                    matcher.group();

            String minusculo =
                    objeto.toLowerCase(
                            Locale.ROOT
                    );

            if (
                    minusculo.contains("qxbroker")
                            ||
                            minusculo.contains("quotex")
            ) {

                Matcher debug =
                        DEBUG.matcher(objeto);

                if (debug.find()) {
                    return debug.group(1);
                }
            }
        }

        return null;
    }

    private void iniciarLeituraInterface() {

        pararLeituraInterface();

        tarefaInterface =
                scheduler.scheduleAtFixedRate(
                        this::consultarInterface,
                        100,
                        500,
                        TimeUnit.MILLISECONDS
                );
    }

    private ScheduledFuture<?> tarefaInterface;

    private void pararLeituraInterface() {

        if (tarefaInterface != null) {

            tarefaInterface.cancel(false);
            tarefaInterface = null;
        }
    }

    private void consultarInterface() {

        try {

            WebSocket atual =
                    socket;

            if (
                    atual == null
                            ||
                            !executando
            ) {
                return;
            }

            int id =
                    proximoId.incrementAndGet();

            requisicoesDom.add(id);

            String expressao = """
                    (() => {

                        const visivel = (el) => {

                            if (!el) {
                                return false;
                            }

                            const r =
                                el.getBoundingClientRect();

                            const s =
                                getComputedStyle(el);

                            return (
                                r.width > 0 &&
                                r.height > 0 &&
                                s.display !== 'none' &&
                                s.visibility !== 'hidden' &&
                                Number(s.opacity || 1) !== 0
                            );
                        };


                        const texto = (el) => {

                            if (!el) {
                                return '';
                            }

                            return String(
                                el.innerText ||
                                el.textContent ||
                                ''
                            ).trim();
                        };


                        const normalizar = (str) => {

                            return String(str || '')
                                .normalize('NFD')
                                .replace(/[\\u0300-\\u036f]/g, '')
                                .replace(/\\s+/g, ' ')
                                .trim()
                                .toUpperCase();
                        };


                        const encontrarDinheiro = (str) => {

                            const txt =
                                String(str || '').trim();

                            const match =
                                txt.match(
                                    /(?:R\\$|US\\$|\\$|€|£)[ \\t]*[0-9][0-9., \\t]*/
                                );

                            if (match) {
                                return match[0].trim();
                            }

                            return '';
                        };


                        // ==========================================
                        // ATIVO SELECIONADO
                        // ==========================================

                        let tab =
                            document.querySelector(
                                '#tab-active'
                            );

                        if (!tab) {

                            tab =
                                document.querySelector(
                                    '[data-symbol][aria-selected="true"],' +
                                    '[data-symbol].active,' +
                                    '[data-symbol][class*="active"],' +
                                    '[data-symbol][class*="selected"]'
                                );
                        }

                        if (!tab) {

                            const candidatos =
                                Array.from(
                                    document.querySelectorAll(
                                        '[data-symbol]'
                                    )
                                );

                            tab =
                                candidatos.find(el => {

                                    if (!visivel(el)) {
                                        return false;
                                    }

                                    const classes =
                                        String(
                                            el.className || ''
                                        );

                                    return (
                                        el.id === 'tab-active' ||
                                        el.getAttribute(
                                            'aria-selected'
                                        ) === 'true' ||
                                        /active|selected|current/i.test(
                                            classes
                                        )
                                    );
                                }) || null;
                        }


                        let symbol = '';

                        if (tab) {

                            symbol =
                                (
                                    tab.getAttribute(
                                        'data-symbol'
                                    ) ||
                                    tab.getAttribute(
                                        'data-asset'
                                    ) ||
                                    tab.getAttribute(
                                        'data-pair'
                                    ) ||
                                    ''
                                ).trim();
                        }


                        // ==========================================
                        // PAYOUT
                        // ==========================================

                        let payout = '';

                        if (tab) {

                            const payoutEl =
                                tab.querySelector(
                                    '.ElyTP'
                                )
                                ||
                                tab.querySelector(
                                    '[class*="payout"]'
                                )
                                ||
                                tab.querySelector(
                                    '[class*="percent"]'
                                );

                            if (payoutEl) {

                                payout =
                                    texto(
                                        payoutEl
                                    );
                            }

                            if (!payout) {

                                const elementos =
                                    Array.from(
                                        tab.querySelectorAll('*')
                                    );

                                for (
                                    const el of elementos
                                ) {

                                    const txt =
                                        texto(el);

                                    if (
                                        /^\\d{1,3}\\s*%$/.test(
                                            txt
                                        )
                                    ) {

                                        payout = txt;
                                        break;
                                    }
                                }
                            }
                        }


                        // ==========================================
                        // CONTA ATIVA + SALDO
                        // ==========================================

                        let tipoConta =
                            'DESCONHECIDO';

                        let saldoStr =
                            '';


                        const todos =
                            Array.from(
                                document.querySelectorAll(
                                    'body *'
                                )
                            );


                        /*
                         * Primeiro procura elementos que parecem
                         * explicitamente selecionados/ativos.
                         */
                        const ativos =
                            todos.filter(el => {

                                if (!visivel(el)) {
                                    return false;
                                }

                                const t =
                                    normalizar(
                                        texto(el)
                                    );

                                if (
                                    !t.includes('DEMO') &&
                                    !t.includes('REAL')
                                ) {
                                    return false;
                                }

                                const classe =
                                    String(
                                        el.className || ''
                                    );

                                const selecionado =
                                    el.getAttribute(
                                        'aria-selected'
                                    ) === 'true'
                                    ||
                                    el.getAttribute(
                                        'aria-current'
                                    ) === 'true'
                                    ||
                                    el.getAttribute(
                                        'data-selected'
                                    ) === 'true'
                                    ||
                                    el.getAttribute(
                                        'data-active'
                                    ) === 'true'
                                    ||
                                    /active|selected|current|checked/i.test(
                                        classe
                                    );

                                return selecionado;
                            });


                        for (
                            const el of ativos
                        ) {

                            const t =
                                normalizar(
                                    texto(el)
                                );

                            if (
                                t.includes('DEMO')
                            ) {

                                tipoConta =
                                    'DEMO';

                            } else if (
                                t.includes('REAL')
                            ) {

                                tipoConta =
                                    'REAL';
                            }

                            let atual = el;

                            for (
                                let i = 0;
                                i < 5 && atual;
                                i++
                            ) {

                                const dinheiro =
                                    encontrarDinheiro(
                                        texto(atual)
                                    );

                                if (dinheiro) {

                                    saldoStr =
                                        dinheiro;

                                    break;
                                }

                                atual =
                                    atual.parentElement;
                            }

                            if (
                                tipoConta !==
                                'DESCONHECIDO'
                            ) {
                                break;
                            }
                        }


                        /*
                         * Segunda tentativa:
                         * procura blocos de conta visíveis.
                         */
                        if (
                            tipoConta ===
                            'DESCONHECIDO'
                        ) {

                            const blocos =
                                Array.from(
                                    document.querySelectorAll(
                                        'header,' +
                                        '[class*="account"],' +
                                        '[class*="cabinet"],' +
                                        '[class*="balance"],' +
                                        '[class*="profile"]'
                                    )
                                )
                                .filter(visivel);


                            for (
                                const el of blocos
                            ) {

                                const bruto =
                                    texto(el);

                                const t =
                                    normalizar(
                                        bruto
                                    );

                                /*
                                 * Evita escolher um menu contendo
                                 * simultaneamente REAL e DEMO.
                                 */
                                const temDemo =
                                    t.includes(
                                        'DEMO'
                                    );

                                const temReal =
                                    t.includes(
                                        'REAL'
                                    );

                                if (
                                    temDemo &&
                                    !temReal
                                ) {

                                    tipoConta =
                                        'DEMO';

                                    if (!saldoStr) {

                                        saldoStr =
                                            encontrarDinheiro(
                                                bruto
                                            );
                                    }

                                    break;
                                }

                                if (
                                    temReal &&
                                    !temDemo
                                ) {

                                    tipoConta =
                                        'REAL';

                                    if (!saldoStr) {

                                        saldoStr =
                                            encontrarDinheiro(
                                                bruto
                                            );
                                    }

                                    break;
                                }
                            }
                        }


                        /*
                         * Terceira tentativa:
                         * procura pequenos elementos da barra superior.
                         */
                        if (
                            tipoConta ===
                            'DESCONHECIDO'
                        ) {

                            const cabecalho =
                                Array.from(
                                    document.querySelectorAll(
                                        'header span,' +
                                        'header div,' +
                                        '[class*="header"] span,' +
                                        '[class*="header"] div'
                                    )
                                )
                                .filter(
                                    el =>
                                        visivel(el)
                                        &&
                                        el.children.length <= 2
                                );


                            for (
                                const el of cabecalho
                            ) {

                                const t =
                                    normalizar(
                                        texto(el)
                                    );

                                if (
                                    t === 'DEMO' ||
                                    t === 'CONTA DEMO' ||
                                    t === 'DEMO ACCOUNT'
                                ) {

                                    tipoConta =
                                        'DEMO';

                                    break;
                                }

                                if (
                                    t === 'REAL' ||
                                    t === 'CONTA REAL' ||
                                    t === 'REAL ACCOUNT'
                                ) {

                                    tipoConta =
                                        'REAL';

                                    break;
                                }
                            }
                        }


                        // ==========================================
                        // SALDO - CASO AINDA NÃO TENHA SIDO ACHADO
                        // ==========================================

                        if (!saldoStr) {

                            const seletoresSaldo = [

                                'header [class*="balance"]',

                                '[class*="header"] [class*="balance"]',

                                'header [class*="account"] [class*="value"]',

                                '[class*="cabinet"] [class*="balance"]',

                                '[class*="account"] [class*="balance"]',

                                '[class*="balance"] [class*="value"]'
                            ];


                            for (
                                const seletor of seletoresSaldo
                            ) {

                                const elementos =
                                    Array.from(
                                        document.querySelectorAll(
                                            seletor
                                        )
                                    );

                                for (
                                    const el of elementos
                                ) {

                                    if (!visivel(el)) {
                                        continue;
                                    }

                                    const dinheiro =
                                        encontrarDinheiro(
                                            texto(el)
                                        );

                                    if (dinheiro) {

                                        saldoStr =
                                            dinheiro;

                                        break;
                                    }
                                }

                                if (saldoStr) {
                                    break;
                                }
                            }
                        }


                        /*
                         * Último fallback para saldo:
                         * valores monetários visíveis no cabeçalho.
                         */
                        if (!saldoStr) {

                            const elementos =
                                Array.from(
                                    document.querySelectorAll(
                                        'header span,' +
                                        'header div,' +
                                        '[class*="header"] span,' +
                                        '[class*="header"] div'
                                    )
                                );


                            for (
                                const el of elementos
                            ) {

                                if (
                                    !visivel(el)
                                    ||
                                    el.children.length > 1
                                ) {
                                    continue;
                                }

                                const dinheiro =
                                    encontrarDinheiro(
                                        texto(el)
                                    );

                                if (dinheiro) {

                                    saldoStr =
                                        dinheiro;

                                    break;
                                }
                            }
                        }


                        return [

                            symbol ||
                            'SEM_ATIVO',

                            payout ||
                            'SEM_PAYOUT',

                            tipoConta,

                            saldoStr ||
                            'SEM_SALDO'

                        ].join('|');

                    })()
                    """;

            String comando =
                    "{\"id\":"
                            + id
                            + ",\"method\":\"Runtime.evaluate\","
                            + "\"params\":{\"expression\":"
                            + jsonString(expressao)
                            + ",\"returnByValue\":true}}";

            atual.sendText(
                    comando,
                    true
            );

        } catch (Throwable e) {

            System.err.println(
                    "ERRO ao consultar interface: "
                            + e.getMessage()
            );
        }
    }

    private String jsonString(
            String str
    ) {

        return "\""
                + str
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\t",
                        "\\t"
                )
                + "\"";
    }

    private class Listener
            implements WebSocket.Listener {

        private final StringBuilder buffer =
                new StringBuilder();

        @Override
        public void onOpen(
                WebSocket webSocket
        ) {

            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(
                WebSocket webSocket,
                CharSequence data,
                boolean last
        ) {

            try {

                buffer.append(data);

                if (last) {

                    String mensagem =
                            buffer.toString();

                    buffer.setLength(0);

                    processar(
                            mensagem
                    );
                }

            } catch (Throwable e) {

                erro(e);
            }

            webSocket.request(1);

            return CompletableFuture
                    .completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(
                WebSocket webSocket,
                int statusCode,
                String reason
        ) {

            pararLeituraInterface();

            socket = null;

            agendarReconexao();

            return CompletableFuture
                    .completedFuture(null);
        }

        @Override
        public void onError(
                WebSocket webSocket,
                Throwable error
        ) {

            pararLeituraInterface();

            socket = null;

            erro(error);

            agendarReconexao();
        }
    }

    private void processar(
            String mensagem
    ) {

        Matcher idMatcher =
                ID.matcher(
                        mensagem
                );

        if (idMatcher.find()) {

            int id;

            try {

                id =
                        Integer.parseInt(
                                idMatcher.group(1)
                        );

            } catch (NumberFormatException e) {

                id = -1;
            }

            if (
                    id >= 0
                            &&
                            requisicoesDom.remove(id)
            ) {

                processarRespostaInterface(
                        mensagem
                );

                return;
            }
        }

        if (
                !mensagem.contains(
                        "Network.webSocketFrameReceived"
                )
        ) {
            return;
        }

        Matcher op =
                OPCODE.matcher(
                        mensagem
                );

        if (!op.find()) {
            return;
        }

        int opcode;

        try {

            opcode =
                    Integer.parseInt(
                            op.group(1)
                    );

        } catch (NumberFormatException e) {

            return;
        }

        if (opcode != 2) {
            return;
        }

        String payload =
                extrairPayload(
                        mensagem
                );

        if (payload == null) {
            return;
        }

        try {

            byte[] dados =
                    Base64
                            .getDecoder()
                            .decode(
                                    payload
                            );

            processarBinario(
                    dados
            );

        } catch (IllegalArgumentException e) {

            processarBinario(
                    payload.getBytes(
                            StandardCharsets.ISO_8859_1
                    )
            );
        }
    }

    private String extrairPayload(
            String mensagem
    ) {

        Pattern payloadPattern =
                Pattern.compile(
                        "\"payloadData\"\\s*:\\s*\"([^\"]+)\""
                );

        Matcher matcher =
                payloadPattern.matcher(
                        mensagem
                );

        return matcher.find()
                ? matcher.group(1)
                : null;
    }

    private String desserializarJson(
            String str
    ) {

        return str
                .replace(
                        "\\\"",
                        "\""
                )
                .replace(
                        "\\n",
                        "\n"
                )
                .replace(
                        "\\r",
                        "\r"
                )
                .replace(
                        "\\t",
                        "\t"
                )
                .replace(
                        "\\\\",
                        "\\"
                );
    }

    private void processarRespostaInterface(
            String mensagem
    ) {

        Matcher value =
                VALUE.matcher(
                        mensagem
                );

        if (!value.find()) {
            return;
        }

        String texto =
                desserializarJson(
                        value.group(1)
                );

        if (
                texto == null
                        ||
                        texto.isBlank()
        ) {
            return;
        }

        String[] partes =
                texto.split(
                        "\\|",
                        -1
                );

        if (partes.length < 4) {
            return;
        }

        String novoAtivo =
                partes[0].trim();

        String payoutStr =
                partes[1].trim();

        String novaConta =
                partes[2].trim();

        String saldoStr =
                partes[3].trim();

        Integer novoPayout =
                interpretarPayout(
                        payoutStr
                );

        Double novoSaldo =
                interpretarSaldo(
                        saldoStr
                );

        processarConta(
                novaConta
        );

        processarSaldo(
                novoSaldo,
                saldoStr
        );

        if (
                novoAtivo.isBlank()
                        ||
                        novoAtivo.equalsIgnoreCase(
                                "SEM_ATIVO"
                        )
        ) {
            return;
        }

        boolean mudouAtivo =
                ativoAtual == null
                        ||
                        !ativoAtual.equalsIgnoreCase(
                                novoAtivo
                        );

        if (mudouAtivo) {

            payoutAtual =
                    novoPayout;

            trocarAtivo(
                    novoAtivo
            );

            return;
        }

        if (
                novoPayout != null
                        &&
                        !novoPayout.equals(
                                payoutAtual
                        )
        ) {

            payoutAtual =
                    novoPayout;

            System.out.println(
                    "PAYOUT: "
                            + novoPayout
                            + "%"
            );
        }
    }

    private void processarConta(
            String novaConta
    ) {

        if (
                novaConta == null
                        ||
                        novaConta.isBlank()
        ) {
            return;
        }

        String normalizada =
                novaConta
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !normalizada.equals("DEMO")
                        &&
                        !normalizada.equals("REAL")
        ) {
            return;
        }

        if (
                normalizada.equalsIgnoreCase(
                        tipoContaAtual
                )
        ) {
            return;
        }

        tipoContaAtual =
                normalizada;

        System.out.println(
                "CONTA: "
                        + tipoContaAtual
        );

        configurarTemposDaConta();
    }

    /*
     * Mantém a interface da plataforma no tempo desejado para a conta.
     *
     * O método trabalha somente nos controles de tempo.
     * Ele NÃO troca a conta e NÃO clica em PARA CIMA/PARA BAIXO.
     */
    private void configurarTemposDaConta() {

        if (
                tipoContaAtual == null
                        ||
                        tipoContaAtual.equals("DESCONHECIDO")
        ) {
            return;
        }

        String vela;
        String expiracao;

        if (
                tipoContaAtual.equals("DEMO")
        ) {

            vela = VELA_DEMO;
            expiracao = EXPIRACAO_DEMO;

        } else if (
                tipoContaAtual.equals("REAL")
        ) {

            vela = VELA_REAL;
            expiracao = EXPIRACAO_REAL;

        } else {

            return;
        }

        WebSocket atual =
                socket;

        if (atual == null) {
            return;
        }

        try {

            int id =
                    proximoId.incrementAndGet();

            String expressao = """
                    (async () => {

                        const alvoVela = %s;
                        const alvoExpiracao = %s;

                        const visivel = (el) => {

                            if (!el) {
                                return false;
                            }

                            const r =
                                el.getBoundingClientRect();

                            const s =
                                getComputedStyle(el);

                            return (
                                r.width > 0 &&
                                r.height > 0 &&
                                s.display !== 'none' &&
                                s.visibility !== 'hidden' &&
                                Number(s.opacity || 1) !== 0
                            );
                        };

                        const texto = (el) =>
                            String(
                                el?.innerText ||
                                el?.textContent ||
                                ''
                            )
                            .replace(/\s+/g, ' ')
                            .trim();

                        const clicarTextoExato = (valor) => {

                            const elementos =
                                Array.from(
                                    document.querySelectorAll(
                                        'button,[role="button"],span,div'
                                    )
                                )
                                .filter(
                                    el =>
                                        visivel(el) &&
                                        texto(el) === valor
                                );

                            if (elementos.length === 0) {
                                return false;
                            }

                            elementos.sort(
                                (a, b) => {

                                    const areaA =
                                        a.getBoundingClientRect().width *
                                        a.getBoundingClientRect().height;

                                    const areaB =
                                        b.getBoundingClientRect().width *
                                        b.getBoundingClientRect().height;

                                    return areaA - areaB;
                                }
                            );

                            const alvo =
                                elementos[0]
                                    .closest(
                                        'button,[role="button"]'
                                    )
                                || elementos[0];

                            alvo.click();

                            return true;
                        };

                        const ajustarInputTempo = (valor) => {

                            const inputs =
                                Array.from(
                                    document.querySelectorAll(
                                        'input'
                                    )
                                )
                                .filter(visivel);

                            for (const input of inputs) {

                                const atual =
                                    String(
                                        input.value || ''
                                    ).trim();

                                if (
                                    /^\\d{1,2}:\\d{2}$/.test(atual) ||
                                    /^\\d{2}:\\d{2}$/.test(atual)
                                ) {

                                    const setter =
                                        Object.getOwnPropertyDescriptor(
                                            HTMLInputElement.prototype,
                                            'value'
                                        )?.set;

                                    if (setter) {
                                        setter.call(input, valor);
                                    } else {
                                        input.value = valor;
                                    }

                                    input.dispatchEvent(
                                        new Event(
                                            'input',
                                            {
                                                bubbles: true
                                            }
                                        )
                                    );

                                    input.dispatchEvent(
                                        new Event(
                                            'change',
                                            {
                                                bubbles: true
                                            }
                                        )
                                    );

                                    input.blur();

                                    return true;
                                }
                            }

                            return false;
                        };

                        const velaOk =
                            clicarTextoExato(
                                alvoVela
                            );

                        await new Promise(
                            resolve =>
                                setTimeout(
                                    resolve,
                                    100
                                )
                        );

                        let expiracaoOk =
                            ajustarInputTempo(
                                alvoExpiracao
                            );

                        if (!expiracaoOk) {

                            /*
                             * Primeiro tenta clicar no próprio valor atual
                             * para abrir o seletor de expiração.
                             */
                            const valoresTempo =
                                Array.from(
                                    document.querySelectorAll(
                                        'button,[role="button"],span,div'
                                    )
                                )
                                .filter(
                                    el =>
                                        visivel(el) &&
                                        /^\\d{1,2}:\\d{2}$/.test(
                                            texto(el)
                                        )
                                );

                            if (valoresTempo.length > 0) {

                                valoresTempo.sort(
                                    (a, b) => {

                                        const areaA =
                                            a.getBoundingClientRect().width *
                                            a.getBoundingClientRect().height;

                                        const areaB =
                                            b.getBoundingClientRect().width *
                                            b.getBoundingClientRect().height;

                                        return areaA - areaB;
                                    }
                                );

                                const controle =
                                    valoresTempo[0]
                                        .closest(
                                            'button,[role="button"]'
                                        )
                                    || valoresTempo[0];

                                if (
                                    texto(controle) !==
                                    alvoExpiracao
                                ) {

                                    controle.click();

                                    await new Promise(
                                        resolve =>
                                            setTimeout(
                                                resolve,
                                                150
                                            )
                                    );

                                    expiracaoOk =
                                        clicarTextoExato(
                                            alvoExpiracao
                                        );
                                } else {

                                    expiracaoOk = true;
                                }
                            }
                        }

                        return JSON.stringify({
                            vela: velaOk,
                            expiracao: expiracaoOk
                        });

                    })()
                    """
                    .formatted(
                            jsonString(vela),
                            jsonString(expiracao)
                    );

            String comando =
                    "{\"id\":"
                            + id
                            + ",\"method\":\"Runtime.evaluate\","
                            + "\"params\":{\"expression\":"
                            + jsonString(expressao)
                            + ",\"awaitPromise\":true,"
                            + "\"returnByValue\":true}}";

            atual.sendText(
                    comando,
                    true
            );

        } catch (Throwable e) {

            System.err.println(
                    "ERRO ao configurar tempos da conta: "
                            + e.getMessage()
            );
        }
    }

    private void processarSaldo(
            Double novoSaldo,
            String saldoStr
    ) {

        if (novoSaldo == null) {
            return;
        }

        if (
                Double.compare(
                        novoSaldo,
                        saldoAtual
                ) == 0
                        &&
                        saldoStr.equals(
                                saldoTextoAtual
                        )
        ) {
            return;
        }

        saldoAtual =
                novoSaldo;

        saldoTextoAtual =
                saldoStr;

        System.out.println(
                "SALDO: "
                        + saldoStr
        );
    }

    private Double interpretarSaldo(
            String texto
    ) {

        if (
                texto == null
                        ||
                        texto.isBlank()
                        ||
                        texto.equalsIgnoreCase(
                                "SEM_SALDO"
                        )
        ) {
            return null;
        }

        try {

            String limpo =
                    texto
                            .replaceAll(
                                    "[^0-9.,]",
                                    ""
                            );

            if (limpo.isBlank()) {
                return null;
            }

            int virgula =
                    limpo.lastIndexOf(',');

            int ponto =
                    limpo.lastIndexOf('.');

            /*
             * Casos como:
             *
             * 9,947.85
             * 9.947,85
             */
            if (
                    virgula >= 0
                            &&
                            ponto >= 0
            ) {

                if (ponto > virgula) {

                    limpo =
                            limpo
                                    .replace(
                                            ",",
                                            ""
                                    );

                } else {

                    limpo =
                            limpo
                                    .replace(
                                            ".",
                                            ""
                                    )
                                    .replace(
                                            ",",
                                            "."
                                    );
                }

            } else if (virgula >= 0) {

                int casas =
                        limpo.length()
                                - virgula
                                - 1;

                if (casas == 2) {

                    limpo =
                            limpo.replace(
                                    ",",
                                    "."
                            );

                } else {

                    limpo =
                            limpo.replace(
                                    ",",
                                    ""
                            );
                }

            } else if (ponto >= 0) {

                int primeiro =
                        limpo.indexOf('.');

                int ultimo =
                        limpo.lastIndexOf('.');

                if (primeiro != ultimo) {

                    String depoisUltimo =
                            limpo.substring(
                                    ultimo + 1
                            );

                    if (
                            depoisUltimo.length()
                                    == 2
                    ) {

                        String inteiro =
                                limpo.substring(
                                                0,
                                                ultimo
                                        )
                                        .replace(
                                                ".",
                                                ""
                                        );

                        limpo =
                                inteiro
                                        + "."
                                        + depoisUltimo;

                    } else {

                        limpo =
                                limpo.replace(
                                        ".",
                                        ""
                                );
                    }
                }
            }

            double saldo =
                    Double.parseDouble(
                            limpo
                    );

            if (
                    Double.isNaN(saldo)
                            ||
                            Double.isInfinite(saldo)
                            ||
                            saldo < 0
            ) {
                return null;
            }

            return saldo;

        } catch (Exception e) {

            return null;
        }
    }

    private Integer interpretarPayout(
            String texto
    ) {

        if (
                texto == null
                        ||
                        texto.equalsIgnoreCase(
                                "SEM_PAYOUT"
                        )
        ) {
            return null;
        }

        Matcher matcher =
                PAYOUT.matcher(
                        texto
                );

        if (!matcher.find()) {
            return null;
        }

        try {

            int valor =
                    Integer.parseInt(
                            matcher.group(1)
                    );

            return (
                    valor >= 0
                            &&
                            valor <= 100
            )
                    ? valor
                    : null;

        } catch (NumberFormatException e) {

            return null;
        }
    }

    private void processarBinario(
            byte[] dados
    ) {

        List<QuoteStream.Quote> quotes =
                quoteStream.parseBinaryMessage(
                        dados
                );

        if (quotes.isEmpty()) {
            return;
        }

        String selecionado =
                ativoAtual;

        if (
                selecionado == null
                        ||
                        selecionado.isBlank()
        ) {
            return;
        }

        for (
                QuoteStream.Quote quote : quotes
        ) {

            if (
                    quote.ativo() == null
                            ||
                            quote.ativo().isBlank()
            ) {
                continue;
            }

            if (
                    !quote.ativo()
                            .equalsIgnoreCase(
                                    selecionado
                            )
            ) {
                continue;
            }

            processarQuote(
                    quote
            );
        }
    }

    private String formatarAtivo(
            String ativo
    ) {

        return ativo != null
                ? ativo
                .toUpperCase(
                        Locale.ROOT
                )
                .replace(
                        "_OTC",
                        " (OTC)"
                )
                : "";
    }

    private synchronized void trocarAtivo(
            String novoAtivo
    ) {

        if (
                novoAtivo == null
                        ||
                        novoAtivo.isBlank()
        ) {
            return;
        }

        if (
                ativoAtual != null
                        &&
                        ativoAtual.equalsIgnoreCase(
                                novoAtivo
                        )
        ) {
            return;
        }

        String anterior =
                ativoAtual;

        if (anterior != null) {

            paperTracker.cancelarAtivo(
                    anterior
            );
        }

        ativoAtual =
                novoAtivo;

        candleBuilder =
                new CandleBuilder15s();

        System.out.println();

        if (anterior == null) {

            System.out.println(
                    "ATIVO: "
                            + formatarAtivo(
                            ativoAtual
                    )
            );

        } else {

            System.out.println(
                    "ATIVO ALTERADO: "
                            + formatarAtivo(
                            anterior
                    )
                            + " -> "
                            + formatarAtivo(
                            ativoAtual
                    )
            );
        }

        System.out.println(
                "CONTA: "
                        + tipoContaAtual
                        + " | SALDO: "
                        + saldoTextoAtual
                        + " | PAYOUT: "
                        + (
                        payoutAtual != null
                                ? payoutAtual + "%"
                                : "N/D"
                )
        );

        System.out.println();
    }

    private void processarQuote(
            QuoteStream.Quote quote
    ) {

        /*
         * Apenas acompanha estatísticas.
         * Não existe banca virtual.
         */
        paperTracker.onQuote(
                quote.ativo(),
                quote.preco()
        );

        int quantidadeAntes =
                candleBuilder
                        .getCandlesFechados()
                        .size();

        candleBuilder.adicionarQuote(
                quote
        );

        List<Candle> candles =
                candleBuilder
                        .getCandlesFechados();

        if (
                candles.size()
                        <= quantidadeAntes
        ) {
            return;
        }

        /*
         * A IA usa uma janela móvel de 10 candles.
         * A amostra só é gravada quando o 11º candle fecha,
         * porque ele fornece o resultado real dos 15s seguintes.
         */
        datasetCollector.registrarJanela(
                quote.ativo(),
                candles
        );

        if (candles.size() < 10) {
            return;
        }

        Signal sinal =
                analyzer.analisar(
                        candles
                );

        boolean direcaoValida =
                sinal.direcao()
                        .equals(
                                "PARA CIMA"
                        )
                        ||
                        sinal.direcao()
                                .equals(
                                        "PARA BAIXO"
                                );

        if (!direcaoValida) {
            return;
        }

        if (
                payoutAtual == null
                        ||
                        payoutAtual
                                < PAYOUT_MINIMO
        ) {

            System.out.println(
                    "⚠ Entrada ignorada: payout "
                            + (
                            payoutAtual != null
                                    ? payoutAtual + "%"
                                    : "N/D"
                    )
                            + " abaixo do mínimo de "
                            + PAYOUT_MINIMO
                            + "%."
            );

            return;
        }

        mostrar(
                quote,
                sinal,
                candles.size()
        );

        /*
         * Todo sinal qualificado vai para a tela Java.
         *
         * A tela mostra apenas o sinal atual:
         * quando chega outro, ele substitui o anterior.
         */
        SignalHttpServer.publicarSinal(
                quote.ativo(),
                sinal.direcao(),
                payoutAtual.doubleValue()
        );

        /*
         * Estatística e clique automático ficam na DEMO,
         * onde a expiração configurada é de 15 segundos.
         */
        if (
                "DEMO".equalsIgnoreCase(
                        tipoContaAtual
                )
        ) {

            paperTracker.abrir(
                    quote.ativo(),
                    sinal.direcao(),
                    quote.preco(),
                    payoutAtual.doubleValue()
            );

            roboBotao.receberSinal(
                    sinal.direcao()
            );
        }
    }

    private void mostrar(
            QuoteStream.Quote quote,
            Signal sinal,
            int numeroCandles
    ) {

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "SINAL QUALIFICADO"
        );

        System.out.println(
                "Ativo: "
                        + formatarAtivo(
                        quote.ativo()
                )
        );

        System.out.println(
                "Candles: "
                        + numeroCandles
        );

        System.out.println(
                "Preço: "
                        + quote.preco()
        );

        String icone =
                sinal.direcao()
                        .equals(
                                "PARA CIMA"
                        )
                        ? "🟢"
                        : "🔴";

        System.out.println(
                "SINAL: "
                        + icone
                        + " "
                        + sinal.direcao()
        );

        System.out.println(
                "CONTA: "
                        + tipoContaAtual
                        + " | SALDO: "
                        + saldoTextoAtual
                        + " | PAYOUT: "
                        + payoutAtual
                        + "%"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();
    }
}

