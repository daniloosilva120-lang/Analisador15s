package com.d4niboy.analisador;

import java.util.ArrayList;
import java.util.List;

public class CandleBuilder15s {

    private static final long INTERVALO_MS = 15_000;

    private final List<Candle> candlesFechados =
            new ArrayList<>();

    private long inicioAtual = -1;

    private double open;
    private double high;
    private double low;
    private double close;

    public void adicionarQuote(
            QuoteStream.Quote quote
    ) {

        long timestampMs =
                (long) (
                        quote.timestamp()
                                *
                                1000
                );

        long intervalo =
                (
                        timestampMs
                                /
                                INTERVALO_MS
                )
                        *
                        INTERVALO_MS;

        if (inicioAtual == -1) {

            iniciar(
                    intervalo,
                    quote.preco()
            );

            return;
        }

        if (
                intervalo < inicioAtual
        ) {
            return;
        }

        if (
                intervalo > inicioAtual
        ) {

            fechar();

            iniciar(
                    intervalo,
                    quote.preco()
            );

            return;
        }

        atualizar(
                quote.preco()
        );
    }

    private void iniciar(
            long inicio,
            double preco
    ) {

        inicioAtual = inicio;

        open = preco;
        high = preco;
        low = preco;
        close = preco;
    }

    private void atualizar(
            double preco
    ) {

        high =
                Math.max(
                        high,
                        preco
                );

        low =
                Math.min(
                        low,
                        preco
                );

        close = preco;
    }

    private void fechar() {

        Candle candle =
                new Candle(inicioAtual, open, high, low, close);

        candlesFechados.add(
                candle
        );

        int numeroCandle =
                candlesFechados.size();

        System.out.println();

        System.out.println(
                "CANDLE 15s FECHADO #"
                        + numeroCandle
        );

        System.out.println(
                "Open:  "
                        + open
        );

        System.out.println(
                "High:  "
                        + high
        );

        System.out.println(
                "Low:   "
                        + low
        );

        System.out.println(
                "Close: "
                        + close
        );

        System.out.println();
    }

    public List<Candle> getCandlesFechados() {

        return new ArrayList<>(
                candlesFechados
        );
    }
}
