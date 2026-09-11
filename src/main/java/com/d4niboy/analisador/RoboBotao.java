package com.d4niboy.analisador;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class RoboBotao {

    // =========================================================
    // CHROME ESPECIAL
    // =========================================================

    /*
     * Configuracao portatil.
     * Deve usar os mesmos valores do RealTimeFeed.
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

    private static WebDriver navegador;


    // =========================================================
    // BOTOES DA PLATAFORMA
    // =========================================================

    private static final String XPATH_CIMA =
            "//span[normalize-space()='Para cima']/ancestor::button";

    private static final String XPATH_BAIXO =
            "//span[normalize-space()='Para baixo']/ancestor::button";


    // =========================================================
    // CONSTRUTOR
    // =========================================================

    public RoboBotao() {
        conectar();
    }


    // =========================================================
    // CONECTAR AO CHROME ESPECIAL
    // =========================================================

    private static synchronized void conectar() {

        if (navegador != null) {
            return;
        }

        try {

            System.out.println(
                    "ROBO: procurando Chrome especial em " + DEBUGGER_ADDRESS + "..."
            );

            ChromeOptions opcoes =
                    new ChromeOptions();

            opcoes.setExperimentalOption(
                    "debuggerAddress",
                    DEBUGGER_ADDRESS
            );

            /*
             * Não usamos mais caminho fixo para chromedriver.exe.
             *
             * O Selenium Manager procura automaticamente
             * uma versão compatível com o Chrome instalado.
             *
             * Funciona mesmo trocando de computador,
             * usuário do Windows ou versão do Chrome.
             */
            navegador =
                    new ChromeDriver(opcoes);

            System.out.println(
                    "ROBO: conectado ao Chrome especial."
            );

            try {

                System.out.println(
                        "ROBO: página atual: "
                                + navegador.getCurrentUrl()
                );

            } catch (Exception ignored) {
            }

            removerDestaque();

        } catch (Exception e) {

            navegador = null;

            System.err.println();
            System.err.println(
                    "ERRO: não foi possível conectar ao Chrome especial."
            );

            System.err.println(
                    "Abra primeiro o INICIAR_CHROME.bat."
            );

            System.err.println(
                    "Porta esperada: "
                            + DEBUGGER_ADDRESS
            );

            System.err.println();

            if (e.getMessage() != null) {

                System.err.println(
                        "Detalhes: "
                                + e.getMessage()
                );
            }
        }
    }


    // =========================================================
    // RECEBER SINAL
    // =========================================================

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
                    "ERRO: direção desconhecida recebida: "
                            + direcao
            );
        }
    }


    // =========================================================
    // COMPRA / VENDA
    // =========================================================

    public static synchronized void clicarCompra() {
        clicarCima();
    }

    public static synchronized void clicarVenda() {
        clicarBaixo();
    }


    // =========================================================
    // CLICAR PARA CIMA
    // =========================================================

    public static synchronized void clicarCima() {

        if (!garantirConexao()) {
            return;
        }

        /*
         * Segurança:
         * clique automático somente na conta DEMO.
         */
        if (!estaNaContaDemo()) {

            System.err.println(
                    "ROBO: clique automático bloqueado."
            );

            System.err.println(
                    "ROBO: a página atual não foi identificada como DEMO."
            );

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

            destacar(botao);

            botao.click();

            System.out.println(
                    "ROBO: clique PARA CIMA executado na DEMO."
            );

        } catch (Exception e) {

            System.err.println(
                    "ERRO: não foi possível clicar em PARA CIMA."
            );

            if (e.getMessage() != null) {

                System.err.println(
                        "Detalhes: "
                                + e.getMessage()
                );
            }
        }
    }


    // =========================================================
    // CLICAR PARA BAIXO
    // =========================================================

    public static synchronized void clicarBaixo() {

        if (!garantirConexao()) {
            return;
        }

        /*
         * Segurança:
         * clique automático somente na conta DEMO.
         */
        if (!estaNaContaDemo()) {

            System.err.println(
                    "ROBO: clique automático bloqueado."
            );

            System.err.println(
                    "ROBO: a página atual não foi identificada como DEMO."
            );

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

            destacar(botao);

            botao.click();

            System.out.println(
                    "ROBO: clique PARA BAIXO executado na DEMO."
            );

        } catch (Exception e) {

            System.err.println(
                    "ERRO: não foi possível clicar em PARA BAIXO."
            );

            if (e.getMessage() != null) {

                System.err.println(
                        "Detalhes: "
                                + e.getMessage()
                );
            }
        }
    }


    // =========================================================
    // GARANTIR CONEXAO
    // =========================================================

    private static boolean garantirConexao() {

        if (navegador != null) {

            try {

                navegador.getCurrentUrl();

                return true;

            } catch (Exception e) {

                /*
                 * O Chrome foi fechado ou a sessão morreu.
                 * Libera a referência para reconectar.
                 */

                navegador = null;
            }
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


    // =========================================================
    // VERIFICAR CONTA DEMO
    // =========================================================

    private static boolean estaNaContaDemo() {

        if (navegador == null) {
            return false;
        }

        try {

            String url =
                    navegador.getCurrentUrl();

            if (url == null) {
                return false;
            }

            String urlMinuscula =
                    url.toLowerCase();

            return urlMinuscula.contains(
                    "demo-trade"
            );

        } catch (Exception e) {

            return false;
        }
    }


    // =========================================================
    // DESTACAR BOTAO
    // =========================================================

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


    // =========================================================
    // REMOVER DESTAQUE
    // =========================================================

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


    // =========================================================
    // STATUS
    // =========================================================

    public boolean estaConectado() {

        if (navegador == null) {
            return false;
        }

        try {

            navegador.getCurrentUrl();

            return true;

        } catch (Exception e) {

            navegador = null;

            return false;
        }
    }
}

