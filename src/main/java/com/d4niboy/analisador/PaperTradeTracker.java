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
            15_000
    };

    private static final long MAX_GAP_MS = 5_000;

    private final Map<Long, Estatistica> estatisticas = new HashMap<>();
    private final List<PaperTrade> abertas = new ArrayList<>();
    private final Map<String, Long> ultimaQuote = new HashMap<>();
    private final AtomicLong contador = new AtomicLong();

    private final DateTimeFormatter horario =
            DateTimeFormatter
                    .ofPattern("HH:mm:ss.SSS")
                    .withZone(ZoneId.systemDefault());

    public PaperTradeTracker() {

        for (long expiracao : EXPIRACOES) {
            estatisticas.put(
                    expiracao,
                    new Estatistica()
            );
        }
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
            System.out.println("⚠ TESTE NÃO ABERTO");
            System.out.println("Payout inválido: " + payout);
            System.out.println();

            return;
        }

        long agora = System.currentTimeMillis();
        long grupo = contador.incrementAndGet();

        for (long expiracao : EXPIRACOES) {

            PaperTrade trade =
                    new PaperTrade(
                            grupo,
                            ativo,
                            direcao,
                            preco,
                            agora,
                            expiracao,
                            confianca,
                            payout
                    );

            abertas.add(trade);
        }
    }

    public synchronized void onQuote(
            String ativo,
            double preco
    ) {

        if (ativo == null) {
            return;
        }

        long agora =
                System.currentTimeMillis();

        String chaveAtivo =
                ativo.toLowerCase(Locale.ROOT);

        Long anterior =
                ultimaQuote.put(
                        chaveAtivo,
                        agora
                );

        if (anterior != null) {

            long gap =
                    agora - anterior;

            if (gap > MAX_GAP_MS) {
                invalidarOperacoesDoAtivo(
                        ativo,
                        gap
                );
            }
        }

        Iterator<PaperTrade> it =
                abertas.iterator();

        while (it.hasNext()) {

            PaperTrade trade =
                    it.next();

            if (!trade
                    .getAtivo()
                    .equalsIgnoreCase(ativo)) {

                continue;
            }

            if (trade.isInvalida()) {
                it.remove();
                continue;
            }

            if (agora < trade.getHorarioExpiracao()) {
                continue;
            }

            finalizar(
                    trade,
                    preco,
                    agora
            );

            it.remove();
        }
    }

    private void finalizar(
            PaperTrade trade,
            double precoSaida,
            long horarioSaida
    ) {

        String resultado;

        int comparacao =
                Double.compare(
                        precoSaida,
                        trade.getPrecoEntrada()
                );

        if (comparacao == 0) {

            resultado = "EMPATE";

        } else if (
                trade.getDirecao()
                        .equals("PARA CIMA")
        ) {

            resultado =
                    comparacao > 0
                            ? "ACERTO"
                            : "ERRO";

        } else {

            resultado =
                    comparacao < 0
                            ? "ACERTO"
                            : "ERRO";
        }

        long expiracao =
                trade.getExpiracaoMs();

        Estatistica estatistica =
                estatisticas.get(expiracao);

        if (estatistica == null) {
            return;
        }

        switch (resultado) {

            case "ACERTO" ->
                    estatistica.acertos++;

            case "ERRO" ->
                    estatistica.erros++;

            default ->
                    estatistica.empates++;
        }

        System.out.println();
        System.out.println(
                "--------------- RESULTADO TESTE ----------------"
        );

        System.out.println(
                "Entrada #"
                        + trade.getId()
                        + " | "
                        + formatarAtivo(
                        trade.getAtivo()
                )
        );

        System.out.println(
                "Expiração: "
                        + nomeExpiracao(expiracao)
        );

        System.out.println(
                "Direção: "
                        + trade.getDirecao()
        );

        System.out.printf(
                Locale.US,
                "Entrada: %.5f%n",
                trade.getPrecoEntrada()
        );

        System.out.printf(
                Locale.US,
                "Saída:   %.5f%n",
                precoSaida
        );

        System.out.println(
                "Fechamento observado: "
                        + horario.format(
                        Instant.ofEpochMilli(
                                horarioSaida
                        )
                )
        );

        System.out.println(
                "RESULTADO: "
                        + simbolo(resultado)
                        + " "
                        + resultado
        );

        System.out.printf(
                Locale.US,
                "Confiança heurística: %.1f%%%n",
                trade.getConfianca()
        );

        System.out.printf(
                Locale.US,
                "Payout: %.0f%%%n",
                trade.getPayout()
        );

        System.out.println(
                "------------------------------------------------"
        );

        mostrarEstatisticas();
    }

    private void invalidarOperacoesDoAtivo(
            String ativo,
            long gap
    ) {

        boolean encontrou = false;

        Iterator<PaperTrade> it =
                abertas.iterator();

        while (it.hasNext()) {

            PaperTrade trade =
                    it.next();

            if (!trade
                    .getAtivo()
                    .equalsIgnoreCase(ativo)) {

                continue;
            }

            trade.setInvalida(true);
            it.remove();

            encontrou = true;
        }

        if (encontrou) {

            System.out.println();
            System.out.println(
                    "⚠ TESTES INVALIDADOS"
            );

            System.out.println(
                    "Ativo: "
                            + formatarAtivo(ativo)
            );

            System.out.printf(
                    Locale.US,
                    "Feed ficou %.2f segundos sem cotações.%n",
                    gap / 1000.0
            );

            System.out.println(
                    "Esses testes não entram nas estatísticas."
            );

            System.out.println();
        }
    }

    public synchronized void cancelarAtivo(
            String ativo
    ) {

        if (ativo == null) {
            return;
        }

        Iterator<PaperTrade> it =
                abertas.iterator();

        while (it.hasNext()) {

            PaperTrade trade =
                    it.next();

            if (trade
                    .getAtivo()
                    .equalsIgnoreCase(ativo)) {

                it.remove();
            }
        }

        ultimaQuote.remove(
                ativo.toLowerCase(Locale.ROOT)
        );
    }

    private void mostrarEstatisticas() {

        System.out.println();
        System.out.println(
                "================ ESTATÍSTICAS ================"
        );

        System.out.printf(
                "%-7s %-7s %-8s %-7s %-8s %-9s%n",
                "Tempo",
                "Total",
                "Acertos",
                "Erros",
                "Empates",
                "Taxa"
        );

        for (long expiracao : EXPIRACOES) {

            Estatistica e =
                    estatisticas.get(expiracao);

            if (e == null) {
                continue;
            }

            int decididas =
                    e.acertos
                            +
                            e.erros;

            int total =
                    decididas
                            +
                            e.empates;

            double taxa =
                    decididas == 0
                            ? 0
                            : (e.acertos * 100.0)
                            / decididas;

            System.out.printf(
                    Locale.US,
                    "%-7s %-7d %-8d %-7d %-8d %6.2f%%%n",
                    nomeExpiracao(expiracao),
                    total,
                    e.acertos,
                    e.erros,
                    e.empates,
                    taxa
            );

        }

        System.out.println(
                "=============================================="
        );

        System.out.println();
    }

    private String simbolo(
            String resultado
    ) {

        return switch (resultado) {

            case "ACERTO" -> "✓";
            case "ERRO" -> "✗";
            default -> "=";
        };
    }

    private String nomeExpiracao(
            long ms
    ) {

        if (ms < 60_000) {
            return (ms / 1000) + "s";
        }

        return (ms / 60_000) + "min";
    }

    private String formatarAtivo(
            String ativo
    ) {

        if (ativo == null) {
            return "DESCONHECIDO";
        }

        String s =
                ativo.trim();

        boolean otc =
                s.toLowerCase(Locale.ROOT)
                        .endsWith("_otc");

        if (otc) {

            s =
                    s.substring(
                            0,
                            s.length() - 4
                    );
        }

        if (s.length() == 6
                && s.chars()
                .allMatch(Character::isLetter)) {

            s =
                    s.substring(0, 3)
                            + "/"
                            + s.substring(3);
        }

        return otc
                ? s + " OTC"
                : s;
    }

    private static class Estatistica {

        private int acertos;
        private int erros;
        private int empates;
    }
}
