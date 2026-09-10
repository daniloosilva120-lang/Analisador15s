package com.d4niboy.analisador;

public class PaperTrade {

    private final long id;
    private final String ativo;
    private final String direcao;
    private final double precoEntrada;
    private final long horarioEntrada;
    private final long expiracaoMs;
    private final double confianca;
    private final double payout;

    private boolean invalida;

    public PaperTrade(
            long id,
            String ativo,
            String direcao,
            double precoEntrada,
            long horarioEntrada,
            long expiracaoMs,
            double confianca,
            double payout
    ) {
        this.id = id;
        this.ativo = ativo;
        this.direcao = direcao;
        this.precoEntrada = precoEntrada;
        this.horarioEntrada = horarioEntrada;
        this.expiracaoMs = expiracaoMs;
        this.confianca = confianca;
        this.payout = payout;
    }

    public long getId() {
        return id;
    }

    public String getAtivo() {
        return ativo;
    }

    public String getDirecao() {
        return direcao;
    }

    public double getPrecoEntrada() {
        return precoEntrada;
    }

    public long getHorarioExpiracao() {
        return horarioEntrada + expiracaoMs;
    }

    public long getExpiracaoMs() {
        return expiracaoMs;
    }

    public double getConfianca() {
        return confianca;
    }

    public double getPayout() {
        return payout;
    }

    public boolean isInvalida() {
        return invalida;
    }

    public void setInvalida(boolean invalida) {
        this.invalida = invalida;
    }
}
