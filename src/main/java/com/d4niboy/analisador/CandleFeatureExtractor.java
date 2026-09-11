package com.d4niboy.analisador;

import java.util.List;

public class CandleFeatureExtractor {

    public static final int HISTORICO_MINIMO = 10;

    public Features extrair(List<Candle> candles, int fimExclusivo) {

        if (candles == null
                || fimExclusivo < HISTORICO_MINIMO
                || fimExclusivo > candles.size()) {
            return null;
        }

        Candle atual = candles.get(fimExclusivo - 1);
        double preco = seguro(Math.abs(atual.close()));

        double sma5 = sma(candles, fimExclusivo, 5);
        double sma10 = sma(candles, fimExclusivo, 10);
        /*
         * Mantemos estes nomes no record/CSV por compatibilidade com
         * datasets já existentes. Com histórico mínimo de 10 candles,
         * o antigo SMA20 passa a usar a janela máxima disponível: 10.
         */
        double sma20 = sma(candles, fimExclusivo, 10);

        double ema5 = ema(candles, fimExclusivo, 5);
        double ema10 = ema(candles, fimExclusivo, 10);
        /*
         * Compatibilidade com a coluna antiga ema20.
         * Com 10 candles, usamos EMA10 para não exigir histórico extra.
         */
        double ema20 = ema(candles, fimExclusivo, 10);

        /*
         * Dez candles possuem 9 variações entre fechamentos.
         * Por isso o RSI usa período 9.
         * O nome rsi14 é mantido somente para compatibilidade do CSV.
         */
        double rsi14 = rsi(candles, fimExclusivo, 9);

        double momentum3 = retorno(candles, fimExclusivo, 3);
        double momentum5 = retorno(candles, fimExclusivo, 5);
        /*
         * Com uma janela de 10 candles, o maior retorno possível compara
         * o primeiro candle com o décimo: distância de 9 candles.
         * O nome momentum10 é mantido para compatibilidade do CSV.
         */
        double momentum10 = retorno(candles, fimExclusivo, 9);

        /*
         * ATR9: 10 candles fornecem 9 True Ranges com fechamento anterior.
         * O nome atr14 é mantido para compatibilidade do CSV.
         */
        double atr14 = atr(candles, fimExclusivo, 9);
        double atr14Relativo = atr14 / preco;

        double volatilidade5 = volatilidadeRetornos(candles, fimExclusivo, 5);
        double volatilidade10 = volatilidadeRetornos(candles, fimExclusivo, 10);

        Bollinger bollinger = bollinger(candles, fimExclusivo, 10, 2.0);

        double suporte10 = menorMinima(candles, fimExclusivo, 10);
        double resistencia10 = maiorMaxima(candles, fimExclusivo, 10);

        double distanciaSuporte =
                (atual.close() - suporte10) / preco;

        double distanciaResistencia =
                (resistencia10 - atual.close()) / preco;

        double distanciaSma5 =
                (atual.close() - sma5) / preco;

        double distanciaSma10 =
                (atual.close() - sma10) / preco;

        double distanciaSma20 =
                (atual.close() - sma20) / preco;

        double slopeSma5 =
                slopeLinear(candles, fimExclusivo, 5);

        double slopeSma10 =
                slopeLinear(candles, fimExclusivo, 10);

        int altas10 = 0;
        int baixas10 = 0;
        int empates10 = 0;
        int sequenciaAtual = 0;
        int direcaoSequencia = 0;

        double somaCorpoRel = 0.0;
        double somaPavioSupRel = 0.0;
        double somaPavioInfRel = 0.0;
        double somaAmplitudeRel = 0.0;

        int inicio10 = fimExclusivo - 10;

        for (int i = inicio10; i < fimExclusivo; i++) {

            Candle c = candles.get(i);

            double amplitude =
                    seguro(c.high() - c.low());

            double base =
                    seguro(Math.abs(c.open()));

            double corpo =
                    Math.abs(c.close() - c.open());

            double pavioSup =
                    c.high() - Math.max(c.open(), c.close());

            double pavioInf =
                    Math.min(c.open(), c.close()) - c.low();

            somaCorpoRel += corpo / amplitude;
            somaPavioSupRel += pavioSup / amplitude;
            somaPavioInfRel += pavioInf / amplitude;
            somaAmplitudeRel += amplitude / base;

            if (c.close() > c.open()) {
                altas10++;
            } else if (c.close() < c.open()) {
                baixas10++;
            } else {
                empates10++;
            }
        }

        for (int i = fimExclusivo - 1; i >= inicio10; i--) {

            Candle c = candles.get(i);

            int direcao =
                    c.close() > c.open()
                            ? 1
                            : c.close() < c.open()
                            ? -1
                            : 0;

            if (direcao == 0) {
                break;
            }

            if (direcaoSequencia == 0) {
                direcaoSequencia = direcao;
            }

            if (direcao != direcaoSequencia) {
                break;
            }

            sequenciaAtual++;
        }

        double amplitudeAtual =
                seguro(atual.high() - atual.low());

        double corpoAtualRel =
                Math.abs(atual.close() - atual.open())
                        / amplitudeAtual;

        double pavioSuperiorAtualRel =
                (atual.high()
                        - Math.max(atual.open(), atual.close()))
                        / amplitudeAtual;

        double pavioInferiorAtualRel =
                (Math.min(atual.open(), atual.close())
                        - atual.low())
                        / amplitudeAtual;

        double posicaoFechamento =
                (atual.close() - atual.low())
                        / amplitudeAtual;

        double tendencia10 =
                (atual.close()
                        - candles.get(inicio10).open())
                        / seguro(Math.abs(candles.get(inicio10).open()));

        return new Features(
                rsi14,
                sma5,
                sma10,
                sma20,
                ema5,
                ema10,
                ema20,
                momentum3,
                momentum5,
                momentum10,
                atr14,
                atr14Relativo,
                volatilidade5,
                volatilidade10,
                bollinger.media(),
                bollinger.superior(),
                bollinger.inferior(),
                bollinger.larguraRelativa(),
                bollinger.posicao(),
                suporte10,
                resistencia10,
                distanciaSuporte,
                distanciaResistencia,
                distanciaSma5,
                distanciaSma10,
                distanciaSma20,
                slopeSma5,
                slopeSma10,
                altas10,
                baixas10,
                empates10,
                tendencia10,
                somaCorpoRel / 10.0,
                somaPavioSupRel / 10.0,
                somaPavioInfRel / 10.0,
                somaAmplitudeRel / 10.0,
                sequenciaAtual,
                direcaoSequencia,
                corpoAtualRel,
                pavioSuperiorAtualRel,
                pavioInferiorAtualRel,
                posicaoFechamento
        );
    }

    private double sma(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        double soma = 0.0;

        for (int i = fim - periodo; i < fim; i++) {
            soma += c.get(i).close();
        }

        return soma / periodo;
    }

    private double ema(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        int inicio =
                Math.max(0, fim - Math.max(periodo * 4, periodo));

        double alpha =
                2.0 / (periodo + 1.0);

        double valor =
                c.get(inicio).close();

        for (int i = inicio + 1; i < fim; i++) {
            valor =
                    alpha * c.get(i).close()
                            + (1.0 - alpha) * valor;
        }

        return valor;
    }

    private double rsi(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        double ganhos = 0.0;
        double perdas = 0.0;

        int inicio = fim - periodo;

        for (int i = inicio; i < fim; i++) {

            double anterior =
                    c.get(i - 1).close();

            double atual =
                    c.get(i).close();

            double diferenca =
                    atual - anterior;

            if (diferenca > 0) {
                ganhos += diferenca;
            } else if (diferenca < 0) {
                perdas -= diferenca;
            }
        }

        if (ganhos == 0.0 && perdas == 0.0) {
            return 50.0;
        }

        if (perdas == 0.0) {
            return 100.0;
        }

        if (ganhos == 0.0) {
            return 0.0;
        }

        double rs =
                (ganhos / periodo)
                        / (perdas / periodo);

        return 100.0
                - (100.0 / (1.0 + rs));
    }

    private double retorno(
            List<Candle> c,
            int fim,
            int candlesAtras
    ) {

        double atual =
                c.get(fim - 1).close();

        double anterior =
                c.get(fim - 1 - candlesAtras).close();

        return (atual - anterior)
                / seguro(Math.abs(anterior));
    }

    private double atr(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        double soma = 0.0;

        for (int i = fim - periodo; i < fim; i++) {

            Candle atual = c.get(i);
            double fechamentoAnterior =
                    c.get(i - 1).close();

            double tr =
                    Math.max(
                            atual.high() - atual.low(),
                            Math.max(
                                    Math.abs(
                                            atual.high()
                                                    - fechamentoAnterior
                                    ),
                                    Math.abs(
                                            atual.low()
                                                    - fechamentoAnterior
                                    )
                            )
                    );

            soma += tr;
        }

        return soma / periodo;
    }

    private double volatilidadeRetornos(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        double[] retornos =
                new double[periodo];

        double media = 0.0;

        for (int j = 0; j < periodo; j++) {

            int i =
                    fim - periodo + j;

            double anterior =
                    c.get(i - 1).close();

            double atual =
                    c.get(i).close();

            retornos[j] =
                    (atual - anterior)
                            / seguro(Math.abs(anterior));

            media += retornos[j];
        }

        media /= periodo;

        double soma = 0.0;

        for (double r : retornos) {
            double d = r - media;
            soma += d * d;
        }

        return Math.sqrt(soma / periodo);
    }

    private Bollinger bollinger(
            List<Candle> c,
            int fim,
            int periodo,
            double desvios
    ) {

        double media =
                sma(c, fim, periodo);

        double soma = 0.0;

        for (int i = fim - periodo; i < fim; i++) {

            double d =
                    c.get(i).close() - media;

            soma += d * d;
        }

        double desvio =
                Math.sqrt(soma / periodo);

        double superior =
                media + desvios * desvio;

        double inferior =
                media - desvios * desvio;

        double largura =
                (superior - inferior)
                        / seguro(Math.abs(media));

        double posicao =
                (c.get(fim - 1).close() - inferior)
                        / seguro(superior - inferior);

        return new Bollinger(
                media,
                superior,
                inferior,
                largura,
                posicao
        );
    }

    private double maiorMaxima(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        double maior =
                -Double.MAX_VALUE;

        for (int i = fim - periodo; i < fim; i++) {
            maior =
                    Math.max(
                            maior,
                            c.get(i).high()
                    );
        }

        return maior;
    }

    private double menorMinima(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        double menor =
                Double.MAX_VALUE;

        for (int i = fim - periodo; i < fim; i++) {
            menor =
                    Math.min(
                            menor,
                            c.get(i).low()
                    );
        }

        return menor;
    }

    private double slopeLinear(
            List<Candle> c,
            int fim,
            int periodo
    ) {

        if (periodo < 2 || fim < periodo) {
            return 0.0;
        }

        int inicio = fim - periodo;

        double mediaX = (periodo - 1) / 2.0;
        double mediaY = 0.0;

        for (int i = 0; i < periodo; i++) {
            mediaY += c.get(inicio + i).close();
        }

        mediaY /= periodo;

        double numerador = 0.0;
        double denominador = 0.0;

        for (int i = 0; i < periodo; i++) {

            double dx = i - mediaX;
            double dy = c.get(inicio + i).close() - mediaY;

            numerador += dx * dy;
            denominador += dx * dx;
        }

        if (denominador <= 0.0) {
            return 0.0;
        }

        double preco =
                seguro(
                        Math.abs(
                                c.get(fim - 1).close()
                        )
                );

        return (numerador / denominador) / preco;
    }

    private double seguro(double valor) {
        return Math.max(valor, 0.0000000001);
    }

    private record Bollinger(
            double media,
            double superior,
            double inferior,
            double larguraRelativa,
            double posicao
    ) {
    }

    public record Features(
            double rsi14,
            double sma5,
            double sma10,
            double sma20,
            double ema5,
            double ema10,
            double ema20,
            double momentum3,
            double momentum5,
            double momentum10,
            double atr14,
            double atr14Relativo,
            double volatilidade5,
            double volatilidade10,
            double bollingerMedia10,
            double bollingerSuperior10,
            double bollingerInferior10,
            double bollingerLargura10,
            double bollingerPosicao10,
            double suporte10,
            double resistencia10,
            double distanciaSuporte10,
            double distanciaResistencia10,
            double distanciaSma5,
            double distanciaSma10,
            double distanciaSma20,
            double slopeSma5,
            double slopeSma10,
            int altas10,
            int baixas10,
            int empates10,
            double tendencia10,
            double corpoMedioRel10,
            double pavioSuperiorMedioRel10,
            double pavioInferiorMedioRel10,
            double amplitudeMediaRel10,
            int sequenciaAtual,
            int direcaoSequencia,
            double corpoAtualRel,
            double pavioSuperiorAtualRel,
            double pavioInferiorAtualRel,
            double posicaoFechamentoAtual
    ) {
    }
}

