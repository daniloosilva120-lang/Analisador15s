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

    private final QuoteStream quoteStream =
            new QuoteStream();

    private CandleBuilder15s candleBuilder =
            new CandleBuilder15s();

    private final Analyzer15s analyzer =
            new Analyzer15s();

    /*
     * Simulador de entradas.
     *
     * NÃO envia ordens para a plataforma.
     */
    private final PaperTradeTracker paperTracker =
            new PaperTradeTracker();

    private final HttpClient client =
            HttpClient.newHttpClient();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    private final AtomicBoolean reconectando =
            new AtomicBoolean(false);

    /*
     * IDs usados pelos comandos enviados ao Chrome.
     */
    private final AtomicInteger proximoId =
            new AtomicInteger(1000);

    /*
     * Guarda os IDs das consultas do DOM.
     */
    private final Set<Integer> requisicoesDom =
            ConcurrentHashMap.newKeySet();

    private volatile WebSocket socket;

    private volatile boolean executando =
            true;

    /*
     * Ativo realmente selecionado na tela.
     *
     * Exemplo:
     * USDMXN_otc
     */
    private volatile String ativoAtual =
            null;

    /*
     * Payout mostrado pela plataforma.
     *
     * Exemplo:
     * 95
     */
    private volatile Integer payoutAtual =
            null;

    private volatile Double ultimoPreco =
            null;

    /*
     * Tarefa que consulta o gráfico selecionado
     * periodicamente.
     */
    private ScheduledFuture<?> tarefaInterface;

    /*
     * URL WebSocket de depuração do Chrome.
     */
    private static final Pattern DEBUG =
            Pattern.compile(
                    "\"webSocketDebuggerUrl\"\\s*:\\s*\"([^\"]+)\""
            );

    /*
     * Opcode dos frames WebSocket capturados.
     */
    private static final Pattern OPCODE =
            Pattern.compile(
                    "\"opcode\"\\s*:\\s*(\\d+)"
            );

    /*
     * ID das respostas do Chrome DevTools Protocol.
     */
    private static final Pattern ID =
            Pattern.compile(
                    "\"id\"\\s*:\\s*(\\d+)"
            );

    /*
     * Valor retornado por Runtime.evaluate.
     */
    private static final Pattern VALUE =
            Pattern.compile(
                    "\"value\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
            );

    /*
     * Percentual do payout.
     */
    private static final Pattern PAYOUT =
            Pattern.compile(
                    "(\\d{1,3})\\s*%"
            );

    public void iniciar() {

        System.out.println();
        System.out.println(
                "========================================"
        );
        System.out.println(
                "   ANALISADOR 15s - DADOS REAIS"
        );
        System.out.println(
                "========================================"
        );
        System.out.println();

        System.out.println(
                "REAL TIME FEED"
        );
        System.out.println();

        System.out.println(
                "Detecção do gráfico selecionado ativada."
        );

        System.out.println(
                "O programa seguirá somente o ativo que está visível."
        );

        System.out.println();

        System.out.println(
                "Leitura automática de payout ativada."
        );

        System.out.println();

        System.out.println(
                "Operações automáticas de TESTE ativadas."
        );

        System.out.println(
                "Nenhuma ordem será enviada para a plataforma."
        );

        System.out.println();

        conectar();
    }

    /*
     * ============================================================
     * CONEXÃO COM CHROME
     * ============================================================
     */

    private void conectar() {

        if (!executando) {
            return;
        }

        try {

            System.out.println(
                    "Procurando Chrome..."
            );

            String url =
                    localizarChrome();

            if (url == null) {

                System.out.println(
                        "Chrome de depuração não encontrado."
                );

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

            System.out.println(
                    "Conectado ao Chrome."
            );

            /*
             * Ativa captura de rede.
             */
            socket.sendText(
                    """
                    {"id":1,"method":"Network.enable"}
                    """.trim(),
                    true
            );

            /*
             * Ativa Runtime.evaluate.
             *
             * É o Runtime que permite ler:
             *
             * #tab-active
             * data-symbol
             * payout
             */
            socket.sendText(
                    """
                    {"id":2,"method":"Runtime.enable"}
                    """.trim(),
                    true
            );

            reconectando.set(false);

            iniciarLeituraInterface();

            System.out.println(
                    "Captura de preços ativada."
            );

            System.out.println(
                    "Aguardando identificação do gráfico selecionado..."
            );

            System.out.println();

        } catch (Throwable e) {

            erro(e);

            agendarReconexao();
        }
    }

    /*
     * Procura a aba da plataforma no Chrome
     * aberto com porta de depuração 9222.
     */
    private String localizarChrome()
            throws Exception {

        HttpRequest req =
                HttpRequest
                        .newBuilder()
                        .uri(
                                URI.create(
                                        "http://127.0.0.1:9222/json"
                                )
                        )
                        .GET()
                        .build();

        HttpResponse<String> res =
                client.send(
                        req,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (res.statusCode() != 200) {
            return null;
        }

        String json =
                res.body();

        /*
         * Primeiro procura especificamente
         * a aba Quotex/QXBroker.
         */
        for (
                String objeto :
                json.split("\\},\\s*\\{")
        ) {

            String texto =
                    objeto.toLowerCase(
                            Locale.ROOT
                    );

            if (
                    texto.contains("quotex")
                            ||
                            texto.contains("qxbroker")
            ) {

                Matcher m =
                        DEBUG.matcher(objeto);

                if (m.find()) {
                    return m.group(1);
                }
            }
        }

        /*
         * Fallback:
         * primeira aba disponível.
         */
        Matcher m =
                DEBUG.matcher(json);

        return m.find()
                ? m.group(1)
                : null;
    }

    /*
     * ============================================================
     * LEITURA DA INTERFACE
     * ============================================================
     */

    private void iniciarLeituraInterface() {

        pararLeituraInterface();

        /*
         * Consulta duas vezes por segundo.
         *
         * Assim uma mudança de gráfico é percebida
         * rapidamente sem precisar usar reconhecimento
         * de imagem.
         */
        tarefaInterface =
                scheduler.scheduleAtFixedRate(
                        this::consultarInterface,
                        100,
                        500,
                        TimeUnit.MILLISECONDS
                );
    }

    private void pararLeituraInterface() {

        if (tarefaInterface != null) {

            tarefaInterface.cancel(false);

            tarefaInterface =
                    null;
        }
    }

    /*
     * Pergunta diretamente ao navegador:
     *
     * Qual elemento possui id="tab-active"?
     *
     * Depois lê:
     *
     * data-symbol
     *
     * e
     *
     * .ElyTP
     */
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

            /*
             * IMPORTANTE:
             *
             * Pela inspeção da página encontramos:
             *
             * <div id="tab-active"
             *      data-symbol="USDMXN_otc">
             *
             * e dentro dele:
             *
             * <div class="ElyTP">95%</div>
             *
             * Também existe um fallback procurando qualquer
             * percentual dentro da aba ativa caso o nome
             * da classe ElyTP seja alterado futuramente.
             */
            String expressao =
                    """
                    (() => {
                        const tab = document.querySelector('#tab-active');

                        if (!tab) {
                            return '';
                        }

                        const symbol =
                            (tab.getAttribute('data-symbol') || '').trim();

                        let payout = '';

                        const payoutEl =
                            tab.querySelector('.ElyTP');

                        if (payoutEl) {
                            payout = (payoutEl.textContent || '').trim();
                        }

                        if (!payout) {
                            const elementos =
                                Array.from(tab.querySelectorAll('*'));

                            for (const el of elementos) {
                                const txt =
                                    (el.textContent || '').trim();

                                if (/^\\d{1,3}%$/.test(txt)) {
                                    payout = txt;
                                    break;
                                }
                            }
                        }

                        return symbol + '|' + payout;
                    })()
                    """;

            String comando =
                    "{\"id\":"
                            + id
                            + ",\"method\":\"Runtime.evaluate\","
                            + "\"params\":{"
                            + "\"expression\":"
                            + jsonString(expressao)
                            + ","
                            + "\"returnByValue\":true"
                            + "}}";

            atual.sendText(
                    comando,
                    true
            );

        } catch (Throwable e) {

            /*
             * Falha momentânea na leitura da interface
             * não deve derrubar o feed de preços.
             */
        }
    }

    /*
     * ============================================================
     * LISTENER DO CHROME
     * ============================================================
     */

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

                    processar(mensagem);
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

            System.out.println(
                    "Conexão encerrada: "
                            + statusCode
            );

            pararLeituraInterface();

            socket =
                    null;

            agendarReconexao();

            return CompletableFuture
                    .completedFuture(null);
        }

        @Override
        public void onError(
                WebSocket webSocket,
                Throwable error
        ) {

            erro(error);

            pararLeituraInterface();

            socket =
                    null;

            agendarReconexao();
        }
    }

    /*
     * ============================================================
     * PROCESSAMENTO DAS MENSAGENS DO CDP
     * ============================================================
     */

    private void processar(
            String mensagem
    ) {

        /*
         * PRIMEIRO:
         * verifica se é resposta de uma consulta
         * Runtime.evaluate que fizemos.
         */
        Matcher idMatcher =
                ID.matcher(mensagem);

        if (idMatcher.find()) {

            int id;

            try {

                id =
                        Integer.parseInt(
                                idMatcher.group(1)
                        );

            } catch (NumberFormatException e) {

                id =
                        -1;
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

        /*
         * Depois processa os frames WebSocket
         * recebidos da plataforma.
         */
        if (
                !mensagem.contains(
                        "Network.webSocketFrameReceived"
                )
        ) {
            return;
        }

        Matcher op =
                OPCODE.matcher(mensagem);

        if (!op.find()) {
            return;
        }

        int opcode =
                Integer.parseInt(
                        op.group(1)
                );

        /*
         * Cotações chegam em frames binários.
         */
        if (opcode != 2) {
            return;
        }

        String payload =
                extrairPayload(mensagem);

        if (payload == null) {
            return;
        }

        try {

            byte[] dados =
                    Base64
                            .getDecoder()
                            .decode(payload);

            processarBinario(dados);

        } catch (
                IllegalArgumentException e
        ) {

            /*
             * Fallback caso o Chrome entregue
             * conteúdo diretamente.
             */
            processarBinario(
                    payload.getBytes(
                            StandardCharsets.ISO_8859_1
                    )
            );
        }
    }

    /*
     * ============================================================
     * RESPOSTA DO DOM
     * ============================================================
     */

    private void processarRespostaInterface(
            String mensagem
    ) {

        Matcher value =
                VALUE.matcher(mensagem);

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

        /*
         * Formato retornado pelo JavaScript:
         *
         * USDMXN_otc|95%
         */
        String[] partes =
                texto.split(
                        "\\|",
                        -1
                );

        if (partes.length == 0) {
            return;
        }

        String novoAtivo =
                partes[0].trim();

        if (novoAtivo.isBlank()) {
            return;
        }

        Integer novoPayout =
                null;

        if (partes.length >= 2) {

            novoPayout =
                    interpretarPayout(
                            partes[1]
                    );
        }

        boolean mudouAtivo =
                ativoAtual == null
                        ||
                        !ativoAtual.equalsIgnoreCase(
                                novoAtivo
                        );

        /*
         * O payout deve ser atualizado ANTES da troca
         * para já aparecer no bloco do novo ativo.
         */
        if (mudouAtivo) {

            payoutAtual =
                    novoPayout;

            trocarAtivo(
                    novoAtivo
            );

            return;
        }

        /*
         * Mesmo ativo, mas payout mudou.
         *
         * Exemplo:
         *
         * 95% -> 93%
         */
        if (
                novoPayout != null
                        &&
                        !novoPayout.equals(
                                payoutAtual
                        )
        ) {

            Integer anterior =
                    payoutAtual;

            payoutAtual =
                    novoPayout;

            System.out.println();

            System.out.println(
                    "PAYOUT ALTERADO ["
                            +
                            formatarAtivo(
                                    ativoAtual
                            )
                            +
                            "]: "
                            +
                            (
                                    anterior == null
                                            ?
                                            "?"
                                            :
                                            anterior + "%"
                            )
                            +
                            " -> "
                            +
                            novoPayout
                            +
                            "%"
            );

            System.out.println();
        }
    }

    private Integer interpretarPayout(
            String texto
    ) {

        if (texto == null) {
            return null;
        }

        Matcher m =
                PAYOUT.matcher(texto);

        if (!m.find()) {
            return null;
        }

        try {

            int valor =
                    Integer.parseInt(
                            m.group(1)
                    );

            if (
                    valor < 0
                            ||
                            valor > 100
            ) {
                return null;
            }

            return valor;

        } catch (
                NumberFormatException e
        ) {

            return null;
        }
    }

    /*
     * ============================================================
     * FRAMES BINÁRIOS / COTAÇÕES
     * ============================================================
     */

    private void processarBinario(
            byte[] dados
    ) {

        List<QuoteStream.Quote> quotes =
                quoteStream
                        .parseBinaryMessage(
                                dados
                        );

        if (quotes.isEmpty()) {
            return;
        }

        /*
         * Sem saber qual gráfico está selecionado,
         * não utilizamos nenhuma cotação.
         *
         * Isso evita analisar acidentalmente outro
         * ativo transmitido pelo WebSocket.
         */
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
                QuoteStream.Quote q :
                quotes
        ) {

            if (
                    q.ativo() == null
                            ||
                            q.ativo().isBlank()
            ) {
                continue;
            }

            /*
             * Esta é a mudança principal:
             *
             * somente processa o ativo cujo
             * data-symbol pertence ao #tab-active.
             */
            if (
                    !q.ativo()
                            .equalsIgnoreCase(
                                    selecionado
                            )
            ) {
                continue;
            }

            processarQuote(q);
        }
    }

    /*
     * ============================================================
     * TROCA REAL DE GRÁFICO
     * ============================================================
     */

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

        /*
         * Não podemos finalizar uma entrada do ativo
         * anterior usando preço de outro ativo.
         */
        if (anterior != null) {

            paperTracker
                    .cancelarAtivo(
                            anterior
                    );
        }

        ativoAtual =
                novoAtivo;

        ultimoPreco =
                null;

        /*
         * Reinicia candles.
         */
        candleBuilder =
                new CandleBuilder15s();

        System.out.println();

        System.out.println(
                "========================================"
        );

        if (anterior == null) {

            System.out.println(
                    "ATIVO SELECIONADO: "
                            +
                            formatarAtivo(
                                    ativoAtual
                            )
            );

        } else {

            System.out.println(
                    "TROCA DE GRÁFICO DETECTADA"
            );

            System.out.println(
                    formatarAtivo(anterior)
                            +
                            " -> "
                            +
                            formatarAtivo(
                                    ativoAtual
                            )
            );
        }

        if (payoutAtual != null) {

            System.out.println(
                    "PAYOUT ATUAL: "
                            +
                            payoutAtual
                            +
                            "%"
            );

        } else {

            System.out.println(
                    "PAYOUT ATUAL: aguardando leitura..."
            );
        }

        System.out.println(
                "Candles reiniciados para o novo ativo."
        );

        System.out.println(
                "========================================"
        );

        System.out.println();
    }

    /*
     * ============================================================
     * PROCESSAMENTO DA COTAÇÃO
     * ============================================================
     */

    private void processarQuote(
            QuoteStream.Quote q
    ) {

        /*
         * Primeiro entrega a cotação
         * ao simulador.
         */
        paperTracker.onQuote(
                q.ativo(),
                q.preco()
        );

        if (
                ultimoPreco == null
                        ||
                        Double.compare(
                                ultimoPreco,
                                q.preco()
                        ) != 0
        ) {

            StringBuilder linha =
                    new StringBuilder();

            linha.append(
                    "PREÇO REAL ["
            );

            linha.append(
                    formatarAtivo(
                            q.ativo()
                    )
            );

            linha.append(
                    "]: "
            );

            linha.append(
                    q.preco()
            );

            if (payoutAtual != null) {

                linha.append(
                        " | PAYOUT: "
                );

                linha.append(
                        payoutAtual
                );

                linha.append("%");
            }

            System.out.println(
                    linha
            );

            ultimoPreco =
                    q.preco();
        }

        int antes =
                candleBuilder
                        .getCandlesFechados()
                        .size();

        candleBuilder
                .adicionarQuote(q);

        List<Candle> candles =
                candleBuilder
                        .getCandlesFechados();

        if (
                candles.size()
                        >
                        antes
        ) {

            System.out.println(
                    "Candles reais disponíveis ["
                            +
                            formatarAtivo(
                                    q.ativo()
                            )
                            +
                            "]: "
                            +
                            candles.size()
            );

            if (
                    candles.size()
                            >=
                            20
            ) {

                Signal sinal =
                        analyzer.analisar(
                                candles
                        );

                mostrar(
                        q,
                        sinal,
                        candles.size()
                );

                /*
                 * Entrada virtual.
                 *
                 * Nesta versão o payout já é detectado
                 * e exibido.
                 *
                 * No próximo passo vamos passar também
                 * o payout para PaperTradeTracker.abrir().
                 */
                if (
                        sinal.direcao()
                                .equals(
                                        "PARA CIMA"
                                )
                                ||
                                sinal.direcao()
                                        .equals(
                                                "PARA BAIXO"
                                        )
                ) {

                    if (payoutAtual != null) {

                        System.out.println(
                                "PAYOUT NO MOMENTO DO SINAL: "
                                        +
                                        payoutAtual
                                        +
                                        "%"
                        );
                    }

                    if (payoutAtual != null) {

                        // Publica o sinal para outros programas pela API local.
                        SignalHttpServer.publicarSinal(
                                q.ativo(),
                                sinal.direcao(),
                                sinal.confianca(),
                                payoutAtual.doubleValue()
                        );

                        // Continua abrindo a operação virtual normalmente.
                        paperTracker.abrir(
                                q.ativo(),
                                sinal.direcao(),
                                q.preco(),
                                sinal.confianca(),
                                payoutAtual.doubleValue()
                        );

                    } else {

                        System.out.println(
                                "⚠ Entrada virtual ignorada: payout não identificado."
                        );
                    }
                }
            }
        }
    }

    /*
     * ============================================================
     * TELA DA ANÁLISE
     * ============================================================
     */

    private void mostrar(
            QuoteStream.Quote q,
            Signal sinal,
            int numeroCandles
    ) {

        System.out.println();

        System.out.println(
                "========================================"
        );

        System.out.println(
                "       ANÁLISE REAL 15s"
        );

        System.out.println(
                "========================================"
        );

        System.out.println(
                "Ativo: "
                        +
                        formatarAtivo(
                                q.ativo()
                        )
        );

        if (payoutAtual != null) {

            System.out.println(
                    "Payout atual: "
                            +
                            payoutAtual
                            +
                            "%"
            );

        } else {

            System.out.println(
                    "Payout atual: não identificado"
            );
        }

        System.out.println(
                "Candles: "
                        +
                        numeroCandles
        );

        System.out.println(
                "Preço: "
                        +
                        q.preco()
        );

        System.out.println();

        String icone =
                sinal.direcao()
                        .equals(
                                "PARA CIMA"
                        )
                        ?
                        "🟢"
                        :
                        sinal.direcao()
                                .equals(
                                        "PARA BAIXO"
                                )
                                ?
                                "🔴"
                                :
                                "⚪";

        System.out.println(
                "SINAL: "
                        +
                        icone
                        +
                        " "
                        +
                        sinal.direcao()
        );

        System.out.printf(
                "CONFIANÇA HEURÍSTICA: %.1f%%%n",
                sinal.confianca()
        );

        System.out.println(
                "MOTIVO: "
                        +
                        sinal.motivo()
        );

        System.out.println(
                "CANDLE BASE: 15 segundos"
        );

        System.out.println(
                "EXPIRAÇÕES TESTADAS: "
                        +
                        "15s / 30s / 1min / 2min / 5min"
        );

        System.out.println(
                "========================================"
        );

        System.out.println();
    }

    /*
     * ============================================================
     * EXTRAÇÃO DO PAYLOAD DO CDP
     * ============================================================
     */

    private String extrairPayload(
            String json
    ) {

        String chave =
                "\"payloadData\"";

        int pos =
                json.indexOf(chave);

        if (pos < 0) {
            return null;
        }

        int colon =
                json.indexOf(
                        ':',
                        pos + chave.length()
                );

        if (colon < 0) {
            return null;
        }

        int i =
                colon + 1;

        while (
                i < json.length()
                        &&
                        Character.isWhitespace(
                                json.charAt(i)
                        )
        ) {
            i++;
        }

        if (
                i >= json.length()
                        ||
                        json.charAt(i) != '"'
        ) {
            return null;
        }

        i++;

        StringBuilder out =
                new StringBuilder();

        boolean escape =
                false;

        for (
                ;
                i < json.length();
                i++
        ) {

            char c =
                    json.charAt(i);

            if (escape) {

                switch (c) {

                    case '"' ->
                            out.append('"');

                    case '\\' ->
                            out.append('\\');

                    case '/' ->
                            out.append('/');

                    case 'n' ->
                            out.append('\n');

                    case 'r' ->
                            out.append('\r');

                    case 't' ->
                            out.append('\t');

                    case 'b' ->
                            out.append('\b');

                    case 'f' ->
                            out.append('\f');

                    default ->
                            out.append(c);
                }

                escape =
                        false;

                continue;
            }

            if (c == '\\') {

                escape =
                        true;

                continue;
            }

            if (c == '"') {
                return out.toString();
            }

            out.append(c);
        }

        return null;
    }

    /*
     * ============================================================
     * FUNÇÕES AUXILIARES JSON
     * ============================================================
     */

    private String jsonString(
            String texto
    ) {

        if (texto == null) {
            return "\"\"";
        }

        StringBuilder out =
                new StringBuilder();

        out.append('"');

        for (
                int i = 0;
                i < texto.length();
                i++
        ) {

            char c =
                    texto.charAt(i);

            switch (c) {

                case '"' ->
                        out.append("\\\"");

                case '\\' ->
                        out.append("\\\\");

                case '\n' ->
                        out.append("\\n");

                case '\r' ->
                        out.append("\\r");

                case '\t' ->
                        out.append("\\t");

                default ->
                        out.append(c);
            }
        }

        out.append('"');

        return out.toString();
    }

    private String desserializarJson(
            String texto
    ) {

        if (texto == null) {
            return null;
        }

        StringBuilder out =
                new StringBuilder();

        boolean escape =
                false;

        for (
                int i = 0;
                i < texto.length();
                i++
        ) {

            char c =
                    texto.charAt(i);

            if (escape) {

                switch (c) {

                    case '"' ->
                            out.append('"');

                    case '\\' ->
                            out.append('\\');

                    case '/' ->
                            out.append('/');

                    case 'n' ->
                            out.append('\n');

                    case 'r' ->
                            out.append('\r');

                    case 't' ->
                            out.append('\t');

                    case 'b' ->
                            out.append('\b');

                    case 'f' ->
                            out.append('\f');

                    default ->
                            out.append(c);
                }

                escape =
                        false;

                continue;
            }

            if (c == '\\') {

                escape =
                        true;

            } else {

                out.append(c);
            }
        }

        return out.toString();
    }

    /*
     * ============================================================
     * FORMATAÇÃO DO ATIVO
     * ============================================================
     */

    private String formatarAtivo(
            String ativo
    ) {

        if (ativo == null) {
            return "DESCONHECIDO";
        }

        String s =
                ativo.trim();

        boolean otc =
                s.toLowerCase(
                        Locale.ROOT
                ).endsWith("_otc");

        if (otc) {

            s =
                    s.substring(
                            0,
                            s.length() - 4
                    );
        }

        /*
         * EURUSD -> EUR/USD
         * USDMXN -> USD/MXN
         * USDBRL -> USD/BRL
         */
        if (
                s.length() == 6
                        &&
                        s.chars()
                                .allMatch(
                                        Character::isLetter
                                )
        ) {

            s =
                    s.substring(0, 3)
                            +
                            "/"
                            +
                            s.substring(3);
        }

        return otc
                ?
                s + " OTC"
                :
                s;
    }

    /*
     * ============================================================
     * RECONEXÃO
     * ============================================================
     */

    private void agendarReconexao() {

        if (
                !executando
                        ||
                        !reconectando.compareAndSet(
                                false,
                                true
                        )
        ) {
            return;
        }

        pararLeituraInterface();

        System.out.println(
                "Reconectando em 3 segundos..."
        );

        scheduler.schedule(
                () -> {

                    reconectando.set(false);

                    conectar();

                },
                3,
                TimeUnit.SECONDS
        );
    }

    /*
     * ============================================================
     * ERRO
     * ============================================================
     */

    private void erro(
            Throwable e
    ) {

        System.out.println(
                "\nErro: "
                        +
                        e.getClass().getName()
                        +
                        " - "
                        +
                        String.valueOf(
                                e.getMessage()
                        )
        );
    }
}
