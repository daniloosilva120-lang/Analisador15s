package com.d4niboy.analisador;

public record Candle(
        long timestamp,
        double open,
        double high,
        double low,
        double close
) {

    public boolean isAlta() {
        return close > open;
    }

    public boolean isBaixa() {
        return close < open;
    }

    public double corpo() {
        return Math.abs(close - open);
    }

    public double amplitude() {
        return Math.max(0.0000001, high - low);
    }

    public double pavioSuperior() {
        return high - Math.max(open, close);
    }

    public double pavioInferior() {
        return Math.min(open, close) - low;
    }
}
