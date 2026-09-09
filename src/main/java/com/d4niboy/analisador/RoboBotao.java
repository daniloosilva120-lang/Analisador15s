package com.d4niboy.analisador;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import java.util.Scanner; // Import necessário para ler o teclado

public class RoboBotao {
    public static void main(String[] args) {
        System.out.println("Iniciando conexão com o Chrome especial...");

        try {
            // Conecta ao Chrome do .bat
            ChromeOptions opcoes = new ChromeOptions();
            opcoes.setExperimentalOption("debuggerAddress", "localhost:9222");
            WebDriver navegador = new ChromeDriver(opcoes);

            System.out.println("Conectado com sucesso!");

            // XPaths inteligentes baseados nas suas imagens
            String xpathCima = "//span[text()='Para cima']/ancestor::button";
            String xpathBaixo = "//span[text()='Para baixo']/ancestor::button";

            // Prepara o leitor do terminal
            Scanner scanner = new Scanner(System.in);

            System.out.println("\n======================================");
            System.out.println("ROBÔ PRONTO! Comandos:");
            System.out.println("Digite 1 + Enter para CIMA (Verde)");
            System.out.println("Digite 2 + Enter para BAIXO (Vermelho)");
            System.out.println("======================================");

            // Loop infinito para manter o programa rodando
            while (true) {
                System.out.print("\nAguardando comando (1 ou 2): ");
                String comando = scanner.nextLine();

                try {
                    if (comando.equals("1")) {
                        WebElement botaoCima = navegador.findElement(By.xpath(xpathCima));
                        botaoCima.click();
                        System.out.println("✅ Ordem enviada: PARA CIMA!");
                    }
                    else if (comando.equals("2")) {
                        WebElement botaoBaixo = navegador.findElement(By.xpath(xpathBaixo));
                        botaoBaixo.click();
                        System.out.println("🔻 Ordem enviada: PARA BAIXO!");
                    }
                    else {
                        System.out.println("Comando inválido. Digite 1 para Cima ou 2 para Baixo.");
                    }
                } catch (Exception e) {
                    System.out.println("❌ Erro: Não encontrei o botão. A tela da corretora está visível?");
                }
            }

        } catch (Exception e) {
            System.out.println("Falha de conexão. Verifique se o .bat está rodando.");
        }
    }
}
