package com.d4niboy.analisador;

public class Main {

    public static void main(String[] args) {

        // Servidor local de sinais
        SignalHttpServer.iniciar();

        // Janela receptora de sinais
        SignalReceiverWindow.abrir();

        // Inicia o feed de dados reais e o robô
        RealTimeFeed feed = new RealTimeFeed();
        feed.iniciar();

        // Mantém o programa executando
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
