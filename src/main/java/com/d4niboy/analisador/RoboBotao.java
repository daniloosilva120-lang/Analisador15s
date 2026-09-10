package com.d4niboy.analisador;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;

public class RoboBotao {

    private static final String CHROMEDRIVER_PATH =
            "C:\\Users\\Engenharia\\.cache\\selenium\\chromedriver\\win64\\151.0.7922.138\\chromedriver.exe";

    private static WebDriver navegador;

    private static final String XPATH_CIMA =
            "//span[normalize-space()='Para cima']/ancestor::button";

    private static final String XPATH_BAIXO =
            "//span[normalize-space()='Para baixo']/ancestor::button";

    public RoboBotao() {
        conectar();
    }

    private static synchronized void conectar() {

        if (navegador != null) {
            return;
        }

        try {

            ChromeOptions opcoes =
                    new ChromeOptions();

            opcoes.setExperimentalOption(
                    "debuggerAddress",
                    "127.0.0.1:9222"
            );

            ChromeDriverService servico =
                    new ChromeDriverService.Builder()
                            .usingDriverExecutable(
                                    new File(
                                            CHROMEDRIVER_PATH
                                    )
                            )
                            .build();

            navegador =
                    new ChromeDriver(
                            servico,
                            opcoes
                    );

            removerDestaque();

        } catch (Exception e) {

            navegador = null;

            System.err.println(
                    "ERRO: Não foi possível conectar ao Chrome especial."
            );

            System.err.println(
                    "Abra primeiro o INICIAR_CHROME.bat."
            );
        }
    }

    public static synchronized void receberSinal(
            String direcao
    ) {

        if (
                direcao == null
                        ||
                        direcao.isBlank()
        ) {
            return;
        }

        if (navegador == null) {
            conectar();
        }

        if (
                direcao.equalsIgnoreCase("PARA CIMA")
                        ||
                        direcao.equalsIgnoreCase("CIMA")
                        ||
                        direcao.equalsIgnoreCase("CALL")
                        ||
                        direcao.equalsIgnoreCase("COMPRA")
        ) {

            clicarCima();

        } else if (
                direcao.equalsIgnoreCase("PARA BAIXO")
                        ||
                        direcao.equalsIgnoreCase("BAIXO")
                        ||
                        direcao.equalsIgnoreCase("PUT")
                        ||
                        direcao.equalsIgnoreCase("VENDA")
        ) {

            clicarBaixo();

        } else {

            System.err.println(
                    "ERRO: Direção desconhecida recebida: "
                            + direcao
            );
        }
    }

    public static synchronized void clicarCompra() {
        clicarCima();
    }

    public static synchronized void clicarVenda() {
        clicarBaixo();
    }

    public static synchronized void clicarCima() {

        if (!garantirConexao()) {
            return;
        }

        removerDestaque();

        try {

            WebElement botao =
                    navegador.findElement(
                            By.xpath(
                                    XPATH_CIMA
                            )
                    );

            destacar(
                    botao
            );

            botao.click();

        } catch (Exception e) {

            System.err.println(
                    "ERRO: Não foi possível clicar em PARA CIMA."
            );
        }
    }

    public static synchronized void clicarBaixo() {

        if (!garantirConexao()) {
            return;
        }

        removerDestaque();

        try {

            WebElement botao =
                    navegador.findElement(
                            By.xpath(
                                    XPATH_BAIXO
                            )
                    );

            destacar(
                    botao
            );

            botao.click();

        } catch (Exception e) {

            System.err.println(
                    "ERRO: Não foi possível clicar em PARA BAIXO."
            );
        }
    }

    private static boolean garantirConexao() {

        if (navegador != null) {
            return true;
        }

        conectar();

        if (navegador == null) {

            System.err.println(
                    "ERRO: Chrome especial não conectado."
            );

            return false;
        }

        return true;
    }

    private static void destacar(
            WebElement elemento
    ) {

        if (
                navegador == null
                        ||
                        elemento == null
        ) {
            return;
        }

        try {

            JavascriptExecutor js =
                    (JavascriptExecutor) navegador;

            js.executeScript(
                    """
                    arguments[0].setAttribute(
                        'data-robo-botao-destaque',
                        'true'
                    );

                    arguments[0].style.outline =
                        '5px solid yellow';

                    arguments[0].style.outlineOffset =
                        '4px';

                    arguments[0].style.boxShadow =
                        '0 0 20px yellow';
                    """,
                    elemento
            );

        } catch (Exception ignored) {
        }
    }

    public static synchronized void removerDestaque() {

        if (navegador == null) {
            return;
        }

        try {

            JavascriptExecutor js =
                    (JavascriptExecutor) navegador;

            js.executeScript(
                    """
                    document
                        .querySelectorAll(
                            '[data-robo-botao-destaque="true"]'
                        )
                        .forEach(function(el) {

                            el.style.outline = '';
                            el.style.outlineOffset = '';
                            el.style.boxShadow = '';

                            el.removeAttribute(
                                'data-robo-botao-destaque'
                            );
                        });
                    """
            );

        } catch (Exception ignored) {
        }
    }

    public boolean estaConectado() {
        return navegador != null;
    }
}
