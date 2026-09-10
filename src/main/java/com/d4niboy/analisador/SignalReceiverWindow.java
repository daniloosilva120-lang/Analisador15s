package com.d4niboy.analisador;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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

    // --- NOVA VARIÁVEL DO ROBÔ ---
    private static volatile boolean autoClickAtivado = false;
    private final JCheckBox autoClickCheckBox = new JCheckBox("🤖 ATIVAR ROBÔ (Auto-Click)");

    private final JLabel statusLabel = new JLabel("Aguardando sinal...", SwingConstants.CENTER);
    private final JLabel ativoLabel = new JLabel("---", SwingConstants.CENTER);
    private final JLabel direcaoLabel = new JLabel("---", SwingConstants.CENTER);
    private final JLabel confiancaLabel = new JLabel("Confiança: ---", SwingConstants.CENTER);
    private final JLabel payoutLabel = new JLabel("Payout: ---", SwingConstants.CENTER);
    private final JLabel horarioLabel = new JLabel("Último sinal: ---", SwingConstants.CENTER);
    private final JButton confirmarButton = new JButton("CONFIRMAR");
    private final JButton ignorarButton = new JButton("IGNORAR");

    private volatile long ultimoTimestamp = -1;
    private volatile String ativoAtual = null;
    private volatile String direcaoAtual = null;
    private volatile double confiancaAtual = 0;
    private volatile double payoutAtual = 0;
    private volatile long timestampAtual = -1;

    private final PlatformButtonLocator locator = new PlatformButtonLocator();

    public SignalReceiverWindow() {
        super("Analisador15s - Receptor de Sinais");
        configurarJanela();

        try {
            locator.conectar();
            locator.removerDestaques();
        } catch (Exception e) {
            System.out.println("⚠ Não foi possível limpar destaque anterior: " + e.getMessage());
        }
        iniciarMonitoramento();
    }

    private void configurarJanela() {
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(500, 500); // Janela levemente maior para caber o novo botão
        setLocationRelativeTo(null);
        setAlwaysOnTop(true);

        JPanel painel = new JPanel();
        painel.setLayout(new BoxLayout(painel, BoxLayout.Y_AXIS));
        painel.setBorder(new EmptyBorder(25, 30, 25, 30));

        JLabel titulo = new JLabel("ANALISADOR 15s", SwingConstants.CENTER);
        titulo.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 25));
        titulo.setAlignmentX(Component.CENTER_ALIGNMENT);

        // --- CONFIGURAÇÃO DO BOTÃO LIGA/DESLIGA ---
        autoClickCheckBox.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        autoClickCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);
        autoClickCheckBox.setForeground(new Color(190, 0, 0)); // Vermelho quando desligado
        autoClickCheckBox.setFocusPainted(false);
        autoClickCheckBox.addActionListener(e -> {
            autoClickAtivado = autoClickCheckBox.isSelected();
            if (autoClickAtivado) {
                autoClickCheckBox.setForeground(new Color(0, 140, 0)); // Verde
                statusLabel.setText("🤖 AUTO-CLICK LIGADO");
            } else {
                autoClickCheckBox.setForeground(new Color(190, 0, 0)); // Vermelho
                statusLabel.setText("Aguardando sinal... (Robô Pausado)");
            }
        });

        statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        ativoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
        ativoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        direcaoLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 27));
        direcaoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        confiancaLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        confiancaLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        payoutLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
        payoutLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        horarioLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        confirmarButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        confirmarButton.setEnabled(false);
        confirmarButton.addActionListener(e -> confirmarSinal());

        ignorarButton.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        ignorarButton.setEnabled(false);
        ignorarButton.addActionListener(e -> ignorarSinal());

        JPanel painelBotoes = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        painelBotoes.add(confirmarButton);
        painelBotoes.add(ignorarButton);

        painel.add(titulo);
        painel.add(Box.createVerticalStrut(15));
        painel.add(autoClickCheckBox); // <-- BOTÃO ADICIONADO AQUI
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
        painel.add(Box.createVerticalStrut(25));
        painel.add(painelBotoes);

        setContentPane(painel);
    }

    private void confirmarSinal() {
        if (direcaoAtual == null || direcaoAtual.isBlank() || timestampAtual <= 0) return;
        statusLabel.setText("✓ CONFIRMADO - FINALIZE NO NAVEGADOR");
        confirmarButton.setEnabled(false);
        ignorarButton.setEnabled(false);

        try {
            locator.removerDestaques();
            boolean encontrado = locator.destacarBotao(direcaoAtual);
            if (!encontrado) statusLabel.setText("⚠ CONFIRMADO - BOTÃO NÃO LOCALIZADO");
        } catch (Exception e) {
            System.out.println("⚠ Não foi possível destacar o botão: " + e.getMessage());
        }

        trazerChromeParaFrente();
        System.out.println("\n========================================");
        System.out.println("SINAL CONFIRMADO MANUALMENTE");
        System.out.println("Ativo: " + ativoAtual);
        System.out.println("Direção: " + direcaoAtual);
        System.out.printf(Locale.US, "Confiança: %.2f%%%n", confiancaAtual);
        System.out.printf(Locale.US, "Payout: %.0f%%%n", payoutAtual);
        System.out.println("O botão correto foi apenas destacado.");
        System.out.println("========================================\n");
    }

    private void trazerChromeParaFrente() {
        try {
            String comando = "$wshell = New-Object -ComObject WScript.Shell; " +
                    "$p = Get-Process chrome -ErrorAction SilentlyContinue | Where-Object {$_.MainWindowTitle -ne ''} | Select-Object -First 1; " +
                    "if ($p) { $wshell.AppActivate($p.Id) | Out-Null }";
            new ProcessBuilder("powershell.exe", "-NoProfile", "-WindowStyle", "Hidden", "-Command", comando).start();
        } catch (Exception e) {
            System.out.println("⚠ Não foi possível trazer o Chrome para frente: " + e.getMessage());
        }
    }

    private void ignorarSinal() {
        statusLabel.setText("Sinal ignorado. Aguardando próximo sinal...");
        direcaoLabel.setText("---");
        direcaoLabel.setForeground(Color.BLACK);
        confirmarButton.setEnabled(false);
        ignorarButton.setEnabled(false);
        try { locator.removerDestaques(); } catch (Exception ignored) {}
        System.out.println("\nSinal ignorado pelo usuário.\n");
    }

    private void iniciarMonitoramento() {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    consultarSinal();
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Servidor indisponível..."));
                    try { Thread.sleep(1500); } catch (InterruptedException ex) {
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
            while ((linha = reader.readLine()) != null) json.append(linha);
        } finally {
            conexao.disconnect();
        }

        String resposta = json.toString();
        if (resposta.contains("\"AGUARDANDO_SINAL\"")) {
            if (ultimoTimestamp <= 0) {
                SwingUtilities.invokeLater(() -> statusLabel.setText(autoClickAtivado ? "🤖 AUTO-CLICK LIGADO" : "Aguardando sinal..."));
            }
            return;
        }

        long timestamp = extrairLong(resposta, "timestamp");
        if (timestamp <= 0 || timestamp == ultimoTimestamp) return;

        ultimoTimestamp = timestamp;
        ativoAtual = extrairTexto(resposta, "ativo");
        direcaoAtual = extrairTexto(resposta, "direcao");
        confiancaAtual = extrairDouble(resposta, "confianca");
        payoutAtual = extrairDouble(resposta, "payout");
        timestampAtual = timestamp;

        mostrarNovoSinal(ativoAtual, direcaoAtual, confiancaAtual, payoutAtual, timestamp);
    }

    private void mostrarNovoSinal(String ativo, String direcao, double confianca, double payout, long timestamp) {
        SwingUtilities.invokeLater(() -> {
            try { locator.removerDestaques(); } catch (Exception ignored) {}

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

            // --- LÓGICA DE EXIBIÇÃO BASEADA NO AUTO-CLICK ---
            if (autoClickAtivado) {
                statusLabel.setText("✅ SINAL EXECUTADO PELO ROBÔ!");
                confirmarButton.setEnabled(false);
                ignorarButton.setEnabled(false);
            } else {
                statusLabel.setText("NOVO SINAL RECEBIDO");
                confirmarButton.setEnabled(true);
                ignorarButton.setEnabled(true);
            }

            Toolkit.getDefaultToolkit().beep();
            if (!isVisible()) setVisible(true);
            toFront();
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
        if (otc) s = s.substring(0, s.length() - 4);
        if (s.length() == 6 && s.chars().allMatch(Character::isLetter)) s = s.substring(0, 3) + "/" + s.substring(3);
        return otc ? s + " OTC" : s;
    }

    public static void abrir() {
        SwingUtilities.invokeLater(() -> new SignalReceiverWindow().setVisible(true));
    }

    // --- MÉTODO PARA O REALTIMEFEED VERIFICAR SE PODE CLICAR ---
    public static boolean isAutoClickAtivo() {
        return autoClickAtivado;
    }
}
