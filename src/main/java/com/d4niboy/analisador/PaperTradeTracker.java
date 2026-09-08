package com.d4niboy.analisador;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class PaperTradeTracker {

    private static final long[] EXPIRACOES = {
            15_000,
            30_000,
            60_000,
            120_000,
            300_000
    };

    private static final long MAX_GAP_MS = 5_000;

    private static final double BANCA_INICIAL = 100.00;
    private static final double ENTRADA_MINIMA = 1.00;

    private final Map<Long, Double> bancas = new HashMap<>();
    private final Map<Long, Double> reservado = new HashMap<>();
    private final Map<Long, Estatistica> estatisticas = new HashMap<>();

    private final List<PaperTrade> abertas = new ArrayList<>();
    private final AtomicLong contador = new AtomicLong();
    private final Map<String, Long> ultimaQuote = new HashMap<>();

    private final DateTimeFormatter horario =
            DateTimeFormatter
                    .ofPattern("HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    private final Locale localeBrasil =
            Locale.forLanguageTag("pt-BR");

    public PaperTradeTracker() {

        for (long expiracao : EXPIRACOES) {
            bancas.put(expiracao, BANCA_INICIAL);
            reservado.put(expiracao, 0.0);
            estatisticas.put(expiracao, new Estatistica());
        }

        mostrarBancaInicial();
    }

    private void mostrarBancaInicial() {

        System.out.println();
        System.out.println("==============================================");
        System.out.println("              BANCA VIRTUAL");
        System.out.println("==============================================");
        System.out.println("Banca inicial por expiração: " + moeda(BANCA_INICIAL));
        System.out.println("Gestão: tamanho da entrada depende SOMENTE do sinal atual.");
        System.out.println("Sem martingale e sem recuperação de perdas.");
        System.out.println();

        for (long expiracao : EXPIRACOES) {
            System.out.println(
                    String.format(
                            "%-6s -> %s",
                            nomeExpiracao(expiracao),
                            moeda(bancas.get(expiracao))
                    )
            );
        }

        System.out.println("==============================================");
        System.out.println();
    }

    public synchronized void abrir(
            String ativo,
            String direcao,
            double preco,
            double confianca,
            double payout
    ) {

        if (ativo == null || direcao == null) {
            return;
        }

        if (!direcao.equals("PARA CIMA")
                && !direcao.equals("PARA BAIXO")) {
            return;
        }

        if (Double.isNaN(payout)
                || Double.isInfinite(payout)
                || payout <= 0
                || payout > 100) {

            System.out.println();
            System.out.println("⚠ OPERAÇÃO VIRTUAL NÃO ABERTA");
            System.out.println("Payout inválido: " + payout);
            System.out.println();
            return;
        }

        double percentualRisco = percentualRisco(confianca);

        if (percentualRisco <= 0) {
            System.out.println();
            System.out.printf(
                    Locale.US,
                    "⚠ Sinal ignorado pela gestão: confiança heurística %.1f%% abaixo do mínimo de risco.%n",
                    confianca
            );
            System.out.println();
            return;
        }

        long agora = System.currentTimeMillis();
        long grupo = contador.incrementAndGet();

        System.out.println();
        System.out.println("======================================================");
        System.out.println("        ENTRADA AUTOMÁTICA DE TESTE #" + grupo);
        System.out.println("======================================================");
        System.out.println("Ativo: " + formatarAtivo(ativo));
        System.out.println("Direção: " + direcao);
        System.out.printf(Locale.US, "Preço de entrada: %.5f%n", preco);
        System.out.println("Horário: " + horario.format(Instant.ofEpochMilli(agora)));
        System.out.printf(Locale.US, "Confiança heurística: %.1f%%%n", confianca);
        System.out.printf(Locale.US, "Payout da entrada: %.0f%%%n", payout);
        System.out.printf(Locale.US, "Risco calculado pelo sinal atual: %.2f%%%n", percentualRisco);
        System.out.println();
        System.out.println("Expirações sendo testadas:");

        boolean abriuAlguma = false;

        for (long expiracao : EXPIRACOES) {

            double banca = bancas.getOrDefault(expiracao, BANCA_INICIAL);
            double jaReservado = reservado.getOrDefault(expiracao, 0.0);
            double disponivel = Math.max(0.0, banca - jaReservado);

            double valorEntrada = banca * (percentualRisco / 100.0);
            valorEntrada = Math.max(ENTRADA_MINIMA, valorEntrada);
            valorEntrada = Math.min(valorEntrada, disponivel);

            if (disponivel < ENTRADA_MINIMA || valorEntrada < ENTRADA_MINIMA) {

                System.out.println(
                        "  " + nomeExpiracao(expiracao)
                                + " -> SEM SALDO DISPONÍVEL"
                                + " | banca: " + moeda(banca)
                                + " | reservado: " + moeda(jaReservado)
                );

                continue;
            }

            PaperTrade trade =
                    new PaperTrade(
                            grupo,
                            ativo,
                            direcao,
                            preco,
                            agora,
                            expiracao,
                            confianca,
                            payout,
                            valorEntrada,
                            percentualRisco
                    );

            abertas.add(trade);
            reservado.put(expiracao, jaReservado + valorEntrada);
            abriuAlguma = true;

            double lucroPossivel = valorEntrada * (payout / 100.0);

            System.out.println(
                    "  " + nomeExpiracao(expiracao)
                            + " -> aguardando"
                            + " | banca: " + moeda(banca)
                            + " | entrada: " + moeda(valorEntrada)
                            + " | lucro se ACERTO: " + moeda(lucroPossivel)
            );
        }

        if (!abriuAlguma) {
            System.out.println("Nenhuma expiração conseguiu abrir operação virtual.");
        }

        System.out.println("======================================================");
        System.out.println();
    }

    public synchronized void onQuote(
            String ativo,
            double preco
    ) {

        if (ativo == null) {
            return;
        }

        long agora = System.currentTimeMillis();

        Long anterior = ultimaQuote.put(
                ativo.toLowerCase(Locale.ROOT),
                agora
        );

        if (anterior != null) {
            long gap = agora - anterior;

            if (gap > MAX_GAP_MS) {
                invalidarOperacoesDoAtivo(ativo, gap);
            }
        }

        Iterator<PaperTrade> it = abertas.iterator();

        while (it.hasNext()) {

            PaperTrade trade = it.next();

            if (!trade.getAtivo().equalsIgnoreCase(ativo)) {
                continue;
            }

            if (trade.isInvalida()) {
                liberarReserva(trade);
                it.remove();
                continue;
            }

            if (agora < trade.getHorarioExpiracao()) {
                continue;
            }

            finalizar(trade, preco, agora);
            it.remove();
        }
    }

    private void finalizar(
            PaperTrade trade,
            double precoSaida,
            long horarioSaida
    ) {

        trade.setFinalizada(true);
        liberarReserva(trade);

        String resultado;

        int comparacao = Double.compare(
                precoSaida,
                trade.getPrecoEntrada()
        );

        if (comparacao == 0) {
            resultado = "EMPATE";

        } else if (trade.getDirecao().equals("PARA CIMA")) {
            resultado = comparacao > 0 ? "ACERTO" : "ERRO";

        } else {
            resultado = comparacao < 0 ? "ACERTO" : "ERRO";
        }

        long expiracao = trade.getExpiracaoMs();
        Estatistica estatistica = estatisticas.get(expiracao);

        double bancaAntes = bancas.getOrDefault(expiracao, BANCA_INICIAL);
        double bancaDepois = bancaAntes;
        double lucroPrejuizo = 0.0;

        if (resultado.equals("ACERTO")) {

            estatistica.acertos++;

            lucroPrejuizo =
                    trade.getValorEntrada()
                            *
                            (trade.getPayout() / 100.0);

            bancaDepois = bancaAntes + lucroPrejuizo;
            estatistica.lucroTotal += lucroPrejuizo;

        } else if (resultado.equals("ERRO")) {

            estatistica.erros++;

            lucroPrejuizo = -trade.getValorEntrada();
            bancaDepois = bancaAntes - trade.getValorEntrada();
            estatistica.lucroTotal += lucroPrejuizo;

        } else {

            estatistica.empates++;
        }

        bancaDepois = Math.max(0.0, bancaDepois);
        bancas.put(expiracao, bancaDepois);

        System.out.println();
        System.out.println("--------------- RESULTADO ----------------");
        System.out.println(
                "Entrada #" + trade.getId()
                        + " | " + formatarAtivo(trade.getAtivo())
        );
        System.out.println("Expiração: " + nomeExpiracao(expiracao));
        System.out.println("Direção: " + trade.getDirecao());
        System.out.printf(Locale.US, "Entrada: %.5f%n", trade.getPrecoEntrada());
        System.out.printf(Locale.US, "Saída:   %.5f%n", precoSaida);
        System.out.println(
                "Fechamento observado: "
                        + horario.format(Instant.ofEpochMilli(horarioSaida))
        );
        System.out.println("RESULTADO: " + simbolo(resultado) + " " + resultado);
        System.out.printf(Locale.US, "Confiança heurística: %.1f%%%n", trade.getConfianca());
        System.out.printf(Locale.US, "Risco usado: %.2f%%%n", trade.getPercentualRisco());
        System.out.printf(Locale.US, "Payout da entrada: %.0f%%%n", trade.getPayout());
        System.out.println("Valor da operação: " + moeda(trade.getValorEntrada()));

        if (resultado.equals("ACERTO")) {
            System.out.println("Lucro: +" + moeda(lucroPrejuizo));

        } else if (resultado.equals("ERRO")) {
            System.out.println("Prejuízo: -" + moeda(Math.abs(lucroPrejuizo)));

        } else {
            System.out.println("Resultado financeiro: " + moeda(0));
        }

        System.out.println();
        System.out.println("BANCA " + nomeExpiracao(expiracao));
        System.out.println("Antes:  " + moeda(bancaAntes));
        System.out.println("Depois: " + moeda(bancaDepois));

        double variacao = bancaDepois - BANCA_INICIAL;

        if (variacao > 0) {
            System.out.println("Resultado acumulado: +" + moeda(variacao));
        } else if (variacao < 0) {
            System.out.println("Resultado acumulado: -" + moeda(Math.abs(variacao)));
        } else {
            System.out.println("Resultado acumulado: " + moeda(0));
        }

        System.out.println("------------------------------------------");

        mostrarEstatisticas();
    }

    private void liberarReserva(PaperTrade trade) {

        long expiracao = trade.getExpiracaoMs();
        double atual = reservado.getOrDefault(expiracao, 0.0);
        double novo = Math.max(0.0, atual - trade.getValorEntrada());
        reservado.put(expiracao, novo);
    }

    private void invalidarOperacoesDoAtivo(
            String ativo,
            long gap
    ) {

        boolean encontrou = false;

        Iterator<PaperTrade> it = abertas.iterator();

        while (it.hasNext()) {

            PaperTrade trade = it.next();

            if (!trade.getAtivo().equalsIgnoreCase(ativo)) {
                continue;
            }

            trade.setInvalida(true);
            liberarReserva(trade);
            it.remove();
            encontrou = true;
        }

        if (encontrou) {
            System.out.println();
            System.out.println("⚠ OPERAÇÕES DE TESTE INVALIDADAS");
            System.out.println("Ativo: " + formatarAtivo(ativo));
            System.out.printf(
                    Locale.US,
                    "Feed ficou %.2f segundos sem cotações.%n",
                    gap / 1000.0
            );
            System.out.println("Essas operações NÃO entram nas estatísticas.");
            System.out.println("Nenhuma banca foi alterada.");
            System.out.println();
        }
    }

    public synchronized void cancelarAtivo(
            String ativo
    ) {

        if (ativo == null) {
            return;
        }

        int removidas = 0;

        Iterator<PaperTrade> it = abertas.iterator();

        while (it.hasNext()) {

            PaperTrade trade = it.next();

            if (trade.getAtivo().equalsIgnoreCase(ativo)) {
                liberarReserva(trade);
                it.remove();
                removidas++;
            }
        }

        if (removidas > 0) {
            System.out.println(
                    "Operações pendentes de "
                            + formatarAtivo(ativo)
                            + " canceladas por troca de ativo."
            );
            System.out.println("Nenhuma banca foi alterada.");
        }
    }

    private void mostrarEstatisticas() {

        System.out.println();
        System.out.println("======================= ESTATÍSTICAS =======================");
        System.out.printf(
                "%-7s %-7s %-8s %-7s %-8s %-9s %-12s %-12s%n",
                "Tempo",
                "Total",
                "Acertos",
                "Erros",
                "Empates",
                "Taxa",
                "Banca",
                "Reservado"
        );

        long melhorTempo = 0;
        double melhorTaxa = -1;

        for (long expiracao : EXPIRACOES) {

            Estatistica e = estatisticas.get(expiracao);

            int decididas = e.acertos + e.erros;
            int total = decididas + e.empates;

            double taxa =
                    decididas == 0
                            ? 0
                            : (e.acertos * 100.0) / decididas;

            double banca = bancas.getOrDefault(expiracao, BANCA_INICIAL);
            double valorReservado = reservado.getOrDefault(expiracao, 0.0);

            System.out.printf(
                    localeBrasil,
                    "%-7s %-7d %-8d %-7d %-8d %6.2f%%   %-12s %-12s%n",
                    nomeExpiracao(expiracao),
                    total,
                    e.acertos,
                    e.erros,
                    e.empates,
                    taxa,
                    moeda(banca),
                    moeda(valorReservado)
            );

            if (decididas > 0 && taxa > melhorTaxa) {
                melhorTaxa = taxa;
                melhorTempo = expiracao;
            }
        }

        if (melhorTempo > 0) {
            System.out.println();
            System.out.printf(
                    localeBrasil,
                    "Melhor taxa até agora: %s (%.2f%%)%n",
                    nomeExpiracao(melhorTempo),
                    melhorTaxa
            );
            System.out.println(
                    "Banca dessa expiração: "
                            + moeda(bancas.get(melhorTempo))
            );
        }

        System.out.println();
        mostrarResumoFinanceiro();
        System.out.println("============================================================");
        System.out.println();
    }

    private void mostrarResumoFinanceiro() {

        System.out.println("BANCAS VIRTUAIS:");

        for (long expiracao : EXPIRACOES) {

            double banca = bancas.getOrDefault(expiracao, BANCA_INICIAL);
            double valorReservado = reservado.getOrDefault(expiracao, 0.0);
            double resultado = banca - BANCA_INICIAL;

            String sinal = resultado > 0 ? "+" : "";

            System.out.println(
                    "  "
                            + nomeExpiracao(expiracao)
                            + ": " + moeda(banca)
                            + " | " + sinal + moeda(resultado)
                            + " | reservado: " + moeda(valorReservado)
            );
        }
    }

    /*
     * Gestão dinâmica baseada SOMENTE na força do sinal atual.
     *
     * Não aumenta depois de derrota.
     * Não usa martingale.
     * Não tenta recuperar prejuízo.
     *
     * A confiança continua sendo HEURÍSTICA, não probabilidade real.
     *
     * Faixas:
     * < 60%       -> não abre pela gestão
     * 60 a <70%   -> 1% da banca
     * 70 a <80%   -> 2% da banca
     * 80 a <90%   -> 5% da banca
     * 90 a <95%   -> 10% da banca
     * 95 a <100%  -> 20% da banca
     * 100%        -> 50% da banca
     *
     * Hoje o Analyzer15s limita a confiança em 90%, então na versão
     * atual o risco máximo efetivamente usado será 10%.
     */
    private double percentualRisco(double confianca) {

        if (confianca < 60.0) {
            return 0.0;
        }

        if (confianca < 70.0) {
            return 1.0;
        }

        if (confianca < 80.0) {
            return 2.0;
        }

        if (confianca < 90.0) {
            return 5.0;
        }

        if (confianca < 95.0) {
            return 10.0;
        }

        if (confianca < 100.0) {
            return 20.0;
        }

        return 50.0;
    }

    private String simbolo(String resultado) {

        return switch (resultado) {
            case "ACERTO" -> "✓";
            case "ERRO" -> "✗";
            default -> "=";
        };
    }

    private String nomeExpiracao(long ms) {

        if (ms < 60_000) {
            return (ms / 1000) + "s";
        }

        return (ms / 60_000) + "min";
    }

    private String formatarAtivo(String ativo) {

        if (ativo == null) {
            return "DESCONHECIDO";
        }

        String s = ativo.trim();

        boolean otc =
                s.toLowerCase(Locale.ROOT)
                        .endsWith("_otc");

        if (otc) {
            s = s.substring(0, s.length() - 4);
        }

        if (s.length() == 6
                && s.chars().allMatch(Character::isLetter)) {

            s = s.substring(0, 3)
                    + "/"
                    + s.substring(3);
        }

        return otc ? s + " OTC" : s;
    }

    private String moeda(double valor) {

        return String.format(
                localeBrasil,
                "R$ %.2f",
                valor
        );
    }

    private static class Estatistica {
        private int acertos;
        private int erros;
        private int empates;
        private double lucroTotal;
    }
}
