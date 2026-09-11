package com.d4niboy.analisador;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CandleDatasetCollector {

    private static final int JANELA = 10;

    /*
     * Arquivo V2 separado para não misturar o cabeçalho antigo
     * com as novas colunas de indicadores.
     */
    /*
     * Pasta portatil do dataset.
     *
     * Por padrao os dados ficam dentro da pasta do usuario atual:
     *
     * Windows:
     * C:\\Users\\USUARIO\\Analisador15s\\dados_ia
     *
     * Assim o codigo nao depende do nome do usuario,
     * da pasta do projeto ou do computador.
     *
     * Se algum dia for necessario escolher outra pasta,
     * ela pode ser informada sem alterar o codigo usando:
     *
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

    private static final Path PASTA =
            PASTA_BASE.resolve("dados_ia");

    private static final Path ARQUIVO =
            PASTA.resolve(
                    "candles_janelas_10_indicadores.csv"
            );

    private static final DateTimeFormatter DATA_HORA =
            DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());

    private final Map<String, Long> ultimoResultadoSalvo =
            new HashMap<>();

    private final CandleFeatureExtractor featureExtractor =
            new CandleFeatureExtractor();

    public CandleDatasetCollector() {
        prepararArquivo();
    }

    private void prepararArquivo() {

        try {

            Files.createDirectories(PASTA);

            if (
                    Files.notExists(ARQUIVO)
                            || Files.size(ARQUIVO) == 0
            ) {

                try (
                        BufferedWriter writer =
                                Files.newBufferedWriter(
                                        ARQUIVO,
                                        StandardCharsets.UTF_8,
                                        StandardOpenOption.CREATE,
                                        StandardOpenOption.APPEND
                                )
                ) {

                    writer.write(criarCabecalho());
                    writer.newLine();
                }
            }

            System.out.println(
                    "IA DATASET: "
                            + ARQUIVO.toAbsolutePath()
            );

        } catch (IOException e) {

            System.err.println(
                    "ERRO ao preparar dataset da IA: "
                            + e.getMessage()
            );
        }
    }

    private String criarCabecalho() {

        StringBuilder h =
                new StringBuilder();

        h.append("ativo");
        h.append(",inicio_janela");
        h.append(",fim_janela");

        for (int i = 1; i <= JANELA; i++) {

            h.append(",c").append(i).append("_open");
            h.append(",c").append(i).append("_high");
            h.append(",c").append(i).append("_low");
            h.append(",c").append(i).append("_close");
            h.append(",c").append(i).append("_corpo");
            h.append(",c").append(i).append("_amplitude");
            h.append(",c").append(i).append("_pavio_sup");
            h.append(",c").append(i).append("_pavio_inf");
            h.append(",c").append(i).append("_variacao");
            h.append(",c").append(i).append("_direcao");
        }

        h.append(",rsi14");
        h.append(",sma5");
        h.append(",sma10");
        h.append(",sma20");
        h.append(",ema5");
        h.append(",ema10");
        h.append(",ema20");

        h.append(",momentum3");
        h.append(",momentum5");
        h.append(",momentum10");

        h.append(",atr14");
        h.append(",atr14_relativo");

        h.append(",volatilidade5");
        h.append(",volatilidade10");

        h.append(",bollinger_media10");
        h.append(",bollinger_superior10");
        h.append(",bollinger_inferior10");
        h.append(",bollinger_largura10");
        h.append(",bollinger_posicao10");

        h.append(",suporte10");
        h.append(",resistencia10");
        h.append(",distancia_suporte10");
        h.append(",distancia_resistencia10");

        h.append(",distancia_sma5");
        h.append(",distancia_sma10");
        h.append(",distancia_sma20");

        h.append(",slope_sma5");
        h.append(",slope_sma10");

        h.append(",altas_10");
        h.append(",baixas_10");
        h.append(",empates_10");
        h.append(",tendencia_10");

        h.append(",corpo_medio_rel_10");
        h.append(",pavio_sup_medio_rel_10");
        h.append(",pavio_inf_medio_rel_10");
        h.append(",amplitude_media_rel_10");

        h.append(",sequencia_atual");
        h.append(",direcao_sequencia");

        h.append(",corpo_atual_rel");
        h.append(",pavio_sup_atual_rel");
        h.append(",pavio_inf_atual_rel");
        h.append(",posicao_fechamento_atual");

        h.append(",preco_base");
        h.append(",preco_15s_depois");
        h.append(",resultado_15s");
        h.append(",timestamp_resultado");

        return h.toString();
    }

    public synchronized void registrarJanela(
            String ativo,
            List<Candle> candles
    ) {

        if (
                ativo == null
                        || ativo.isBlank()
                        || candles == null
        ) {
            return;
        }

        /*
         * Agora precisamos somente de 10 candles fechados ANTES
         * do candle de resultado.
         *
         * Exemplo:
         * candles 1..10 = entrada da IA
         * candle 11     = resultado 15s depois
         *
         * As colunas antigas rsi14/sma20/ema20/atr14 continuam
         * no CSV apenas para manter compatibilidade com arquivos
         * já coletados. O CandleFeatureExtractor calcula versões
         * compatíveis com a janela de 10 candles.
         *
         * O AiTrainer atual usa as 70 características derivadas
         * diretamente dos 10 candles, não essas colunas extras.
         */
        int indiceResultado =
                candles.size() - 1;

        if (
                indiceResultado
                        < CandleFeatureExtractor.HISTORICO_MINIMO
        ) {
            return;
        }

        Candle candleResultado =
                candles.get(indiceResultado);

        Long ultimo =
                ultimoResultadoSalvo.get(ativo);

        if (
                ultimo != null
                        && ultimo.longValue()
                        == candleResultado.timestamp()
        ) {
            return;
        }

        int inicioJanela =
                indiceResultado - JANELA;

        List<Candle> janela =
                candles.subList(
                        inicioJanela,
                        indiceResultado
                );

        CandleFeatureExtractor.Features features =
                featureExtractor.extrair(
                        candles,
                        indiceResultado
                );

        if (features == null) {
            return;
        }

        salvar(
                ativo,
                janela,
                candleResultado,
                features
        );

        ultimoResultadoSalvo.put(
                ativo,
                candleResultado.timestamp()
        );
    }

    private void salvar(
            String ativo,
            List<Candle> janela,
            Candle candleResultado,
            CandleFeatureExtractor.Features f
    ) {

        Candle primeiro =
                janela.get(0);

        Candle ultimo =
                janela.get(
                        janela.size() - 1
                );

        double precoBase =
                ultimo.close();

        double precoDepois =
                candleResultado.close();

        String resultado;

        if (precoDepois > precoBase) {
            resultado = "CIMA";
        } else if (precoDepois < precoBase) {
            resultado = "BAIXO";
        } else {
            resultado = "EMPATE";
        }

        StringBuilder linha =
                new StringBuilder();

        linha.append(csv(ativo));

        linha.append(',').append(
                csv(
                        DATA_HORA.format(
                                Instant.ofEpochMilli(
                                        primeiro.timestamp()
                                )
                        )
                )
        );

        linha.append(',').append(
                csv(
                        DATA_HORA.format(
                                Instant.ofEpochMilli(
                                        ultimo.timestamp()
                                )
                        )
                )
        );

        for (Candle candle : janela) {

            double open = candle.open();
            double high = candle.high();
            double low = candle.low();
            double close = candle.close();

            double corpo =
                    Math.abs(close - open);

            double amplitude =
                    Math.max(
                            0.0000000001,
                            high - low
                    );

            double pavioSuperior =
                    high - Math.max(open, close);

            double pavioInferior =
                    Math.min(open, close) - low;

            double variacao =
                    open != 0.0
                            ? (close - open) / open
                            : 0.0;

            String direcao =
                    close > open
                            ? "ALTA"
                            : close < open
                            ? "BAIXA"
                            : "EMPATE";

            linha.append(',').append(numero(open));
            linha.append(',').append(numero(high));
            linha.append(',').append(numero(low));
            linha.append(',').append(numero(close));
            linha.append(',').append(numero(corpo));
            linha.append(',').append(numero(amplitude));
            linha.append(',').append(numero(pavioSuperior));
            linha.append(',').append(numero(pavioInferior));
            linha.append(',').append(numero(variacao));
            linha.append(',').append(direcao);
        }

        linha.append(',').append(numero(f.rsi14()));

        linha.append(',').append(numero(f.sma5()));
        linha.append(',').append(numero(f.sma10()));
        linha.append(',').append(numero(f.sma20()));

        linha.append(',').append(numero(f.ema5()));
        linha.append(',').append(numero(f.ema10()));
        linha.append(',').append(numero(f.ema20()));

        linha.append(',').append(numero(f.momentum3()));
        linha.append(',').append(numero(f.momentum5()));
        linha.append(',').append(numero(f.momentum10()));

        linha.append(',').append(numero(f.atr14()));
        linha.append(',').append(numero(f.atr14Relativo()));

        linha.append(',').append(numero(f.volatilidade5()));
        linha.append(',').append(numero(f.volatilidade10()));

        linha.append(',').append(numero(f.bollingerMedia10()));
        linha.append(',').append(numero(f.bollingerSuperior10()));
        linha.append(',').append(numero(f.bollingerInferior10()));
        linha.append(',').append(numero(f.bollingerLargura10()));
        linha.append(',').append(numero(f.bollingerPosicao10()));

        linha.append(',').append(numero(f.suporte10()));
        linha.append(',').append(numero(f.resistencia10()));
        linha.append(',').append(numero(f.distanciaSuporte10()));
        linha.append(',').append(numero(f.distanciaResistencia10()));

        linha.append(',').append(numero(f.distanciaSma5()));
        linha.append(',').append(numero(f.distanciaSma10()));
        linha.append(',').append(numero(f.distanciaSma20()));

        linha.append(',').append(numero(f.slopeSma5()));
        linha.append(',').append(numero(f.slopeSma10()));

        linha.append(',').append(f.altas10());
        linha.append(',').append(f.baixas10());
        linha.append(',').append(f.empates10());
        linha.append(',').append(numero(f.tendencia10()));

        linha.append(',').append(numero(f.corpoMedioRel10()));
        linha.append(',').append(numero(f.pavioSuperiorMedioRel10()));
        linha.append(',').append(numero(f.pavioInferiorMedioRel10()));
        linha.append(',').append(numero(f.amplitudeMediaRel10()));

        linha.append(',').append(f.sequenciaAtual());
        linha.append(',').append(f.direcaoSequencia());

        linha.append(',').append(numero(f.corpoAtualRel()));
        linha.append(',').append(numero(f.pavioSuperiorAtualRel()));
        linha.append(',').append(numero(f.pavioInferiorAtualRel()));
        linha.append(',').append(numero(f.posicaoFechamentoAtual()));

        linha.append(',').append(numero(precoBase));
        linha.append(',').append(numero(precoDepois));
        linha.append(',').append(resultado);
        linha.append(',').append(
                candleResultado.timestamp()
        );

        try (
                BufferedWriter writer =
                        Files.newBufferedWriter(
                                ARQUIVO,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.APPEND
                        )
        ) {

            writer.write(linha.toString());
            writer.newLine();

            System.out.println(
                    "IA DATASET: indicadores + 10 candles -> "
                            + resultado
                            + " | "
                            + ativo
                            + " | amostras acumulando"
            );

        } catch (IOException e) {

            System.err.println(
                    "ERRO ao salvar amostra da IA: "
                            + e.getMessage()
            );
        }
    }

    private String numero(double valor) {

        return String.format(
                Locale.US,
                "%.12f",
                valor
        );
    }

    private String csv(String texto) {

        if (texto == null) {
            return "\"\"";
        }

        return "\""
                + texto.replace(
                "\"",
                "\"\""
        )
                + "\"";
    }

    public Path getArquivo() {
        return ARQUIVO.toAbsolutePath();
    }
}


