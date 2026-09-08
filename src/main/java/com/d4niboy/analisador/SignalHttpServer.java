package com.d4niboy.analisador;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.Executors;

public class SignalHttpServer {

    private static final int PORTA = 8765;

    private static HttpServer servidor;

    private static volatile String ultimoSinal = """
            {
              "status": "AGUARDANDO_SINAL"
            }
            """;

    private SignalHttpServer() {
    }

    public static synchronized void iniciar() {

        if (servidor != null) {
            return;
        }

        try {

            servidor = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", PORTA),
                    0
            );

            servidor.createContext(
                    "/sinal",
                    SignalHttpServer::responderSinal
            );

            servidor.createContext(
                    "/status",
                    exchange -> {

                        String resposta = """
                                {
                                  "status": "ONLINE",
                                  "porta": 8765
                                }
                                """;

                        enviarJson(
                                exchange,
                                resposta
                        );
                    }
            );

            servidor.setExecutor(
                    Executors.newCachedThreadPool()
            );

            servidor.start();

            System.out.println();
            System.out.println("========================================");
            System.out.println("       SERVIDOR DE SINAIS ATIVO");
            System.out.println("========================================");
            System.out.println("http://127.0.0.1:8765/sinal");
            System.out.println("http://127.0.0.1:8765/status");
            System.out.println("========================================");
            System.out.println();

        } catch (IOException e) {

            System.out.println(
                    "ERRO AO INICIAR SERVIDOR DE SINAIS: "
                            + e.getMessage()
            );
        }
    }

    public static void publicarSinal(
            String ativo,
            String direcao,
            double confianca,
            double payout
    ) {

        ultimoSinal = String.format(
                Locale.US,
                """
                {
                  "status": "SINAL",
                  "ativo": "%s",
                  "direcao": "%s",
                  "confianca": %.2f,
                  "payout": %.2f,
                  "timestamp": %d
                }
                """,
                escapar(ativo),
                escapar(direcao),
                confianca,
                payout,
                System.currentTimeMillis()
        );

        System.out.println();
        System.out.println("========================================");
        System.out.println("       SINAL PUBLICADO NA API");
        System.out.println("========================================");
        System.out.println("Ativo: " + ativo);
        System.out.println("Direção: " + direcao);

        System.out.printf(
                Locale.US,
                "Confiança: %.1f%%%n",
                confianca
        );

        System.out.printf(
                Locale.US,
                "Payout: %.0f%%%n",
                payout
        );

        System.out.println("========================================");
        System.out.println();
    }

    private static void responderSinal(
            HttpExchange exchange
    ) throws IOException {

        enviarJson(
                exchange,
                ultimoSinal
        );
    }

    private static void enviarJson(
            HttpExchange exchange,
            String json
    ) throws IOException {

        byte[] resposta =
                json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8"
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(
                200,
                resposta.length
        );

        try (OutputStream os =
                     exchange.getResponseBody()) {

            os.write(resposta);
        }
    }

    private static String escapar(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    public static synchronized void parar() {

        if (servidor != null) {

            servidor.stop(0);
            servidor = null;

            System.out.println(
                    "Servidor de sinais encerrado."
            );
        }
    }
}
