package com.d4niboy.analisador;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignalReceiverWindow
        extends JFrame {

    /*
     * Usa a mesma configuracao portatil do SignalHttpServer.
     */
    private static final String SERVER_HOST =
            System.getProperty(
                    "analisador.signal.host",
                    "127.0.0.1"
            );

    private static final int SERVER_PORT =
            Integer.getInteger(
                    "analisador.signal.port",
                    8765
            );

    private static final String URL_SINAL =
            "http://"
                    + SERVER_HOST
                    + ":"
                    + SERVER_PORT
                    + "/sinal";

    private static final DateTimeFormatter HORARIO =
            DateTimeFormatter
                    .ofPattern(
                            "HH:mm:ss"
                    )
                    .withZone(
                            ZoneId.systemDefault()
                    );

    private final JLabel statusLabel =
            new JLabel(
                    "AGUARDANDO SINAL...",
                    SwingConstants.CENTER
            );

    private final JLabel ativoLabel =
            new JLabel(
                    "---",
                    SwingConstants.CENTER
            );

    private final JLabel direcaoLabel =
            new JLabel(
                    "---",
                    SwingConstants.CENTER
            );

    private final JLabel confiancaLabel =
            new JLabel(
                    "Confiança: ---",
                    SwingConstants.CENTER
            );

    private final JLabel payoutLabel =
            new JLabel(
                    "Payout: ---",
                    SwingConstants.CENTER
            );

    private final JLabel horarioLabel =
            new JLabel(
                    "Último sinal: ---",
                    SwingConstants.CENTER
            );

    private volatile long ultimoTimestamp =
            -1;

    public SignalReceiverWindow() {

        super(
                "Analisador15s"
        );

        configurarJanela();

        iniciarMonitoramento();
    }

    private void configurarJanela() {

        setDefaultCloseOperation(
                JFrame.DISPOSE_ON_CLOSE
        );

        setSize(
                450,
                380
        );

        setLocationRelativeTo(
                null
        );

        setAlwaysOnTop(
                true
        );

        JPanel painel =
                new JPanel();

        painel.setLayout(
                new BoxLayout(
                        painel,
                        BoxLayout.Y_AXIS
                )
        );

        painel.setBorder(
                new EmptyBorder(
                        25,
                        30,
                        25,
                        30
                )
        );

        JLabel titulo =
                new JLabel(
                        "ANALISADOR 15s",
                        SwingConstants.CENTER
                );

        titulo.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        22
                )
        );

        titulo.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        statusLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        15
                )
        );

        statusLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        statusLabel.setForeground(
                new Color(
                        0,
                        140,
                        0
                )
        );

        ativoLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        28
                )
        );

        ativoLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        direcaoLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        25
                )
        );

        direcaoLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        confiancaLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        16
                )
        );

        confiancaLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        payoutLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        16
                )
        );

        payoutLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        horarioLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        painel.add(
                titulo
        );

        painel.add(
                Box.createVerticalStrut(
                        15
                )
        );

        painel.add(
                statusLabel
        );

        painel.add(
                Box.createVerticalStrut(
                        20
                )
        );

        painel.add(
                ativoLabel
        );

        painel.add(
                Box.createVerticalStrut(
                        10
                )
        );

        painel.add(
                direcaoLabel
        );

        painel.add(
                Box.createVerticalStrut(
                        15
                )
        );

        painel.add(
                confiancaLabel
        );

        painel.add(
                Box.createVerticalStrut(
                        5
                )
        );

        painel.add(
                payoutLabel
        );

        painel.add(
                Box.createVerticalStrut(
                        10
                )
        );

        painel.add(
                horarioLabel
        );

        setContentPane(
                painel
        );
    }

    private void iniciarMonitoramento() {

        Thread thread =
                new Thread(
                        () -> {

                            while (
                                    !Thread
                                            .currentThread()
                                            .isInterrupted()
                            ) {

                                try {

                                    consultarSinal();

                                    Thread.sleep(
                                            300
                                    );

                                } catch (
                                        InterruptedException e
                                ) {

                                    Thread
                                            .currentThread()
                                            .interrupt();

                                    break;

                                } catch (
                                        Exception e
                                ) {

                                    mostrarServidorIndisponivel();

                                    try {

                                        Thread.sleep(
                                                1500
                                        );

                                    } catch (
                                            InterruptedException ex
                                    ) {

                                        Thread
                                                .currentThread()
                                                .interrupt();

                                        break;
                                    }
                                }
                            }
                        },
                        "SignalReceiver"
                );

        thread.setDaemon(
                true
        );

        thread.start();
    }

    private void consultarSinal()
            throws Exception {

        /*
         * Adiciona um valor diferente a cada consulta.
         *
         * Isso impede qualquer reaproveitamento
         * da resposta HTTP anterior.
         */
        String url =
                URL_SINAL
                        + "?t="
                        + System.nanoTime();

        HttpURLConnection conexao =
                (HttpURLConnection)
                        URI
                                .create(
                                        url
                                )
                                .toURL()
                                .openConnection();

        conexao.setUseCaches(
                false
        );

        conexao.setDefaultUseCaches(
                false
        );

        conexao.setRequestProperty(
                "Cache-Control",
                "no-cache, no-store"
        );

        conexao.setRequestProperty(
                "Pragma",
                "no-cache"
        );

        conexao.setRequestMethod(
                "GET"
        );

        conexao.setConnectTimeout(
                1000
        );

        conexao.setReadTimeout(
                1000
        );

        StringBuilder json =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        conexao.getInputStream(),
                                        StandardCharsets.UTF_8
                                )
                        )
        ) {

            String linha;

            while (
                    (linha = reader.readLine())
                            != null
            ) {

                json.append(
                        linha
                );
            }

        } finally {

            conexao.disconnect();
        }

        String resposta =
                json.toString();

        if (
                resposta.contains(
                        "\"AGUARDANDO_SINAL\""
                )
        ) {

            return;
        }

        long timestamp =
                extrairLong(
                        resposta,
                        "timestamp"
                );

        if (
                timestamp <= 0
                        ||
                        timestamp
                                == ultimoTimestamp
        ) {

            return;
        }

        String ativo =
                extrairTexto(
                        resposta,
                        "ativo"
                );

        String direcao =
                extrairTexto(
                        resposta,
                        "direcao"
                );

        double confianca =
                extrairDouble(
                        resposta,
                        "confianca"
                );

        double payout =
                extrairDouble(
                        resposta,
                        "payout"
                );

        /*
         * Guarda o timestamp somente depois
         * que todos os campos foram lidos.
         */
        ultimoTimestamp =
                timestamp;

        mostrarNovoSinal(
                ativo,
                direcao,
                confianca,
                payout,
                timestamp
        );
    }

    private void mostrarServidorIndisponivel() {

        SwingUtilities.invokeLater(
                () ->
                        statusLabel.setText(
                                "Servidor indisponível..."
                        )
        );
    }

    private void mostrarNovoSinal(
            String ativo,
            String direcao,
            double confianca,
            double payout,
            long timestamp
    ) {

        SwingUtilities.invokeLater(
                () -> {

                    ativoLabel.setText(
                            formatarAtivo(
                                    ativo
                            )
                    );

                    direcaoLabel.setText(
                            direcao
                    );

                    if (
                            "PARA CIMA"
                                    .equalsIgnoreCase(
                                            direcao
                                    )
                    ) {

                        direcaoLabel.setForeground(
                                new Color(
                                        0,
                                        140,
                                        0
                                )
                        );

                    } else if (
                            "PARA BAIXO"
                                    .equalsIgnoreCase(
                                            direcao
                                    )
                    ) {

                        direcaoLabel.setForeground(
                                new Color(
                                        190,
                                        0,
                                        0
                                )
                        );

                    } else {

                        direcaoLabel.setForeground(
                                Color.BLACK
                        );
                    }

                    confiancaLabel.setText(
                            String.format(
                                    Locale.US,
                                    "Confiança heurística: %.2f%%",
                                    confianca
                            )
                    );

                    payoutLabel.setText(
                            String.format(
                                    Locale.US,
                                    "Payout: %.0f%%",
                                    payout
                            )
                    );

                    horarioLabel.setText(
                            "Último sinal: "
                                    + HORARIO.format(
                                    Instant.ofEpochMilli(
                                            timestamp
                                    )
                            )
                    );

                    statusLabel.setText(
                            "SINAL RECEBIDO"
                    );

                    Toolkit
                            .getDefaultToolkit()
                            .beep();

                    /*
                     * Se a janela estiver aberta,
                     * os labels acima são simplesmente
                     * substituídos pelo sinal atual.
                     *
                     * Não cria histórico.
                     */
                    if (!isVisible()) {

                        setVisible(
                                true
                        );
                    }

                    repaint();
                    revalidate();
                }
        );
    }

    private String extrairTexto(
            String json,
            String campo
    ) {

        Matcher matcher =
                Pattern
                        .compile(
                                "\""
                                        + Pattern.quote(
                                        campo
                                )
                                        + "\"\\s*:\\s*\"([^\"]*)\""
                        )
                        .matcher(
                                json
                        );

        return matcher.find()
                ? matcher.group(1)
                : "";
    }

    private double extrairDouble(
            String json,
            String campo
    ) {

        Matcher matcher =
                Pattern
                        .compile(
                                "\""
                                        + Pattern.quote(
                                        campo
                                )
                                        + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)"
                        )
                        .matcher(
                                json
                        );

        if (!matcher.find()) {
            return 0;
        }

        try {

            return Double.parseDouble(
                    matcher.group(1)
            );

        } catch (
                NumberFormatException e
        ) {

            return 0;
        }
    }

    private long extrairLong(
            String json,
            String campo
    ) {

        Matcher matcher =
                Pattern
                        .compile(
                                "\""
                                        + Pattern.quote(
                                        campo
                                )
                                        + "\"\\s*:\\s*(\\d+)"
                        )
                        .matcher(
                                json
                        );

        if (!matcher.find()) {
            return -1;
        }

        try {

            return Long.parseLong(
                    matcher.group(1)
            );

        } catch (
                NumberFormatException e
        ) {

            return -1;
        }
    }

    private String formatarAtivo(
            String ativo
    ) {

        if (
                ativo == null
                        ||
                        ativo.isBlank()
        ) {

            return "---";
        }

        String texto =
                ativo.trim();

        boolean otc =
                texto
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .endsWith(
                                "_otc"
                        );

        if (otc) {

            texto =
                    texto.substring(
                            0,
                            texto.length() - 4
                    );
        }

        if (
                texto.length() == 6
                        &&
                        texto
                                .chars()
                                .allMatch(
                                        Character::isLetter
                                )
        ) {

            texto =
                    texto.substring(
                            0,
                            3
                    )
                            + "/"
                            + texto.substring(
                            3
                    );
        }

        return otc
                ? texto + " OTC"
                : texto;
    }

    public static void abrir() {

        SwingUtilities.invokeLater(
                () ->
                        new SignalReceiverWindow()
                                .setVisible(
                                        true
                                )
        );
    }
}

