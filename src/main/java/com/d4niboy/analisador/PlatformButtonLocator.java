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
import java.util.logging.Level;
import java.util.logging.Logger;

public class PlatformButtonLocator {

    /*
     * Configuracao portatil.
     * Usa o mesmo Chrome especial do RoboBotao e RealTimeFeed.
     */
    private static final String CHROME_HOST =
            System.getProperty(
                    "analisador.chrome.host",
                    "127.0.0.1"
            );

    private static final int CHROME_PORT =
            Integer.getInteger(
                    "analisador.chrome.port",
                    9222
            );

    private static final String DEBUGGER_ADDRESS =
            CHROME_HOST + ":" + CHROME_PORT;

    private WebDriver driver;

    public synchronized void conectar() {

        if (driver != null) {

            try {
                driver.getCurrentUrl();
                return;

            } catch (Exception e) {
                driver = null;
            }
        }

        try {

            Logger.getLogger(
                    "org.openqa.selenium.manager.SeleniumManager"
            ).setLevel(Level.OFF);

            ChromeOptions options =
                    new ChromeOptions();

            options.setExperimentalOption(
                    "debuggerAddress",
                    DEBUGGER_ADDRESS
            );

            /*
             * Nao existe mais caminho fixo para chromedriver.exe.
             * O Selenium Manager procura automaticamente
             * uma versao compativel com o Chrome instalado.
             */
            driver =
                    new ChromeDriver(
                            options
                    );

            driver.manage()
                    .timeouts()
                    .implicitlyWait(
                            Duration.ofSeconds(1)
                    );

            System.out.println(
                    "PLATFORM LOCATOR: conectado ao Chrome em "
                            + DEBUGGER_ADDRESS
            );

        } catch (Exception e) {

            driver = null;

            System.out.println(
                    "⚠ Erro ao conectar Selenium ao Chrome: "
                            + e.getMessage()
            );
        }
    }

    public synchronized boolean destacarBotao(
            String direcao
    ) {

        conectar();

        if (driver == null) {
            return false;
        }

        if (
                direcao == null
                        ||
                        direcao.isBlank()
        ) {
            return false;
        }

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
                    "⚠ Direção desconhecida: "
                            + direcao
            );

            return false;
        }

        WebElement botao =
                localizarBotao(
                        textoProcurado
                );

        if (botao == null) {

            System.out.println(
                    "⚠ Botão não encontrado: "
                            + textoProcurado
            );

            return false;
        }

        try {

            JavascriptExecutor js =
                    (JavascriptExecutor) driver;

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

            return true;

        } catch (Exception e) {

            System.out.println(
                    "⚠ Erro ao destacar botão: "
                            + e.getMessage()
            );

            return false;
        }
    }

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
                    "⚠ Não foi possível remover o destaque: "
                            + e.getMessage()
            );
        }
    }

    private WebElement localizarBotao(
            String texto
    ) {

        List<WebElement> botoes =
                driver.findElements(
                        By.tagName("button")
                );

        for (WebElement botao : botoes) {

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
