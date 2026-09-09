package com.d4niboy.analisador;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

public class PlatformButtonLocator {

    private WebDriver driver;

    public synchronized void conectar() {

        if (driver != null) {
            return;
        }

        ChromeOptions options =
                new ChromeOptions();

        /*
         * Usa o MESMO Chrome aberto pela
         * porta de depuração 9222.
         */
        options.setExperimentalOption(
                "debuggerAddress",
                "127.0.0.1:9222"
        );

        driver =
                new ChromeDriver(options);

        driver.manage()
                .timeouts()
                .implicitlyWait(
                        Duration.ofSeconds(1)
                );

        System.out.println();
        System.out.println(
                "Selenium conectado ao Chrome."
        );
    }


    /*
     * ============================================================
     * DESTACA O BOTÃO DO SINAL
     * ============================================================
     *
     * IMPORTANTE:
     *
     * Primeiro remove qualquer destaque criado
     * anteriormente pelo analisador.
     *
     * Depois destaca SOMENTE o botão correspondente
     * ao sinal atual.
     *
     * NÃO executa click().
     */
    public synchronized boolean destacarBotao(
            String direcao
    ) {

        conectar();

        if (
                direcao == null
                        ||
                        direcao.isBlank()
        ) {

            return false;
        }

        /*
         * Remove o amarelo do sinal anterior.
         */
        removerDestaques();


        String textoProcurado;

        if (
                direcao.equalsIgnoreCase(
                        "PARA CIMA"
                )
        ) {

            textoProcurado =
                    "para cima";

        } else if (
                direcao.equalsIgnoreCase(
                        "PARA BAIXO"
                )
        ) {

            textoProcurado =
                    "para baixo";

        } else {

            System.out.println(
                    "Direção desconhecida: "
                            +
                            direcao
            );

            return false;
        }


        WebElement botao =
                localizarBotao(
                        textoProcurado
                );


        if (botao == null) {

            System.out.println();
            System.out.println(
                    "⚠ Selenium não encontrou o botão: "
                            +
                            textoProcurado
            );

            return false;
        }


        JavascriptExecutor js =
                (JavascriptExecutor) driver;


        /*
         * Marca o elemento para podermos
         * remover o destaque posteriormente.
         *
         * NÃO executa clique.
         */
        js.executeScript(
                """
                arguments[0].setAttribute(
                    'data-analisador15s-highlight',
                    'true'
                );

                arguments[0].scrollIntoView({
                    block: 'center',
                    inline: 'center'
                });

                arguments[0].style.outline =
                    '5px solid yellow';

                arguments[0].style.outlineOffset =
                    '4px';

                arguments[0].style.boxShadow =
                    '0 0 20px yellow';
                """,
                botao
        );


        System.out.println();
        System.out.println(
                "========================================"
        );

        System.out.println(
                "BOTÃO LOCALIZADO PELO SELENIUM"
        );

        System.out.println(
                "Direção: "
                        +
                        direcao
        );

        System.out.println(
                "Texto do botão: "
                        +
                        botao.getText()
        );

        System.out.println(
                "✓ Destaque anterior removido."
        );

        System.out.println(
                "✓ Somente o botão atual foi destacado."
        );

        System.out.println(
                "Clique NÃO executado."
        );

        System.out.println(
                "========================================"
        );

        return true;
    }


    /*
     * ============================================================
     * REMOVE DESTAQUES ANTIGOS
     * ============================================================
     */
    public synchronized void removerDestaques() {

        if (driver == null) {
            return;
        }

        try {

            JavascriptExecutor js =
                    (JavascriptExecutor) driver;

            js.executeScript(
                    """
                    document
                        .querySelectorAll(
                            '[data-analisador15s-highlight="true"]'
                        )
                        .forEach(el => {

                            el.style.outline = '';
                            el.style.outlineOffset = '';
                            el.style.boxShadow = '';

                            el.removeAttribute(
                                'data-analisador15s-highlight'
                            );
                        });
                    """
            );

        } catch (Exception e) {

            System.out.println(
                    "⚠ Não foi possível remover o destaque anterior: "
                            +
                            e.getMessage()
            );
        }
    }


    /*
     * ============================================================
     * LOCALIZA BOTÃO
     * ============================================================
     */
    private WebElement localizarBotao(
            String texto
    ) {

        List<WebElement> botoes =
                driver.findElements(
                        By.tagName("button")
                );


        for (
                WebElement botao :
                botoes
        ) {

            try {

                if (!botao.isDisplayed()) {
                    continue;
                }


                String visivel =
                        normalizar(
                                botao.getText()
                        );


                String aria =
                        normalizar(
                                botao.getAttribute(
                                        "aria-label"
                                )
                        );


                String title =
                        normalizar(
                                botao.getAttribute(
                                        "title"
                                )
                        );


                if (
                        visivel.contains(texto)
                                ||
                                aria.contains(texto)
                                ||
                                title.contains(texto)
                ) {

                    return botao;
                }

            } catch (Exception ignored) {
            }
        }


        return null;
    }


    /*
     * ============================================================
     * NORMALIZAÇÃO
     * ============================================================
     */
    private String normalizar(
            String texto
    ) {

        if (texto == null) {
            return "";
        }

        return texto
                .trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
