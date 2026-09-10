package com.d4niboy.analisador;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

public class RoboBotao {

    private WebDriver navegador;

    private final String xpathCima =
            "//span[normalize-space()='Para cima']/ancestor::button";

    private final String xpathBaixo =
            "//span[normalize-space()='Para baixo']/ancestor::button";

    public RoboBotao() {
        conectar();
    }

    private void conectar() {
        System.out.println("[RoboBotao] Tentando conectar ao Chrome especial...");
        try {
            ChromeOptions opcoes = new ChromeOptions();
            opcoes.setExperimentalOption("debuggerAddress", "localhost:9222");
            navegador = new ChromeDriver(opcoes);
            System.out.println("[RoboBotao] Conectado ao Chrome com sucesso.");
            removerDestaque();
        } catch (Exception e) {
            navegador = null;
            System.out.println("[RoboBotao] Não foi possível conectar ao Chrome.");
            System.out.println("[RoboBotao] Abra primeiro o INICIAR_CHROME.bat.");
        }
    }

    // Método exigido pelo RealTimeFeed
    public synchronized void receberSinal(String direcao) {
        if (direcao == null) {
            return;
        }

        if (direcao.equalsIgnoreCase("CIMA") || direcao.equalsIgnoreCase("CALL") || direcao.equalsIgnoreCase("COMPRA")) {
            clicarCima();
        } else if (direcao.equalsIgnoreCase("BAIXO") || direcao.equalsIgnoreCase("PUT") || direcao.equalsIgnoreCase("VENDA")) {
            clicarBaixo();
        } else {
            System.out.println("⚠️ [RoboBotao] Direção desconhecida recebida: " + direcao);
        }
    }

    // Métodos chamados pelo RealTimeFeed
    public synchronized void clicarCima() {
        System.out.println("\n🤖 [RoboBotao] Executando ordem: PARA CIMA");
        if (navegador == null) {
            System.out.println("[RoboBotao] Chrome não conectado.");
            return;
        }
        removerDestaque();
        try {
            WebElement botao = navegador.findElement(By.xpath(xpathCima));
            destacar(botao);
            botao.click(); // <--- Clique real no botão da corretora
            System.out.println("🟢 [RoboBotao] Clique em PARA CIMA efetuado com sucesso!");
        } catch (Exception e) {
            System.out.println("❌ [RoboBotao] Não encontrei ou não consegui clicar no botão PARA CIMA.");
        }
    }

    public synchronized void clicarBaixo() {
        System.out.println("\n🤖 [RoboBotao] Executando ordem: PARA BAIXO");
        if (navegador == null) {
            System.out.println("[RoboBotao] Chrome não conectado.");
            return;
        }
        removerDestaque();
        try {
            WebElement botao = navegador.findElement(By.xpath(xpathBaixo));
            destacar(botao);
            botao.click(); // <--- Clique real no botão da corretora
            System.out.println("🔴 [RoboBotao] Clique em PARA BAIXO efetuado com sucesso!");
        } catch (Exception e) {
            System.out.println("❌ [RoboBotao] Não encontrei ou não consegui clicar no botão PARA BAIXO.");
        }
    }

    private void destacar(WebElement elemento) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) navegador;
            js.executeScript(
                    """
                    arguments[0].setAttribute('data-robo-botao-destaque', 'true');
                    arguments[0].style.outline = '5px solid yellow';
                    arguments[0].style.outlineOffset = '4px';
                    arguments[0].style.boxShadow = '0 0 20px yellow';
                    """,
                    elemento
            );
        } catch (Exception ignored) {
        }
    }

    public synchronized void removerDestaque() {
        if (navegador == null) return;
        try {
            JavascriptExecutor js = (JavascriptExecutor) navegador;
            js.executeScript(
                    """
                    document.querySelectorAll('[data-robo-botao-destaque="true"]').forEach(function(el) {
                        el.style.outline = '';
                        el.style.outlineOffset = '';
                        el.style.boxShadow = '';
                        el.removeAttribute('data-robo-botao-destaque');
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
