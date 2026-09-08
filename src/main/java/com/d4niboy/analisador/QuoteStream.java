package com.d4niboy.analisador;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuoteStream {
    private static final String NUMERO = "[-+]?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?";
    private static final Pattern QUOTE_PATTERN = Pattern.compile(
        "\\[\\s*\"([^\"]+)\"\\s*,\\s*(" + NUMERO + ")\\s*,\\s*(" + NUMERO + ")\\s*,\\s*(-?[0-9]+)\\s*\\]"
    );

    public List<Quote> parseBinaryMessage(byte[] dados) {
        if (dados == null || dados.length == 0) return new ArrayList<>();
        int inicio = dados[0] == 0x04 ? 1 : 0;
        return parseText(new String(dados, inicio, dados.length - inicio, StandardCharsets.UTF_8).trim());
    }

    public List<Quote> parseText(String texto) {
        List<Quote> quotes = new ArrayList<>();
        if (texto == null || texto.isBlank()) return quotes;
        Matcher m = QUOTE_PATTERN.matcher(texto);
        while (m.find()) {
            try {
                quotes.add(new Quote(m.group(1), Double.parseDouble(m.group(2)),
                        Double.parseDouble(m.group(3)), Integer.parseInt(m.group(4))));
            } catch (NumberFormatException ignored) {}
        }
        return quotes;
    }

    public record Quote(String ativo, double timestamp, double preco, int campoExtra) {}
}
