package com.d4niboy.analisador;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AiDecisionEngine {

    private static final int MIN_CANDLES = 10;

    /*
     * Caminho portatil do modelo.
     *
     * Usa a mesma pasta do CandleDatasetCollector e do AiTrainer:
     * <usuario>/Analisador15s/dados_ia
     *
     * Pode ser alterado sem modificar o codigo usando:
     * -Danalisador.dados.dir="D:\\MinhaPasta"
     */
    private static final Path PASTA_BASE =
            Path.of(
                    System.getProperty(
                            "analisador.dados.dir",
                            Path.of(
                                    System.getProperty("user.home"),
                                    "Analisador15s"
                            ).toString()
                    )
            );

    private static final Path PASTA_DADOS =
            PASTA_BASE.resolve("dados_ia");

    private static final Path MODELO =
            PASTA_DADOS.resolve(
                    "modelo_ia_10candles.csv"
            );

    private final double[] medias;
    private final double[] desvios;
    private final double[] pesos;
    private final double bias;

    public AiDecisionEngine() throws IOException {

        Modelo m =
                carregarModelo(MODELO);

        this.medias = m.medias;
        this.desvios = m.desvios;
        this.pesos = m.pesos;
        this.bias = m.bias;

        System.out.println(
                "IA: modelo carregado -> "
                        + MODELO.toAbsolutePath()
        );
    }

    public Signal decidir(
            List<Candle> candles
    ) {

        if (candles == null
                || candles.size() < MIN_CANDLES) {

            return null;
        }

        double[] x =
                extrairUltimos10(candles);

        double saida =
                prever(x);

        String direcao =
                saida >= 0.5
                        ? "CIMA"
                        : "BAIXO";

        String motivo =
                String.format(
                        Locale.US,
                        "IA 10 candles | saída=%.4f",
                        saida
                );

        return new Signal(
                direcao,
                motivo
        );
    }

    private double prever(
            double[] x
    ) {

        double z = bias;

        for (int i = 0;
             i < pesos.length;
             i++) {

            double normalizado =
                    (x[i] - medias[i])
                            / desvios[i];

            z +=
                    pesos[i]
                            * normalizado;
        }

        return sigmoid(z);
    }

    private double[] extrairUltimos10(
            List<Candle> candles
    ) {

        int inicio =
                candles.size()
                        - MIN_CANDLES;

        double[] f =
                new double[
                        MIN_CANDLES * 7
                        ];

        int p = 0;

        for (int i = inicio;
             i < candles.size();
             i++) {

            Candle c =
                    candles.get(i);

            double open =
                    c.open();

            double high =
                    c.high();

            double low =
                    c.low();

            double close =
                    c.close();

            double amplitude =
                    Math.max(
                            1e-12,
                            high - low
                    );

            double base =
                    Math.max(
                            1e-12,
                            Math.abs(open)
                    );

            double corpo =
                    Math.abs(
                            close - open
                    );

            double pavioSup =
                    high
                            - Math.max(
                            open,
                            close
                    );

            double pavioInf =
                    Math.min(
                            open,
                            close
                    ) - low;

            double variacao =
                    (close - open)
                            / base;

            double direcao =
                    close > open
                            ? 1.0
                            : close < open
                            ? -1.0
                            : 0.0;

            double posicaoFechamento =
                    (close - low)
                            / amplitude;

            f[p++] =
                    corpo / base;

            f[p++] =
                    amplitude / base;

            f[p++] =
                    pavioSup / amplitude;

            f[p++] =
                    pavioInf / amplitude;

            f[p++] =
                    variacao;

            f[p++] =
                    direcao;

            f[p++] =
                    posicaoFechamento;
        }

        return f;
    }

    private static Modelo carregarModelo(
            Path arquivo
    ) throws IOException {

        if (!Files.exists(arquivo)) {
            throw new IOException(
                    "Modelo da IA não encontrado: "
                            + arquivo.toAbsolutePath()
                            + System.lineSeparator()
                            + "Execute AiTrainer primeiro."
            );
        }

        try (BufferedReader br =
                     Files.newBufferedReader(
                             arquivo,
                             StandardCharsets.UTF_8
                     )) {

            String versao =
                    br.readLine();

            if (!"AI_MODEL_10_CANDLES_V1"
                    .equals(versao)) {

                throw new IOException(
                        "Versão de modelo inválida."
                );
            }

            String linhaBias =
                    br.readLine();

            if (linhaBias == null
                    || !linhaBias
                    .startsWith("bias;")) {

                throw new IOException(
                        "Bias não encontrado no modelo."
                );
            }

            double bias =
                    Double.parseDouble(
                            linhaBias.substring(
                                    "bias;".length()
                            )
                    );

            String cabecalho =
                    br.readLine();

            if (cabecalho == null
                    || !cabecalho
                    .startsWith("feature;")) {

                throw new IOException(
                        "Cabeçalho do modelo inválido."
                );
            }

            List<Double> medias =
                    new ArrayList<>();

            List<Double> desvios =
                    new ArrayList<>();

            List<Double> pesos =
                    new ArrayList<>();

            String linha;

            while ((linha = br.readLine())
                    != null) {

                if (linha.isBlank()) continue;

                String[] p =
                        linha.split(";");

                if (p.length != 4) {
                    continue;
                }

                medias.add(
                        Double.parseDouble(p[1])
                );

                desvios.add(
                        Double.parseDouble(p[2])
                );

                pesos.add(
                        Double.parseDouble(p[3])
                );
            }

            if (pesos.size() != 70) {
                throw new IOException(
                        "Modelo incompleto. "
                                + "Features encontradas: "
                                + pesos.size()
                                + " | esperado: 70"
                );
            }

            return new Modelo(
                    toArray(medias),
                    toArray(desvios),
                    toArray(pesos),
                    bias
            );
        }
    }

    private static double[] toArray(
            List<Double> lista
    ) {

        double[] v =
                new double[lista.size()];

        for (int i = 0;
             i < v.length;
             i++) {

            v[i] =
                    lista.get(i);
        }

        return v;
    }

    private static double sigmoid(
            double z
    ) {

        if (z >= 0) {
            double e =
                    Math.exp(-z);

            return 1.0
                    / (1.0 + e);
        }

        double e =
                Math.exp(z);

        return e
                / (1.0 + e);
    }

    private record Modelo(
            double[] medias,
            double[] desvios,
            double[] pesos,
            double bias
    ) {
    }
}
