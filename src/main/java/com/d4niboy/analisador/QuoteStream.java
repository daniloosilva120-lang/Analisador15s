package com.d4niboy.analisador;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QuoteStream {

    private static final String NUMERO =
            "[-+]?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?";

    private static final Pattern QUOTE_PATTERN =
            Pattern.compile(
                    "\\[\\s*\"([^\"]+)\"\\s*,\\s*("
                            + NUMERO
                            + ")\\s*,\\s*("
                            + NUMERO
                            + ")\\s*,\\s*(-?[0-9]+)\\s*\\]"
            );

    public List<Quote> parseBinaryMessage(
            byte[] dados
    ) {

        if (dados == null || dados.length == 0) {
            return List.of();
        }

        int inicio =
                dados[0] == 0x04
                        ? 1
                        : 0;

        String texto =
                new String(
                        dados,
                        inicio,
                        dados.length - inicio,
                        StandardCharsets.UTF_8
                ).trim();

        return parseText(texto);
    }

    public List<Quote> parseText(
            String texto
    ) {

        if (texto == null || texto.isBlank()) {
            return List.of();
        }

        List<Quote> quotes =
                new ArrayList<>();

        Matcher matcher =
                QUOTE_PATTERN.matcher(texto);

        while (matcher.find()) {

            try {

                quotes.add(
                        new Quote(
                                matcher.group(1),
                                Double.parseDouble(
                                        matcher.group(2)
                                ),
                                Double.parseDouble(
                                        matcher.group(3)
                                ),
                                Integer.parseInt(
                                        matcher.group(4)
                                )
                        )
                );

            } catch (NumberFormatException ignored) {
            }
        }

        return quotes;
    }

    public record Quote(
            String ativo,
            double timestamp,
            double preco,
            int campoExtra
    ) {
    }
}
