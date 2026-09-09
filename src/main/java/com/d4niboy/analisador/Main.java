package com.d4niboy.analisador;

public class Main {

    public static void main(String[] args) {

        // ========================================
        // SERVIDOR LOCAL DE SINAIS
        // ========================================

        SignalHttpServer.iniciar();


        // ========================================
        // JANELA RECEPTORA DE SINAIS
        // ========================================

        SignalReceiverWindow.abrir();


        // ========================================
        // INÍCIO DO ANALISADOR
        // ========================================

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

        System.out.println(
                "Selenium aguardando sinal real do analisador."
        );

        System.out.println(
                "O botão correspondente será apenas destacado."
        );

        System.out.println();


        // ========================================
        // INICIA O FEED DE DADOS REAIS
        // ========================================

        RealTimeFeed feed =
                new RealTimeFeed();

        feed.iniciar();


        // ========================================
        // MANTÉM O PROGRAMA EXECUTANDO
        // ========================================

        try {

            Thread.currentThread().join();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }
    }
}
