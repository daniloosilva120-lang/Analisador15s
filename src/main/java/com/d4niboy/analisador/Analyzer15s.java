package com.d4niboy.analisador;

import java.util.List;
import java.util.Locale;

public class Analyzer15s {

    private static final int MIN_CANDLES = 20;

    /*
     * Quanto maior, mais seletivo.
     *
     * Mantemos relativamente alto para não transformar
     * qualquer pequena oscilação em entrada.
     */
    private static final double SCORE_MINIMO = 4.00;

    /*
     * Volatilidade mínima absoluta.
     *
     * 0.000004 = 0.0004%
     *
     * Nos testes reais do USD/MXN OTC vimos algo
     * próximo de 0.0011% - 0.0012%, portanto esse
     * mercado não deve ser classificado como parado.
     */
    private static final double VOL_MINIMA = 0.000004;

    public Signal analisar(List<Candle> c) {

        if (c == null || c.size() < MIN_CANDLES) {

            return new Signal(
                    "NÃO OPERAR",
                    0,
                    "São necessárias pelo menos 20 velas."
            );
        }

        int n =
                c.size();

        Candle atual =
                c.get(n - 1);

        double preco =
                Math.max(
                        Math.abs(atual.close()),
                        0.0000001
                );

        /*
         * ============================================================
         * INDICADORES
         * ============================================================
         */

        double rsi14 =
                rsi(c, 14);

        double sma5 =
                sma(c, 5);

        double sma10 =
                sma(c, 10);

        double sma20 =
                sma(c, 20);

        double vol5 =
                volatilidadeNormalizada(c, 5);

        double vol10 =
                volatilidadeNormalizada(c, 10);

        double momentum3 =
                retorno(c, 3);

        double momentum5 =
                retorno(c, 5);

        double slope5 =
                inclinacaoSma(c, 5, 3);

        double slope10 =
                inclinacaoSma(c, 10, 3);

        double distancia5_20 =
                Math.abs(sma5 - sma20)
                        /
                        preco;

        /*
         * ============================================================
         * ESCALA ADAPTATIVA
         * ============================================================
         *
         * O problema anterior era usar números fixos que funcionariam
         * em mercados mais voláteis, mas eram grandes demais para o
         * USD/MXN OTC em candles de 15 segundos.
         *
         * Agora utilizamos a volatilidade recente como referência.
         */

        double escala =
                Math.max(
                        vol10,
                        VOL_MINIMA
                );

        /*
         * ============================================================
         * 1. MERCADO REALMENTE PARADO
         * ============================================================
         */

        if (vol10 < VOL_MINIMA) {

            return new Signal(
                    "NÃO OPERAR",
                    20,
                    "Mercado praticamente parado"
                            +
                            " | Vol=" + pct(vol10)
                            +
                            " | RSI=" + f(rsi14)
            );
        }

        /*
         * ============================================================
         * 2. LATERALIZAÇÃO
         * ============================================================
         *
         * Os limites agora dependem da volatilidade.
         */

        boolean mediasJuntas =
                distancia5_20
                        <
                        escala * 0.70;

        boolean momentumFraco =
                Math.abs(momentum5)
                        <
                        escala * 1.10;

        boolean slopesFracos =
                Math.abs(slope5)
                        <
                        escala * 0.18
                        &&
                        Math.abs(slope10)
                                <
                                escala * 0.12;

        if (
                mediasJuntas
                        &&
                        momentumFraco
                        &&
                        slopesFracos
        ) {

            return new Signal(
                    "NÃO OPERAR",
                    30,
                    "Mercado lateral"
                            +
                            " | Vol=" + pct(vol10)
                            +
                            " | Mom=" + pct(momentum5)
                            +
                            " | RSI=" + f(rsi14)
            );
        }

        double score =
                0.0;

        StringBuilder motivo =
                new StringBuilder();

        /*
         * ============================================================
         * 3. ALINHAMENTO DAS MÉDIAS
         * ============================================================
         */

        boolean mediasAlta =
                sma5 > sma10
                        &&
                        sma10 > sma20;

        boolean mediasBaixa =
                sma5 < sma10
                        &&
                        sma10 < sma20;

        if (mediasAlta) {

            score += 1.70;

            motivo.append(
                    "médias alinhadas para alta; "
            );

        } else if (mediasBaixa) {

            score -= 1.70;

            motivo.append(
                    "médias alinhadas para baixa; "
            );
        }

        /*
         * ============================================================
         * 4. INCLINAÇÃO DAS MÉDIAS
         * ============================================================
         *
         * Antes:
         *
         * slope5 > 0.00003
         *
         * Isso era grande demais para os movimentos
         * reais registrados.
         */

        double limiteSlope5 =
                escala * 0.18;

        double limiteSlope10 =
                escala * 0.08;

        if (
                slope5 > limiteSlope5
                        &&
                        slope10 > limiteSlope10
        ) {

            score += 1.10;

            motivo.append(
                    "inclinação positiva; "
            );

        } else if (
                slope5 < -limiteSlope5
                        &&
                        slope10 < -limiteSlope10
        ) {

            score -= 1.10;

            motivo.append(
                    "inclinação negativa; "
            );
        }

        /*
         * ============================================================
         * 5. MOMENTUM
         * ============================================================
         */

        if (
                momentum3 > 0
                        &&
                        momentum5 > 0
        ) {

            score += 0.90;

            motivo.append(
                    "momentum comprador; "
            );

        } else if (
                momentum3 < 0
                        &&
                        momentum5 < 0
        ) {

            score -= 0.90;

            motivo.append(
                    "momentum vendedor; "
            );
        }

        /*
         * Momentum forte relativo à volatilidade.
         */

        double momentumForte =
                escala * 1.50;

        if (
                momentum5
                        >
                        momentumForte
        ) {

            score += 0.45;

            motivo.append(
                    "momentum comprador forte; "
            );

        } else if (
                momentum5
                        <
                        -momentumForte
        ) {

            score -= 0.45;

            motivo.append(
                    "momentum vendedor forte; "
            );
        }

        /*
         * ============================================================
         * 6. RSI
         * ============================================================
         */

        if (
                rsi14 >= 53
                        &&
                        rsi14 <= 68
        ) {

            score += 0.75;

            motivo.append(
                    "RSI confirma compra; "
            );

        } else if (
                rsi14 <= 47
                        &&
                        rsi14 >= 32
        ) {

            score -= 0.75;

            motivo.append(
                    "RSI confirma venda; "
            );
        }

        /*
         * RSI muito esticado.
         */

        if (rsi14 >= 78) {

            score -= 0.70;

            motivo.append(
                    "RSI sobrecomprado; "
            );

        } else if (rsi14 <= 22) {

            score += 0.70;

            motivo.append(
                    "RSI sobrevendido; "
            );
        }

        /*
         * ============================================================
         * 7. PRESSÃO DOS ÚLTIMOS 6 CANDLES
         * ============================================================
         */

        int altas =
                0;

        int baixas =
                0;

        double forcaAlta =
                0;

        double forcaBaixa =
                0;

        int inicio =
                Math.max(
                        0,
                        n - 6
                );

        for (
                int i = inicio;
                i < n;
                i++
        ) {

            Candle x =
                    c.get(i);

            double amplitude =
                    Math.max(
                            x.amplitude(),
                            0.0000001
                    );

            double corpo =
                    x.corpo()
                            /
                            amplitude;

            if (x.isAlta()) {

                altas++;

                forcaAlta +=
                        corpo;

            } else if (x.isBaixa()) {

                baixas++;

                forcaBaixa +=
                        corpo;
            }
        }

        if (
                altas >= 4
                        &&
                        forcaAlta > forcaBaixa
        ) {

            score += 0.80;

            motivo.append(
                    "pressão compradora; "
            );
        }

        if (
                baixas >= 4
                        &&
                        forcaBaixa > forcaAlta
        ) {

            score -= 0.80;

            motivo.append(
                    "pressão vendedora; "
            );
        }

        /*
         * ============================================================
         * 8. CANDLE ATUAL
         * ============================================================
         */

        double amplitudeAtual =
                Math.max(
                        atual.amplitude(),
                        0.0000001
                );

        double corpoAtual =
                atual.corpo()
                        /
                        amplitudeAtual;

        double posicaoFechamento =
                (atual.close() - atual.low())
                        /
                        amplitudeAtual;

        if (
                atual.isAlta()
                        &&
                        corpoAtual >= 0.55
                        &&
                        posicaoFechamento >= 0.72
        ) {

            score += 0.70;

            motivo.append(
                    "candle comprador forte; "
            );
        }

        if (
                atual.isBaixa()
                        &&
                        corpoAtual >= 0.55
                        &&
                        posicaoFechamento <= 0.28
        ) {

            score -= 0.70;

            motivo.append(
                    "candle vendedor forte; "
            );
        }

        /*
         * ============================================================
         * 9. PAVIOS
         * ============================================================
         */

        double pavioSuperior =
                atual.pavioSuperior()
                        /
                        amplitudeAtual;

        double pavioInferior =
                atual.pavioInferior()
                        /
                        amplitudeAtual;

        if (
                pavioInferior >= 0.55
                        &&
                        pavioInferior
                                >
                                pavioSuperior * 1.5
        ) {

            score += 0.45;

            motivo.append(
                    "rejeição inferior; "
            );
        }

        if (
                pavioSuperior >= 0.55
                        &&
                        pavioSuperior
                                >
                                pavioInferior * 1.5
        ) {

            score -= 0.45;

            motivo.append(
                    "rejeição superior; "
            );
        }

        /*
         * ============================================================
         * 10. SUPORTE / RESISTÊNCIA
         * ============================================================
         */

        double max10 =
                maiorMaxima(c, 10);

        double min10 =
                menorMinima(c, 10);

        double distanciaResistencia =
                Math.abs(
                        max10 - atual.close()
                )
                        /
                        preco;

        double distanciaSuporte =
                Math.abs(
                        atual.close() - min10
                )
                        /
                        preco;

        double limiteSR =
                escala * 0.75;

        if (
                score > 0
                        &&
                        distanciaResistencia
                                <
                                limiteSR
                        &&
                        atual.close() < max10
        ) {

            score -= 0.55;

            motivo.append(
                    "resistência próxima; "
            );
        }

        if (
                score < 0
                        &&
                        distanciaSuporte
                                <
                                limiteSR
                        &&
                        atual.close() > min10
        ) {

            score += 0.55;

            motivo.append(
                    "suporte próximo; "
            );
        }

        /*
         * ============================================================
         * 11. EXPANSÃO DE VOLATILIDADE
         * ============================================================
         */

        if (
                vol5 > vol10 * 1.15
                        &&
                        Math.abs(momentum3)
                                >
                                escala * 0.80
        ) {

            if (momentum3 > 0) {

                score += 0.35;

            } else {

                score -= 0.35;
            }

            motivo.append(
                    "expansão de volatilidade; "
            );
        }

        /*
         * ============================================================
         * 12. TENDÊNCIA COMPLETA
         * ============================================================
         */

        boolean tendenciaAlta =
                mediasAlta
                        &&
                        slope5 > 0
                        &&
                        slope10 >= 0;

        boolean tendenciaBaixa =
                mediasBaixa
                        &&
                        slope5 < 0
                        &&
                        slope10 <= 0;

        /*
         * Confirmação adicional se:
         *
         * médias + inclinação + momentum
         * apontam todos para a mesma direção.
         */

        if (
                tendenciaAlta
                        &&
                        momentum3 > 0
                        &&
                        momentum5 > 0
        ) {

            score += 0.55;

            motivo.append(
                    "tendência compradora confirmada; "
            );
        }

        if (
                tendenciaBaixa
                        &&
                        momentum3 < 0
                        &&
                        momentum5 < 0
        ) {

            score -= 0.55;

            motivo.append(
                    "tendência vendedora confirmada; "
            );
        }

        /*
         * ============================================================
         * 13. CONFLITOS
         * ============================================================
         */

        if (
                tendenciaAlta
                        &&
                        momentum5 < 0
        ) {

            score -= 0.80;

            motivo.append(
                    "momentum contra tendência; "
            );
        }

        if (
                tendenciaBaixa
                        &&
                        momentum5 > 0
        ) {

            score += 0.80;

            motivo.append(
                    "momentum contra tendência; "
            );
        }

        /*
         * ============================================================
         * DECISÃO
         * ============================================================
         */

        double magnitude =
                Math.abs(score);

        /*
         * Continua sendo apenas uma pontuação heurística.
         *
         * Não representa probabilidade garantida de acerto.
         */
        double confianca =
                45
                        +
                        magnitude * 6.5;

        confianca =
                Math.max(
                        0,
                        Math.min(
                                90,
                                confianca
                        )
                );

        String detalhes =
                "Score=" + f(score)
                        +
                        " | RSI=" + f(rsi14)
                        +
                        " | Mom3=" + pct(momentum3)
                        +
                        " | Mom5=" + pct(momentum5)
                        +
                        " | Vol5=" + pct(vol5)
                        +
                        " | Vol10=" + pct(vol10)
                        +
                        " | Slope5=" + pct(slope5)
                        +
                        " | Slope10=" + pct(slope10);

        /*
         * Ainda não existe confluência suficiente.
         */

        if (
                magnitude
                        <
                        SCORE_MINIMO
        ) {

            return new Signal(
                    "NÃO OPERAR",
                    confianca,
                    "Confluência insuficiente | "
                            +
                            detalhes
            );
        }

        /*
         * Não libera compra quando o score positivo
         * é formado apenas por sinais isolados.
         */

        if (
                score > 0
                        &&
                        !tendenciaAlta
                        &&
                        momentum5 <= 0
        ) {

            return new Signal(
                    "NÃO OPERAR",
                    confianca,
                    "Compra sem confirmação suficiente | "
                            +
                            detalhes
            );
        }

        /*
         * Mesmo princípio para venda.
         */

        if (
                score < 0
                        &&
                        !tendenciaBaixa
                        &&
                        momentum5 >= 0
        ) {

            return new Signal(
                    "NÃO OPERAR",
                    confianca,
                    "Venda sem confirmação suficiente | "
                            +
                            detalhes
            );
        }

        String direcao =
                score > 0
                        ?
                        "PARA CIMA"
                        :
                        "PARA BAIXO";

        return new Signal(
                direcao,
                confianca,
                motivo
                        +
                        detalhes
        );
    }

    /*
     * ============================================================
     * SMA
     * ============================================================
     */

    private double sma(
            List<Candle> c,
            int periodo
    ) {

        return smaAte(
                c,
                periodo,
                c.size()
        );
    }

    private double smaAte(
            List<Candle> c,
            int periodo,
            int fimExclusivo
    ) {

        if (
                periodo <= 0
                        ||
                        fimExclusivo < periodo
        ) {
            return 0;
        }

        double soma =
                0;

        for (
                int i =
                fimExclusivo - periodo;
                i < fimExclusivo;
                i++
        ) {

            soma +=
                    c.get(i).close();
        }

        return soma
                /
                periodo;
    }

    /*
     * ============================================================
     * INCLINAÇÃO DA SMA
     * ============================================================
     */

    private double inclinacaoSma(
            List<Candle> c,
            int periodo,
            int deslocamento
    ) {

        int n =
                c.size();

        if (
                n <
                        periodo
                                +
                                deslocamento
        ) {
            return 0;
        }

        double atual =
                smaAte(
                        c,
                        periodo,
                        n
                );

        double anterior =
                smaAte(
                        c,
                        periodo,
                        n - deslocamento
                );

        double preco =
                Math.max(
                        Math.abs(
                                c.get(n - 1)
                                        .close()
                        ),
                        0.0000001
                );

        return (atual - anterior)
                /
                preco;
    }

    /*
     * ============================================================
     * RETORNO
     * ============================================================
     */

    private double retorno(
            List<Candle> c,
            int candlesAtras
    ) {

        int n =
                c.size();

        int indice =
                n - 1 - candlesAtras;

        if (indice < 0) {
            return 0;
        }

        double antigo =
                c.get(indice)
                        .close();

        double atual =
                c.get(n - 1)
                        .close();

        if (
                Math.abs(antigo)
                        <
                        0.0000001
        ) {
            return 0;
        }

        return (atual - antigo)
                /
                Math.abs(antigo);
    }

    /*
     * ============================================================
     * VOLATILIDADE
     * ============================================================
     */

    private double volatilidadeNormalizada(
            List<Candle> c,
            int periodo
    ) {

        int inicio =
                Math.max(
                        0,
                        c.size() - periodo
                );

        double soma =
                0;

        int quantidade =
                0;

        for (
                int i = inicio;
                i < c.size();
                i++
        ) {

            Candle x =
                    c.get(i);

            double preco =
                    Math.max(
                            Math.abs(
                                    x.close()
                            ),
                            0.0000001
                    );

            soma +=
                    x.amplitude()
                            /
                            preco;

            quantidade++;
        }

        return quantidade == 0
                ?
                0
                :
                soma
                        /
                        quantidade;
    }

    /*
     * ============================================================
     * RSI
     * ============================================================
     */

    private double rsi(
            List<Candle> c,
            int periodo
    ) {

        if (
                c.size()
                        <=
                        periodo
        ) {
            return 50;
        }

        double ganhos =
                0;

        double perdas =
                0;

        int inicio =
                c.size()
                        -
                        periodo;

        for (
                int i = inicio;
                i < c.size();
                i++
        ) {

            double diferenca =
                    c.get(i).close()
                            -
                            c.get(i - 1).close();

            if (diferenca > 0) {

                ganhos +=
                        diferenca;

            } else if (diferenca < 0) {

                perdas -=
                        diferenca;
            }
        }

        if (
                ganhos == 0
                        &&
                        perdas == 0
        ) {
            return 50;
        }

        if (perdas == 0) {
            return 100;
        }

        if (ganhos == 0) {
            return 0;
        }

        double mediaGanhos =
                ganhos
                        /
                        periodo;

        double mediaPerdas =
                perdas
                        /
                        periodo;

        double rs =
                mediaGanhos
                        /
                        mediaPerdas;

        return 100
                -
                (
                        100
                                /
                                (1 + rs)
                );
    }

    /*
     * ============================================================
     * MÁXIMA
     * ============================================================
     */

    private double maiorMaxima(
            List<Candle> c,
            int periodo
    ) {

        int inicio =
                Math.max(
                        0,
                        c.size() - periodo
                );

        double maior =
                -Double.MAX_VALUE;

        for (
                int i = inicio;
                i < c.size();
                i++
        ) {

            maior =
                    Math.max(
                            maior,
                            c.get(i).high()
                    );
        }

        return maior;
    }

    /*
     * ============================================================
     * MÍNIMA
     * ============================================================
     */

    private double menorMinima(
            List<Candle> c,
            int periodo
    ) {

        int inicio =
                Math.max(
                        0,
                        c.size() - periodo
                );

        double menor =
                Double.MAX_VALUE;

        for (
                int i = inicio;
                i < c.size();
                i++
        ) {

            menor =
                    Math.min(
                            menor,
                            c.get(i).low()
                    );
        }

        return menor;
    }

    /*
     * ============================================================
     * FORMATAÇÃO
     * ============================================================
     */

    private String f(
            double valor
    ) {

        return String.format(
                Locale.US,
                "%.2f",
                valor
        );
    }

    private String pct(
            double valor
    ) {

        return String.format(
                Locale.US,
                "%.4f%%",
                valor * 100.0
        );
    }
}
