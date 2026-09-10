package com.d4niboy.analisador;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Locale;

public class SignalHttpServer {

    private static volatile String ultimoJsonSinal = "{\"status\":\"AGUARDANDO_SINAL\"}";

    public static void iniciar() {
        new Thread(() -> {
            try {
                HttpServer server = HttpServer.create(new InetSocketAddress(8765), 0);
                server.createContext("/sinal", new SinalHandler());
                server.setExecutor(null);
                server.start();
                System.out.println("Servidor HTTP rodando na porta 8765...");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, "HttpServerThread").start();
    }

    public static void publicarSinal(String ativo, String direcao, double confianca, double payout) {
        long timestamp = System.currentTimeMillis();
        ultimoJsonSinal = String.format(
                Locale.US,
                "{\"timestamp\":%d,\"ativo\":\"%s\",\"direcao\":\"%s\",\"confianca\":%.2f,\"payout\":%.2f}",
                timestamp, ativo, direcao, confianca, payout
        );
    }

    static class SinalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            byte[] respostaBytes = ultimoJsonSinal.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, respostaBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respostaBytes);
            }
        }
    }
}
