package com.d4niboy.analisador;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.InputEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SignalReceiverWindow extends JFrame {

    private static final String URL_SINAL = "http://127.0.0.1:8765/sinal";

    // ==========================================
    // CONFIGURAÇÃO DE COORDENADAS PARA O CLIQUE NA TELA
    // (Ajuste X e Y se necessário para o botão da sua corretora)
    // ==========================================
    private static final int COMPRA_X = 1200;
    private static final int COMPRA_Y = 450;

    private static final int VENDA_X = 1200;
    private static final int VENDA_Y = 520;

    private final JLabel statusLabel =
            new JLabel("OPERANDO 100% AUTOMÁTICO", SwingConstants.CENTER);

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
    private volatile String ativoAtual = null;
    private volatile String direcaoAtual = null;
    private volatile double confiancaAtual = 0;
    private volatile double payoutAtual = 0;
    private volatile long timestampAtual = -1;

    private Robot robot;

    public SignalReceiverWindow() {
        super("Analisador15s - Monitor Automático");
        try {
            this.robot = new Robot();
            this.robot.setAutoDelay(50);
        } catch (AWTException e) {
            e.printStackTrace();
        }
        configurarJanela();
        iniciarMonitoramento();
    }

    private void configurarJanela() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(450, 380);
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        JPanel painel = new JPanel();
        painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
        painel.setBorder(new EmptyBorder(25, 30, 25, 30));

        JLabel titulo = new JLabel("ANALISADOR 15s", SwingConstants.CENTER);
        titulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setForeground(new Color(0, 140, 0));

        ativoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        ativoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        direcaoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        direcaoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        confiancaLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        confiancaLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        payoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        payoutLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        horarioLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        painel.add(titulo);
        painel.add(Box.createVerticalStrut(15));
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

        setContentPane(painel);
    }

    private void iniciarMonitoramento() {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    consultarSinal();
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Servidor indisponível..."));
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "SignalReceiver");

        thread.setDaemon(true);
        thread.start();
    }

    private void consultarSinal() throws Exception {
        HttpURLConnection conexao = (HttpURLConnection) URI.create(URL_SINAL).toURL().openConnection();
        conexao.setRequestMethod("GET");
        conexao.setConnectTimeout(1000);
        conexao.setReadTimeout(1000);

        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conexao.getInputStream(), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = reader.readLine()) != null) {
                json.append(linha);
            }
        } finally {
            conexao.disconnect();
        }

        String resposta = json.toString();

        if (resposta.contains("\"AGUARDANDO_SINAL\"")) {
            return;
        }

        long timestamp = extrairLong(resposta, "timestamp");
        if (timestamp <= 0 || timestamp == ultimoTimestamp) {
            return;
        }

        ultimoTimestamp = timestamp;
        ativoAtual = extrairTexto(resposta, "ativo");
        direcaoAtual = extrairTexto(resposta, "direcao");
        confiancaAtual = extrairDouble(resposta, "confianca");
        payoutAtual = extrairDouble(resposta, "payout");
        timestampAtual = timestamp;

        executarCliqueAutomatico(direcaoAtual);
        mostrarNovoSinal(ativoAtual, direcaoAtual, confiancaAtual, payoutAtual, timestamp);
    }

    private void executarCliqueAutomatico(String direcao) {
        if (robot == null) return;

        try {
            if ("PARA CIMA".equalsIgnoreCase(direcao)) {
                robot.mouseMove(COMPRA_X, COMPRA_Y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            } else if ("PARA BAIXO".equalsIgnoreCase(direcao)) {
                robot.mouseMove(VENDA_X, VENDA_Y);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void mostrarNovoSinal(String ativo, String direcao, double confianca, double payout, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            ativoLabel.setText(formatarAtivo(ativo));
            direcaoLabel.setText(direcao);

            if ("PARA CIMA".equalsIgnoreCase(direcao)) {
                direcaoLabel.setForeground(new Color(0, 140, 0));
            } else if ("PARA BAIXO".equalsIgnoreCase(direcao)) {
                direcaoLabel.setForeground(new Color(190, 0, 0));
            } else {
                direcaoLabel.setForeground(Color.BLACK);
            }

            confiancaLabel.setText(String.format(Locale.US, "Confiança heurística: %.2f%%", confianca));
            payoutLabel.setText(String.format(Locale.US, "Payout: %.0f%%", payout));
            horarioLabel.setText("Timestamp: " + timestamp);
            statusLabel.setText("⚡ CLIQUE AUTOMÁTICO EXECUTADO");

            Toolkit.getDefaultToolkit().beep();

            if (!isVisible()) {
                setVisible(true);
            }
        });
    }

    private String extrairTexto(String json, String campo) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private double extrairDouble(String json, String campo) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
    }

    private long extrairLong(String json, String campo) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(campo) + "\"\\s*:\\s*(\\d+)").matcher(json);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : -1;
    }

    private String formatarAtivo(String ativo) {
        if (ativo == null || ativo.isBlank()) return "---";
        String s = ativo.trim();
        boolean otc = s.toLowerCase(Locale.ROOT).endsWith("_otc");
        if (otc) {
            s = s.substring(0, s.length() - 4);
        }
        if (s.length() == 6 && s.chars().allMatch(Character::isLetter)) {
            s = s.substring(0, 3) + "/" + s.substring(3);
        }
        return otc ? s + " OTC" : s;
    }

    public static void abrir() {
        SwingUtilities.invokeLater(() -> new SignalReceiverWindow().setVisible(true));
    }
}
