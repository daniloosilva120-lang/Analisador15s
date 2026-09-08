package com.d4niboy.analisador;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignalReceiverWindow extends JFrame {

    private static final String URL_SINAL =
            "http://127.0.0.1:8765/sinal";

    private final JLabel statusLabel =
            new JLabel("Aguardando sinal...", SwingConstants.CENTER);

    private final JLabel ativoLabel =
            new JLabel("---", SwingConstants.CENTER);

    private final JLabel direcaoLabel =
            new JLabel("---", SwingConstants.CENTER);

    private final JLabel confiancaLabel =
            new JLabel("Confiança: ---", SwingConstants.CENTER);

    private final JLabel payoutLabel =
            new JLabel("Payout: ---", SwingConstants.CENTER);

    private final JLabel horarioLabel =
            new JLabel("Último sinal: ---", SwingConstants.CENTER);

    private volatile long ultimoTimestamp = -1;

    public SignalReceiverWindow() {

        super("Analisador15s - Receptor de Sinais");

        configurarJanela();
        iniciarMonitoramento();
    }

    private void configurarJanela() {

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setSize(430, 390);

        setLocationRelativeTo(null);

        setAlwaysOnTop(true);

        JPanel painel = new JPanel();

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
                        25
                )
        );

        titulo.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        statusLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        16
                )
        );

        statusLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        ativoLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        30
                )
        );

        ativoLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        direcaoLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.BOLD,
                        27
                )
        );

        direcaoLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        confiancaLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        18
                )
        );

        confiancaLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        payoutLabel.setFont(
                new Font(
                        Font.SANS_SERIF,
                        Font.PLAIN,
                        18
                )
        );

        payoutLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        horarioLabel.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        JButton ignorar =
                new JButton("IGNORAR");

        ignorar.setAlignmentX(
                Component.CENTER_ALIGNMENT
        );

        ignorar.addActionListener(
                e -> {
                    statusLabel.setText(
                            "Aguardando próximo sinal..."
                    );

                    direcaoLabel.setText("---");
                }
        );

        painel.add(titulo);
        painel.add(Box.createVerticalStrut(20));

        painel.add(statusLabel);
        painel.add(Box.createVerticalStrut(20));

        painel.add(ativoLabel);
        painel.add(Box.createVerticalStrut(10));

        painel.add(direcaoLabel);
        painel.add(Box.createVerticalStrut(15));

        painel.add(confiancaLabel);
        painel.add(Box.createVerticalStrut(5));

        painel.add(payoutLabel);
        painel.add(Box.createVerticalStrut(10));

        painel.add(horarioLabel);
        painel.add(Box.createVerticalStrut(20));

        painel.add(ignorar);

        setContentPane(painel);
    }

    private void iniciarMonitoramento() {

        Thread thread =
                new Thread(
                        () -> {

                            while (!Thread.currentThread().isInterrupted()) {

                                try {

                                    consultarSinal();

                                    Thread.sleep(500);

                                } catch (InterruptedException e) {

                                    Thread.currentThread().interrupt();
                                    break;

                                } catch (Exception e) {

                                    SwingUtilities.invokeLater(
                                            () ->
                                                    statusLabel.setText(
                                                            "Servidor indisponível..."
                                                    )
                                    );

                                    try {

                                        Thread.sleep(1500);

                                    } catch (InterruptedException ex) {

                                        Thread.currentThread().interrupt();
                                        break;
                                    }
                                }
                            }
                        },
                        "SignalReceiver"
                );

        thread.setDaemon(true);
        thread.start();
    }

    private void consultarSinal() throws Exception {

        HttpURLConnection conexao =
                (HttpURLConnection)
                        URI.create(URL_SINAL)
                                .toURL()
                                .openConnection();

        conexao.setRequestMethod("GET");

        conexao.setConnectTimeout(1000);
        conexao.setReadTimeout(1000);

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

                json.append(linha);
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

            SwingUtilities.invokeLater(
                    () ->
                            statusLabel.setText(
                                    "Aguardando sinal..."
                            )
            );

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
                        timestamp == ultimoTimestamp
        ) {

            return;
        }

        ultimoTimestamp =
                timestamp;

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

        mostrarNovoSinal(
                ativo,
                direcao,
                confianca,
                payout,
                timestamp
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

                    statusLabel.setText(
                            "NOVO SINAL RECEBIDO"
                    );

                    ativoLabel.setText(
                            formatarAtivo(ativo)
                    );

                    direcaoLabel.setText(
                            direcao
                    );

                    confiancaLabel.setText(
                            String.format(
                                    "Confiança heurística: %.2f%%",
                                    confianca
                            )
                    );

                    payoutLabel.setText(
                            String.format(
                                    "Payout: %.0f%%",
                                    payout
                            )
                    );

                    horarioLabel.setText(
                            "Timestamp: "
                                    +
                                    timestamp
                    );

                    Toolkit
                            .getDefaultToolkit()
                            .beep();

                    if (!isVisible()) {

                        setVisible(true);
                    }

                    toFront();
                }
        );
    }

    private String extrairTexto(
            String json,
            String campo
    ) {

        Pattern pattern =
                Pattern.compile(
                        "\""
                                +
                                Pattern.quote(campo)
                                +
                                "\"\\s*:\\s*\"([^\"]*)\""
                );

        Matcher matcher =
                pattern.matcher(json);

        return matcher.find()
                ?
                matcher.group(1)
                :
                "";
    }

    private double extrairDouble(
            String json,
            String campo
    ) {

        Pattern pattern =
                Pattern.compile(
                        "\""
                                +
                                Pattern.quote(campo)
                                +
                                "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)"
                );

        Matcher matcher =
                pattern.matcher(json);

        if (!matcher.find()) {
            return 0;
        }

        return Double.parseDouble(
                matcher.group(1)
        );
    }

    private long extrairLong(
            String json,
            String campo
    ) {

        Pattern pattern =
                Pattern.compile(
                        "\""
                                +
                                Pattern.quote(campo)
                                +
                                "\"\\s*:\\s*(\\d+)"
                );

        Matcher matcher =
                pattern.matcher(json);

        if (!matcher.find()) {
            return -1;
        }

        return Long.parseLong(
                matcher.group(1)
        );
    }

    private String formatarAtivo(
            String ativo
    ) {

        if (ativo == null) {
            return "---";
        }

        String s =
                ativo.replace(
                        "_otc",
                        ""
                );

        if (s.length() == 6) {

            s =
                    s.substring(0, 3)
                            +
                            "/"
                            +
                            s.substring(3);
        }

        if (
                ativo.toLowerCase()
                        .endsWith("_otc")
        ) {

            s += " OTC";
        }

        return s;
    }

    public static void abrir() {

        SwingUtilities.invokeLater(
                () -> {

                    SignalReceiverWindow janela =
                            new SignalReceiverWindow();

                    janela.setVisible(true);
                }
        );
    }
}
