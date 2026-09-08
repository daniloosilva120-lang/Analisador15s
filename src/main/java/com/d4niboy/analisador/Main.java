package com.d4niboy.analisador;

public class Main {

    public static void main(String[] args) {

        SignalHttpServer.iniciar();

        // Abre automaticamente o receptor
        SignalReceiverWindow.abrir();

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
                "Modo leitura. Nenhuma operação será executada."
        );

        System.out.println();

        RealTimeFeed feed =
                new RealTimeFeed();

        feed.iniciar();

        try {

            Thread.currentThread().join();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }
    }
}
