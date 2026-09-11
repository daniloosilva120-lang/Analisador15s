package com.d4niboy.analisador;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class SignalHttpServer {

    /*
     * Configuracao portatil do servidor local.
     *
     * Por padrao continua usando:
     * 127.0.0.1:8765
     *
     * Pode ser alterado sem mexer no codigo:
     *
     * -Danalisador.signal.host=127.0.0.1
     * -Danalisador.signal.port=8765
     */
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

    public static void iniciar() {

        new Thread(
                () -> {

                    try {

                        HttpServer server =
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

                        System.out.println(
                                "SIGNAL HTTP: http://"
                                        + SERVER_HOST
                                        + ":"
                                        + SERVER_PORT
                                        + "/sinal"
                        );

                    } catch (IOException e) {

                        System.err.println(
                                "ERRO: Não foi possível iniciar o servidor HTTP."
                        );

                        if (e.getMessage() != null) {
                            System.err.println(
                                    "Detalhes: "
                                            + e.getMessage()
                            );
                        }
                    }

                },
                "HttpServerThread"
        ).start();
    }

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
