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
        System.out.println("========================================");
        System.out.println("   ANALISADOR 15s - SISTEMA COMPLETO");
        System.out.println("========================================");
        System.out.println();
        System.out.println("Modo AUTO-CLICK ATIVADO. Operações REAIS ativadas!");
        System.out.println("O robô será conectado ao Chrome em segundo plano.");
        System.out.println();

        // ========================================
        // INICIA O FEED DE DADOS REAIS (E O ROBÔ JUNTO)
        // ========================================
        RealTimeFeed feed = new RealTimeFeed();
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
