package com.d4niboy.analisador;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class SignalHttpServer {

    private static final String SERVER_HOST =
            System.getProperty(
                    "analisador.signal.host",
                    "127.0.0.1"
            );

    private static final int SERVER_PORT =
            Integer.getInteger(
                    "analisador.signal.port",
                    8765
            );

    private static volatile String ultimoJsonSinal =
            "{\"status\":\"AGUARDANDO_SINAL\"}";

    private static final AtomicLong ultimoTimestamp =
            new AtomicLong(0);

    /*
     * Mantemos a referência do servidor para conseguir
     * encerrá-lo corretamente quando o Main terminar.
     */
    private static HttpServer server;

    private static boolean iniciado = false;

    // ==========================================================
    // INICIAR SERVIDOR
    // ==========================================================

    public static synchronized void iniciar() {

        /*
         * Evita iniciar duas vezes dentro do mesmo programa.
         */
        if (iniciado && server != null) {

            System.out.println(
                    "SIGNAL HTTP: já está ativo em http://"
                            + SERVER_HOST
                            + ":"
                            + SERVER_PORT
                            + "/sinal"
            );

            return;
        }

        try {

            server =
                    HttpServer.create(
                            new InetSocketAddress(
                                    SERVER_HOST,
                                    SERVER_PORT
                            ),
                            0
                    );

            server.createContext(
                    "/sinal",
                    new SinalHandler()
            );

            server.setExecutor(null);

            server.start();

            iniciado = true;

            System.out.println(
                    "SIGNAL HTTP: http://"
                            + SERVER_HOST
                            + ":"
                            + SERVER_PORT
                            + "/sinal"
            );

        } catch (BindException e) {

            server = null;
            iniciado = false;

            /*
             * Não joga aquele texto vermelho enorme.
             * Mostra apenas uma mensagem simples.
             */
            System.out.println(
                    "SIGNAL HTTP: porta "
                            + SERVER_PORT
                            + " já está sendo usada por outro processo."
            );

        } catch (IOException e) {

            server = null;
            iniciado = false;

            System.out.println(
                    "SIGNAL HTTP: não foi possível iniciar."
            );

            if (e.getMessage() != null) {

                System.out.println(
                        "Detalhes: "
                                + e.getMessage()
                );
            }
        }
    }

    // ==========================================================
    // PARAR SERVIDOR
    // ==========================================================

    public static synchronized void parar() {

        if (server == null) {
            return;
        }

        try {

            server.stop(0);

            System.out.println(
                    "SIGNAL HTTP: encerrado."
            );

        } catch (Exception ignored) {

        } finally {

            server = null;
            iniciado = false;
        }
    }

    // ==========================================================
    // PUBLICAR SINAL
    // ==========================================================

    public static synchronized void publicarSinal(
            String ativo,
            String direcao,
            double payout
    ) {

        long agora =
                System.currentTimeMillis();

        long anterior =
                ultimoTimestamp.get();

        long timestamp =
                Math.max(
                        agora,
                        anterior + 1
                );

        ultimoTimestamp.set(
                timestamp
        );

        ultimoJsonSinal =
                String.format(
                        Locale.US,
                        "{\"timestamp\":%d,\"ativo\":\"%s\",\"direcao\":\"%s\",\"payout\":%.2f}",
                        timestamp,
                        escaparJson(ativo),
                        escaparJson(direcao),
                        payout
                );
    }

    // ==========================================================
    // ESCAPAR JSON
    // ==========================================================

    private static String escaparJson(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return texto
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
                );
    }

    // ==========================================================
    // HANDLER
    // ==========================================================

    private static class SinalHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange
        ) throws IOException {

            exchange
                    .getResponseHeaders()
                    .set(
                            "Content-Type",
                            "application/json; charset=UTF-8"
                    );

            exchange
                    .getResponseHeaders()
                    .set(
                            "Cache-Control",
                            "no-store, no-cache, must-revalidate, max-age=0"
                    );

            exchange
                    .getResponseHeaders()
                    .set(
                            "Pragma",
                            "no-cache"
                    );

            exchange
                    .getResponseHeaders()
                    .set(
                            "Expires",
                            "0"
                    );

            exchange
                    .getResponseHeaders()
                    .set(
                            "Access-Control-Allow-Origin",
                            "*"
                    );

            byte[] respostaBytes =
                    ultimoJsonSinal.getBytes(
                            StandardCharsets.UTF_8
                    );

            exchange.sendResponseHeaders(
                    200,
                    respostaBytes.length
            );

            try (
                    OutputStream os =
                            exchange.getResponseBody()
            ) {

                os.write(
                        respostaBytes
                );
            }
        }
    }
}
