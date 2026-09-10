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

    // --- INSTÂNCIA DO ROBÔ ADICIONADA AQUI ---
    private final RoboBotao roboBotao = new RoboBotao();
    // -----------------------------------------

    private final QuoteStream quoteStream = new QuoteStream();
    private CandleBuilder15s candleBuilder = new CandleBuilder15s();
    private final Analyzer15s analyzer = new Analyzer15s();

    private final PaperTradeTracker paperTracker = new PaperTradeTracker();
    private final HttpClient client = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean reconectando = new AtomicBoolean(false);
    private final AtomicInteger proximoId = new AtomicInteger(1000);
    private final Set<Integer> requisicoesDom = ConcurrentHashMap.newKeySet();

    private volatile WebSocket socket;
    private volatile boolean executando = true;
    private volatile String ativoAtual = null;
    private volatile Integer payoutAtual = null;
    private volatile Double ultimoPreco = null;
    private ScheduledFuture<?> tarefaInterface;

    private static final Pattern DEBUG = Pattern.compile("\"webSocketDebuggerUrl\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OPCODE = Pattern.compile("\"opcode\"\\s*:\\s*(\\d+)");
    private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
    private static final Pattern VALUE = Pattern.compile("\"value\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern PAYOUT = Pattern.compile("(\\d{1,3})\\s*%");

    public void iniciar() {
        System.out.println("\n========================================");
        System.out.println("   ANALISADOR 15s - DADOS REAIS");
        System.out.println("========================================\n");
        System.out.println("REAL TIME FEED\n");
        System.out.println("Detecção do gráfico selecionado ativada.");
        System.out.println("O programa seguirá somente o ativo que está visível.\n");
        System.out.println("Leitura automática de payout ativada.\n");
        System.out.println("RoboBotao em modo assistido: recebe sinais e apenas destaca a direção no Chrome.\n");
        conectar();
    }

    private void conectar() {
        if (!executando) return;
        try {
            System.out.println("Procurando Chrome...");
            String url = localizarChrome();
            if (url == null) {
                System.out.println("Chrome de depuração não encontrado.");
                agendarReconexao();
                return;
            }
            socket = client.newWebSocketBuilder().buildAsync(URI.create(url), new Listener()).join();
            System.out.println("Conectado ao Chrome.");

            socket.sendText("{\"id\":1,\"method\":\"Network.enable\"}", true);
            socket.sendText("{\"id\":2,\"method\":\"Runtime.enable\"}", true);

            reconectando.set(false);
            iniciarLeituraInterface();
            System.out.println("Captura de preços ativada.");
            System.out.println("Aguardando identificação do gráfico selecionado...\n");
        } catch (Throwable e) {
            erro(e);
            agendarReconexao();
        }
    }

    private void agendarReconexao() {
        if (reconectando.compareAndSet(false, true)) {
            scheduler.schedule(this::conectar, 5, TimeUnit.SECONDS);
        }
    }

    private void erro(Throwable e) {
        System.err.println("Erro na conexão: " + e.getMessage());
    }

    private String localizarChrome() throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:9222/json/list"))
                .GET()
                .build();

        HttpResponse<String> res = client.send(
                req,
                HttpResponse.BodyHandlers.ofString()
        );

        if (res.statusCode() != 200) {
            return null;
        }

        String json = res.body();

        Pattern alvo = Pattern.compile(
                "\\{(?:(?!\\}\\s*,\\s*\\{).)*?\\\"type\\\"\\s*:\\s*\\\"page\\\"(?:(?!\\}\\s*,\\s*\\{).)*?\\}",
                Pattern.DOTALL
        );

        Matcher alvoMatcher = alvo.matcher(json);

        while (alvoMatcher.find()) {
            String objeto = alvoMatcher.group();
            String minusculo = objeto.toLowerCase(Locale.ROOT);

            if (minusculo.contains("qxbroker") || minusculo.contains("quotex")) {
                Matcher debug = DEBUG.matcher(objeto);

                if (debug.find()) {
                    System.out.println("Aba da plataforma localizada no Chrome.");
                    return debug.group(1);
                }
            }
        }

        System.out.println(
                "Chrome encontrado, mas nenhuma aba da Quotex/QXBroker foi localizada."
        );

        return null;
    }

    private void iniciarLeituraInterface() {
        pararLeituraInterface();
        tarefaInterface = scheduler.scheduleAtFixedRate(this::consultarInterface, 100, 500, TimeUnit.MILLISECONDS);
    }

    private void pararLeituraInterface() {
        if (tarefaInterface != null) {
            tarefaInterface.cancel(false);
            tarefaInterface = null;
        }
    }

    private void consultarInterface() {
        try {
            WebSocket atual = socket;
            if (atual == null || !executando) {
                return;
            }

            int id = proximoId.incrementAndGet();
            requisicoesDom.add(id);

            String expressao = """
                (() => {
                    let tab = document.querySelector('#tab-active');

                    if (!tab) {
                        tab = document.querySelector(
                            '[data-symbol][aria-selected="true"], ' +
                            '[data-symbol].active, ' +
                            '[data-symbol][class*="active"]'
                        );
                    }

                    if (!tab) {
                        const candidatos = Array.from(
                            document.querySelectorAll('[data-symbol]')
                        );

                        tab = candidatos.find(el => {
                            const r = el.getBoundingClientRect();
                            const style = getComputedStyle(el);

                            return r.width > 0 &&
                                   r.height > 0 &&
                                   style.display !== 'none' &&
                                   style.visibility !== 'hidden' &&
                                   (
                                       el.id === 'tab-active' ||
                                       el.getAttribute('aria-selected') === 'true' ||
                                       /active|selected/i.test(el.className || '')
                                   );
                        }) || null;
                    }

                    if (!tab) {
                        return 'SEM_ATIVO|';
                    }

                    const symbol = (
                        tab.getAttribute('data-symbol') || ''
                    ).trim();

                    let payout = '';

                    const payoutEl =
                        tab.querySelector('.ElyTP') ||
                        tab.querySelector('[class*="payout"]');

                    if (payoutEl) {
                        payout = (payoutEl.textContent || '').trim();
                    }

                    if (!payout) {
                        const elementos = Array.from(
                            tab.querySelectorAll('*')
                        );

                        for (const el of elementos) {
                            const txt = (el.textContent || '').trim();

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
                    "{\"id\":" + id +
                            ",\"method\":\"Runtime.evaluate\"," +
                            "\"params\":{\"expression\":" +
                            jsonString(expressao) +
                            ",\"returnByValue\":true}}";

            atual.sendText(comando, true);

        } catch (Throwable e) {
            System.err.println(
                    "Falha temporária ao consultar a interface: " +
                            e.getMessage()
            );
        }
    }

    private String jsonString(String str) {
        return "\"" + str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t")
                + "\"";
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                buffer.append(data);
                if (last) {
                    String mensagem = buffer.toString();
                    buffer.setLength(0);
                    processar(mensagem);
                }
            } catch (Throwable e) {
                erro(e);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            pararLeituraInterface();
            socket = null;
            agendarReconexao();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            pararLeituraInterface();
            socket = null;
            agendarReconexao();
        }
    }

    private void processar(String mensagem) {
        Matcher idMatcher = ID.matcher(mensagem);
        if (idMatcher.find()) {
            int id;
            try { id = Integer.parseInt(idMatcher.group(1)); }
            catch (NumberFormatException e) { id = -1; }
            if (id >= 0 && requisicoesDom.remove(id)) {
                processarRespostaInterface(mensagem);
                return;
            }
        }

        if (!mensagem.contains("Network.webSocketFrameReceived")) return;
        Matcher op = OPCODE.matcher(mensagem);
        if (!op.find()) return;

        int opcode = Integer.parseInt(op.group(1));
        if (opcode != 2) return;

        String payload = extrairPayload(mensagem);
        if (payload == null) return;

        try {
            byte[] dados = Base64.getDecoder().decode(payload);
            processarBinario(dados);
        } catch (IllegalArgumentException e) {
            processarBinario(payload.getBytes(StandardCharsets.ISO_8859_1));
        }
    }

    private String extrairPayload(String mensagem) {
        Pattern PAYLOAD_PATTERN = Pattern.compile("\"payloadData\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = PAYLOAD_PATTERN.matcher(mensagem);
        return m.find() ? m.group(1) : null;
    }

    private String desserializarJson(String str) {
        return str.replace("\\\"", "\"").replace("\\n", "\n");
    }

    private void processarRespostaInterface(String mensagem) {
        Matcher value = VALUE.matcher(mensagem);

        if (!value.find()) {
            return;
        }

        String texto = desserializarJson(value.group(1));

        System.out.println("[DEBUG DOM] " + texto);

        if (texto == null || texto.isBlank()) {
            return;
        }

        if (texto.startsWith("SEM_ATIVO|")) {
            return;
        }

        String[] partes = texto.split("\\|", -1);

        if (partes.length == 0) {
            return;
        }

        String novoAtivo = partes[0].trim();

        if (novoAtivo.isBlank()) {
            return;
        }

        Integer novoPayout =
                partes.length >= 2
                        ? interpretarPayout(partes[1])
                        : null;

        boolean mudouAtivo =
                ativoAtual == null ||
                        !ativoAtual.equalsIgnoreCase(novoAtivo);

        if (mudouAtivo) {
            payoutAtual = novoPayout;
            trocarAtivo(novoAtivo);
            return;
        }

        if (novoPayout != null &&
                !novoPayout.equals(payoutAtual)) {

            Integer anterior = payoutAtual;
            payoutAtual = novoPayout;

            System.out.println(
                    "\nPAYOUT ALTERADO [" +
                            formatarAtivo(ativoAtual) +
                            "]: " +
                            (anterior == null ? "?" : anterior + "%") +
                            " -> " +
                            novoPayout +
                            "%\n"
            );
        }
    }

    private Integer interpretarPayout(String texto) {
        if (texto == null) return null;
        Matcher m = PAYOUT.matcher(texto);
        if (!m.find()) return null;
        try {
            int valor = Integer.parseInt(m.group(1));
            return (valor >= 0 && valor <= 100) ? valor : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void processarBinario(byte[] dados) {
        List<QuoteStream.Quote> quotes = quoteStream.parseBinaryMessage(dados);
        if (quotes.isEmpty()) return;

        String selecionado = ativoAtual;
        if (selecionado == null || selecionado.isBlank()) return;

        for (QuoteStream.Quote q : quotes) {
            if (q.ativo() == null || q.ativo().isBlank()) continue;
            if (!q.ativo().equalsIgnoreCase(selecionado)) continue;
            processarQuote(q);
        }
    }

    private String formatarAtivo(String ativo) {
        return ativo != null ? ativo.toUpperCase().replace("_OTC", " (OTC)") : "";
    }

    private synchronized void trocarAtivo(String novoAtivo) {
        if (novoAtivo == null || novoAtivo.isBlank()) return;
        if (ativoAtual != null && ativoAtual.equalsIgnoreCase(novoAtivo)) return;

        String anterior = ativoAtual;
        if (anterior != null) paperTracker.cancelarAtivo(anterior);

        ativoAtual = novoAtivo;
        ultimoPreco = null;
        candleBuilder = new CandleBuilder15s();

        System.out.println("\n========================================");
        if (anterior == null) {
            System.out.println("ATIVO SELECIONADO: " + formatarAtivo(ativoAtual));
        } else {
            System.out.println("TROCA DE GRÁFICO DETECTADA");
            System.out.println(formatarAtivo(anterior) + " -> " + formatarAtivo(ativoAtual));
        }
        System.out.println("PAYOUT ATUAL: " + (payoutAtual != null ? payoutAtual + "%" : "aguardando leitura..."));
        System.out.println("Candles reiniciados para o novo ativo.");
        System.out.println("========================================\n");
    }

    private void processarQuote(QuoteStream.Quote q) {
        paperTracker.onQuote(q.ativo(), q.preco());

        if (ultimoPreco == null || Double.compare(ultimoPreco, q.preco()) != 0) {
            StringBuilder linha = new StringBuilder();
            linha.append("PREÇO REAL [").append(formatarAtivo(q.ativo())).append("]: ").append(q.preco());
            if (payoutAtual != null) {
                linha.append(" | PAYOUT: ").append(payoutAtual).append("%");
            }
            System.out.println(linha);
            ultimoPreco = q.preco();
        }

        int antes = candleBuilder.getCandlesFechados().size();
        candleBuilder.adicionarQuote(q);
        List<Candle> candles = candleBuilder.getCandlesFechados();

        if (candles.size() > antes) {
            System.out.println("Candles reais disponíveis [" + formatarAtivo(q.ativo()) + "]: " + candles.size());

            if (candles.size() >= 20) {
                Signal sinal = analyzer.analisar(candles);
                mostrar(q, sinal, candles.size());

                if (sinal.direcao().equals("PARA CIMA") || sinal.direcao().equals("PARA BAIXO")) {
                    if (payoutAtual != null) {
                        System.out.println("PAYOUT NO MOMENTO DO SINAL: " + payoutAtual + "%");

                        SignalHttpServer.publicarSinal(q.ativo(), sinal.direcao(), sinal.confianca(), payoutAtual.doubleValue());
                        paperTracker.abrir(q.ativo(), sinal.direcao(), q.preco(), sinal.confianca(), payoutAtual.doubleValue());

                        // --- ENVIO DO SINAL PARA O ROBÔ EM MODO ASSISTIDO ---
                        if (SignalReceiverWindow.isAutoClickAtivo()) {
                            System.out.println(
                                    "🤖 RoboBotao ativado. Enviando sinal: " +
                                            sinal.direcao()
                            );

                            roboBotao.receberSinal(
                                    sinal.direcao()
                            );
                        } else {
                            System.out.println(
                                    "🤖 RoboBotao pausado. Ação manual requerida."
                            );
                        }
                        // ---------------------------------------------------
                    } else {
                        System.out.println("⚠ Entrada ignorada: payout não identificado.");
                    }
                }
            }
        }
    }

    private void mostrar(QuoteStream.Quote q, Signal sinal, int numeroCandles) {
        System.out.println("\n========================================");
        System.out.println("       ANÁLISE REAL 15s");
        System.out.println("========================================");
        System.out.println("Ativo: " + formatarAtivo(q.ativo()));
        System.out.println("Payout atual: " + (payoutAtual != null ? payoutAtual + "%" : "não identificado"));
        System.out.println("Candles: " + numeroCandles);
        System.out.println("Preço: " + q.preco());
        System.out.println();

        String icone = "⚪";
        if (sinal.direcao().equals("PARA CIMA")) icone = "🟢";
        else if (sinal.direcao().equals("PARA BAIXO")) icone = "🔴";

        System.out.println("SINAL: " + icone + " " + sinal.direcao());
        System.out.println("========================================\n");
    }
}
