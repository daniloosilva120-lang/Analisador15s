package com.d4niboy.analisador;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class AiTrainer {

    /*
     * Caminhos portateis.
     *
     * Usa a mesma pasta do CandleDatasetCollector:
     * <usuario>/Analisador15s/dados_ia
     *
     * Pode ser sobrescrita sem alterar o codigo com:
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

    private static final Path DATASET =
            PASTA_DADOS.resolve(
                    "candles_janelas_10_indicadores.csv"
            );

    private static final Path MODELO =
            PASTA_DADOS.resolve(
                    "modelo_ia_10candles.csv"
            );

    private static final int CANDLES = 10;
    private static final int FEATURES_POR_CANDLE = 7;
    private static final int FEATURE_COUNT = CANDLES * FEATURES_POR_CANDLE;

    private static final int EPOCHS = 4000;
    private static final double LEARNING_RATE = 0.03;
    private static final double L2 = 0.01;

    public static void main(String[] args) {
        try {
            treinar();
        } catch (Exception e) {
            System.err.println("ERRO AO TREINAR IA:");
            e.printStackTrace();
        }
    }

    public static void treinar() throws IOException {

        if (!Files.exists(DATASET)) {
            throw new IOException(
                    "Dataset não encontrado: " + DATASET.toAbsolutePath()
            );
        }

        List<Amostra> amostras = carregarDataset(DATASET);

        if (amostras.size() < 10) {
            throw new IOException(
                    "Poucas amostras para treinar. Encontradas: "
                            + amostras.size()
            );
        }

        System.out.println("========================================");
        System.out.println("        TREINAMENTO IA - 10 CANDLES");
        System.out.println("========================================");
        System.out.println("Dataset: " + DATASET.toAbsolutePath());
        System.out.println("Amostras CIMA/BAIXO: " + amostras.size());
        System.out.println("Features: " + FEATURE_COUNT);
        System.out.println();

        int corte = Math.max(
                1,
                Math.min(
                        amostras.size() - 1,
                        (int) Math.floor(amostras.size() * 0.75)
                )
        );

        List<Amostra> treino = amostras.subList(0, corte);
        List<Amostra> teste = amostras.subList(corte, amostras.size());

        double[] media = new double[FEATURE_COUNT];
        double[] desvio = new double[FEATURE_COUNT];

        calcularNormalizacao(treino, media, desvio);

        double[] pesos = new double[FEATURE_COUNT];
        double bias = 0.0;

        for (int epoca = 1; epoca <= EPOCHS; epoca++) {

            double[] gradW = new double[FEATURE_COUNT];
            double gradB = 0.0;

            for (Amostra a : treino) {

                double z = bias;

                for (int j = 0; j < FEATURE_COUNT; j++) {
                    double x = (a.x[j] - media[j]) / desvio[j];
                    z += pesos[j] * x;
                }

                double p = sigmoid(z);
                double erro = p - a.y;

                gradB += erro;

                for (int j = 0; j < FEATURE_COUNT; j++) {
                    double x = (a.x[j] - media[j]) / desvio[j];
                    gradW[j] += erro * x;
                }
            }

            double n = treino.size();

            bias -= LEARNING_RATE * (gradB / n);

            for (int j = 0; j < FEATURE_COUNT; j++) {
                double grad =
                        (gradW[j] / n)
                                + L2 * pesos[j];

                pesos[j] -= LEARNING_RATE * grad;
            }

            if (epoca == 1
                    || epoca % 500 == 0
                    || epoca == EPOCHS) {

                double loss =
                        calcularLoss(
                                treino,
                                media,
                                desvio,
                                pesos,
                                bias
                        );

                System.out.printf(
                        Locale.US,
                        "Época %4d | loss=%.6f%n",
                        epoca,
                        loss
                );
            }
        }

        System.out.println();
        System.out.println("Divisão cronológica:");
        System.out.println("Treino: " + treino.size());
        System.out.println("Teste:  " + teste.size());
        System.out.println();

        avaliar("TREINO", treino, media, desvio, pesos, bias);
        avaliar("TESTE", teste, media, desvio, pesos, bias);

        salvarModelo(
                MODELO,
                media,
                desvio,
                pesos,
                bias
        );

        System.out.println();
        System.out.println("MODELO SALVO:");
        System.out.println(MODELO.toAbsolutePath());
        System.out.println();
        System.out.println(
                "IMPORTANTE: com poucas amostras, este modelo serve "
                        + "para validar a integração. Continue coletando dados "
                        + "antes de avaliar desempenho."
        );
    }

    private static List<Amostra> carregarDataset(Path arquivo)
            throws IOException {

        List<Amostra> saida = new ArrayList<>();

        try (BufferedReader br =
                     Files.newBufferedReader(
                             arquivo,
                             StandardCharsets.UTF_8
                     )) {

            String cabecalhoLinha = br.readLine();

            if (cabecalhoLinha == null) {
                return saida;
            }

            List<String> cabecalho =
                    parseCsv(cabecalhoLinha);

            Map<String, Integer> idx = new HashMap<>();

            for (int i = 0; i < cabecalho.size(); i++) {
                idx.put(cabecalho.get(i), i);
            }

            Integer idxResultado =
                    idx.get("resultado_15s");

            if (idxResultado == null) {
                throw new IOException(
                        "Coluna resultado_15s não encontrada."
                );
            }

            String linha;

            while ((linha = br.readLine()) != null) {

                if (linha.isBlank()) continue;

                List<String> colunas = parseCsv(linha);

                if (colunas.size() != cabecalho.size()) {
                    continue;
                }

                String resultado =
                        colunas.get(idxResultado)
                                .trim()
                                .toUpperCase(Locale.ROOT);

                if ("EMPATE".equals(resultado)) {
                    continue;
                }

                if (!"CIMA".equals(resultado)
                        && !"BAIXO".equals(resultado)) {
                    continue;
                }

                double[] x =
                        extrairFeatures(
                                colunas,
                                idx
                        );

                int y =
                        "CIMA".equals(resultado)
                                ? 1
                                : 0;

                saida.add(new Amostra(x, y));
            }
        }

        return saida;
    }

    private static double[] extrairFeatures(
            List<String> c,
            Map<String, Integer> idx
    ) {

        double[] f = new double[FEATURE_COUNT];
        int p = 0;

        for (int i = 1; i <= CANDLES; i++) {

            double open =
                    valor(c, idx, "c" + i + "_open");

            double high =
                    valor(c, idx, "c" + i + "_high");

            double low =
                    valor(c, idx, "c" + i + "_low");

            double close =
                    valor(c, idx, "c" + i + "_close");

            double corpo =
                    Math.abs(close - open);

            double amplitude =
                    Math.max(1e-12, high - low);

            double base =
                    Math.max(1e-12, Math.abs(open));

            double pavioSup =
                    high - Math.max(open, close);

            double pavioInf =
                    Math.min(open, close) - low;

            double variacao =
                    (close - open) / base;

            double direcao =
                    close > open
                            ? 1.0
                            : close < open
                            ? -1.0
                            : 0.0;

            double posicaoFechamento =
                    (close - low) / amplitude;

            f[p++] = corpo / base;
            f[p++] = amplitude / base;
            f[p++] = pavioSup / amplitude;
            f[p++] = pavioInf / amplitude;
            f[p++] = variacao;
            f[p++] = direcao;
            f[p++] = posicaoFechamento;
        }

        return f;
    }

    private static double valor(
            List<String> colunas,
            Map<String, Integer> idx,
            String nome
    ) {

        Integer i = idx.get(nome);

        if (i == null) {
            throw new IllegalArgumentException(
                    "Coluna não encontrada: " + nome
            );
        }

        return Double.parseDouble(
                colunas.get(i).trim()
        );
    }

    private static void calcularNormalizacao(
            List<Amostra> treino,
            double[] media,
            double[] desvio
    ) {

        for (Amostra a : treino) {
            for (int j = 0; j < FEATURE_COUNT; j++) {
                media[j] += a.x[j];
            }
        }

        for (int j = 0; j < FEATURE_COUNT; j++) {
            media[j] /= treino.size();
        }

        for (Amostra a : treino) {
            for (int j = 0; j < FEATURE_COUNT; j++) {
                double d = a.x[j] - media[j];
                desvio[j] += d * d;
            }
        }

        for (int j = 0; j < FEATURE_COUNT; j++) {
            desvio[j] =
                    Math.sqrt(
                            desvio[j] / treino.size()
                    );

            if (desvio[j] < 1e-12) {
                desvio[j] = 1.0;
            }
        }
    }

    private static double calcularLoss(
            List<Amostra> dados,
            double[] media,
            double[] desvio,
            double[] pesos,
            double bias
    ) {

        double loss = 0.0;

        for (Amostra a : dados) {

            double p =
                    prever(
                            a.x,
                            media,
                            desvio,
                            pesos,
                            bias
                    );

            p = Math.max(
                    1e-12,
                    Math.min(1.0 - 1e-12, p)
            );

            loss +=
                    -a.y * Math.log(p)
                            - (1 - a.y)
                            * Math.log(1.0 - p);
        }

        double reg = 0.0;

        for (double w : pesos) {
            reg += w * w;
        }

        return loss / dados.size()
                + 0.5 * L2 * reg;
    }

    private static void avaliar(
            String nome,
            List<Amostra> dados,
            double[] media,
            double[] desvio,
            double[] pesos,
            double bias
    ) {

        int corretos = 0;
        int cimaCerto = 0;
        int baixoCerto = 0;
        int totalCima = 0;
        int totalBaixo = 0;

        for (Amostra a : dados) {

            double p =
                    prever(
                            a.x,
                            media,
                            desvio,
                            pesos,
                            bias
                    );

            int previsto =
                    p >= 0.5
                            ? 1
                            : 0;

            if (a.y == 1) totalCima++;
            else totalBaixo++;

            if (previsto == a.y) {
                corretos++;

                if (a.y == 1) cimaCerto++;
                else baixoCerto++;
            }
        }

        double taxa =
                dados.isEmpty()
                        ? 0.0
                        : 100.0
                        * corretos
                        / dados.size();

        System.out.printf(
                Locale.US,
                "%s | total=%d | corretos=%d | taxa=%.2f%%"
                        + " | CIMA=%d/%d | BAIXO=%d/%d%n",
                nome,
                dados.size(),
                corretos,
                taxa,
                cimaCerto,
                totalCima,
                baixoCerto,
                totalBaixo
        );
    }

    private static double prever(
            double[] x,
            double[] media,
            double[] desvio,
            double[] pesos,
            double bias
    ) {

        double z = bias;

        for (int j = 0; j < FEATURE_COUNT; j++) {
            double n =
                    (x[j] - media[j])
                            / desvio[j];

            z += pesos[j] * n;
        }

        return sigmoid(z);
    }

    private static double sigmoid(double z) {

        if (z >= 0) {
            double e = Math.exp(-z);
            return 1.0 / (1.0 + e);
        }

        double e = Math.exp(z);
        return e / (1.0 + e);
    }

    private static void salvarModelo(
            Path arquivo,
            double[] media,
            double[] desvio,
            double[] pesos,
            double bias
    ) throws IOException {

        Files.createDirectories(
                arquivo.toAbsolutePath()
                        .getParent()
        );

        try (BufferedWriter bw =
                     Files.newBufferedWriter(
                             arquivo,
                             StandardCharsets.UTF_8
                     )) {

            bw.write("AI_MODEL_10_CANDLES_V1");
            bw.newLine();

            bw.write(
                    String.format(
                            Locale.US,
                            "bias;%.17g",
                            bias
                    )
            );
            bw.newLine();

            bw.write("feature;mean;std;weight");
            bw.newLine();

            int p = 0;

            for (int candle = 1;
                 candle <= CANDLES;
                 candle++) {

                String[] nomes = {
                        "c" + candle + "_corpo_rel",
                        "c" + candle + "_amplitude_rel",
                        "c" + candle + "_pavio_sup_rel",
                        "c" + candle + "_pavio_inf_rel",
                        "c" + candle + "_variacao",
                        "c" + candle + "_direcao",
                        "c" + candle + "_posicao_fechamento"
                };

                for (String nome : nomes) {

                    bw.write(
                            String.format(
                                    Locale.US,
                                    "%s;%.17g;%.17g;%.17g",
                                    nome,
                                    media[p],
                                    desvio[p],
                                    pesos[p]
                            )
                    );
                    bw.newLine();
                    p++;
                }
            }
        }
    }

    private static List<String> parseCsv(String linha) {

        List<String> campos =
                new ArrayList<>();

        StringBuilder atual =
                new StringBuilder();

        boolean aspas = false;

        for (int i = 0;
             i < linha.length();
             i++) {

            char ch = linha.charAt(i);

            if (ch == '"') {

                if (aspas
                        && i + 1 < linha.length()
                        && linha.charAt(i + 1) == '"') {

                    atual.append('"');
                    i++;

                } else {
                    aspas = !aspas;
                }

            } else if (ch == ','
                    && !aspas) {

                campos.add(
                        atual.toString()
                );

                atual.setLength(0);

            } else {
                atual.append(ch);
            }
        }

        campos.add(
                atual.toString()
        );

        return campos;
    }

    private record Amostra(
            double[] x,
            int y
    ) {
    }
}

