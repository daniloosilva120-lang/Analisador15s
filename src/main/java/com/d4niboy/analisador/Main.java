package com.d4niboy.analisador;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final int CHROME_PORT = 9222;

    private static final String URL =
            "https://qxbroker.com/pt/demo-trade";

    private static Process chromeProcess;

    public static void main(String[] args) {

        // ======================================================
        // SILENCIA AVISOS INTERNOS DO SELENIUM
        // ======================================================

        Logger.getLogger(
                "org.openqa.selenium.manager.SeleniumManager"
        ).setLevel(Level.OFF);

        Logger.getLogger(
                "org.openqa.selenium.manager"
        ).setLevel(Level.OFF);

        Logger.getLogger(
                "org.openqa.selenium"
        ).setLevel(Level.SEVERE);

        try {

            System.out.println(
                    "=========================================="
            );

            System.out.println(
                    "       ANALISADOR 15s - INICIANDO"
            );

            System.out.println(
                    "=========================================="
            );

            System.out.println();


            // ==================================================
            // ABRE O CHROME ESPECIAL
            // ==================================================

            iniciarChromeEspecial();


            // ==================================================
            // ENCERRAMENTO DO PROGRAMA
            // ==================================================

            Runtime.getRuntime().addShutdownHook(
                    new Thread(
                            () -> {

                                System.out.println();

                                System.out.println(
                                        "Encerrando Analisador15s..."
                                );

                                // Libera a porta 8765
                                SignalHttpServer.parar();

                                // Fecha somente o Chrome especial
                                fecharChromeEspecial();
                            }
                    )
            );


            // ==================================================
            // SERVIDOR LOCAL DE SINAIS
            // ==================================================

            SignalHttpServer.iniciar();


            // ==================================================
            // JANELA RECEPTORA
            // ==================================================

            SignalReceiverWindow.abrir();


            // ==================================================
            // FEED + ANALISADOR + ROBO
            // ==================================================

            RealTimeFeed feed =
                    new RealTimeFeed();

            feed.iniciar();


            System.out.println();

            System.out.println(
                    "=========================================="
            );

            System.out.println(
                    "       ANALISADOR 15s PRONTO"
            );

            System.out.println(
                    "=========================================="
            );

            System.out.println();


            // ==================================================
            // MANTEM O PROGRAMA EXECUTANDO
            // ==================================================

            Thread.currentThread().join();

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();

        } catch (Exception e) {

            System.err.println();

            System.err.println(
                    "ERRO AO INICIAR O ANALISADOR:"
            );

            if (e.getMessage() != null) {

                System.err.println(
                        e.getMessage()
                );
            }

            e.printStackTrace();

            SignalHttpServer.parar();

            fecharChromeEspecial();
        }
    }


    // ==========================================================
    // INICIAR CHROME ESPECIAL
    // ==========================================================

    private static void iniciarChromeEspecial()
            throws Exception {

        System.out.println(
                "Procurando Google Chrome..."
        );

        Path chrome =
                localizarChrome();

        if (chrome == null) {

            throw new IOException(
                    "Google Chrome nao foi encontrado neste computador."
            );
        }

        System.out.println(
                "Chrome encontrado:"
        );

        System.out.println(
                chrome
        );

        System.out.println();


        // ======================================================
        // PERFIL PORTATIL
        // ======================================================

        String localAppData =
                System.getenv(
                        "LOCALAPPDATA"
                );

        if (
                localAppData == null
                        ||
                        localAppData.isBlank()
        ) {

            localAppData =
                    System.getProperty(
                            "user.home"
                    );
        }


        Path perfil =
                Path.of(
                        localAppData,
                        "Analisador15s",
                        "ChromeDebug"
                );


        Files.createDirectories(
                perfil
        );


        System.out.println(
                "Perfil especial:"
        );

        System.out.println(
                perfil
        );

        System.out.println();


        // ======================================================
        // VERIFICA PORTA 9222
        // ======================================================

        if (
                portaAberta(
                        CHROME_PORT
                )
        ) {

            throw new IOException(
                    "A porta "
                            + CHROME_PORT
                            + " ja esta sendo usada.\n"
                            + "Feche o Chrome especial antigo e execute novamente."
            );
        }


        // ======================================================
        // COMANDO DO CHROME
        // ======================================================

        List<String> comando =
                new ArrayList<>();


        comando.add(
                chrome.toString()
        );


        comando.add(
                "--remote-debugging-port="
                        + CHROME_PORT
        );


        comando.add(
                "--user-data-dir="
                        + perfil
        );


        comando.add(
                "--no-first-run"
        );


        comando.add(
                "--no-default-browser-check"
        );


        comando.add(
                "--new-window"
        );


        comando.add(
                URL
        );


        System.out.println(
                "Abrindo Chrome especial..."
        );


        ProcessBuilder pb =
                new ProcessBuilder(
                        comando
                );


        chromeProcess =
                pb.start();


        // ======================================================
        // AGUARDA PORTA 9222
        // ======================================================

        System.out.println(
                "Aguardando porta "
                        + CHROME_PORT
                        + "..."
        );


        boolean conectado =
                false;


        for (
                int i = 0;
                i < 30;
                i++
        ) {

            if (
                    portaAberta(
                            CHROME_PORT
                    )
            ) {

                conectado =
                        true;

                break;
            }


            Thread.sleep(
                    500
            );
        }


        if (!conectado) {

            fecharChromeEspecial();

            throw new IOException(
                    "O Chrome abriu, mas a porta "
                            + CHROME_PORT
                            + " nao respondeu."
            );
        }


        System.out.println();

        System.out.println(
                "Chrome especial pronto."
        );

        System.out.println(
                "Porta: "
                        + CHROME_PORT
        );

        System.out.println();
    }


    // ==========================================================
    // FECHAR CHROME ESPECIAL
    // ==========================================================

    private static synchronized void fecharChromeEspecial() {

        Process processo =
                chromeProcess;

        if (processo == null) {
            return;
        }

        try {

            long pid =
                    processo.pid();


            System.out.println(
                    "Fechando Chrome especial..."
            );


            /*
             * /T:
             * encerra também processos filhos.
             *
             * /F:
             * força o encerramento.
             *
             * Utiliza somente o PID do Chrome
             * iniciado pelo Main.
             */
            new ProcessBuilder(
                    "taskkill",
                    "/PID",
                    String.valueOf(
                            pid
                    ),
                    "/T",
                    "/F"
            )
                    .redirectErrorStream(
                            true
                    )
                    .start()
                    .waitFor();


        } catch (Exception e) {

            try {

                processo.destroyForcibly();

            } catch (Exception ignored) {
            }
        }


        chromeProcess =
                null;
    }


    // ==========================================================
    // TESTAR PORTA
    // ==========================================================

    private static boolean portaAberta(
            int porta
    ) {

        try (
                Socket socket =
                        new Socket()
        ) {

            socket.connect(
                    new InetSocketAddress(
                            "127.0.0.1",
                            porta
                    ),
                    300
            );

            return true;

        } catch (IOException e) {

            return false;
        }
    }


    // ==========================================================
    // LOCALIZAR CHROME
    // ==========================================================

    private static Path localizarChrome() {

        List<Path> caminhos =
                new ArrayList<>();


        String programFiles =
                System.getenv(
                        "ProgramFiles"
                );


        String programFilesX86 =
                System.getenv(
                        "ProgramFiles(x86)"
                );


        String localAppData =
                System.getenv(
                        "LOCALAPPDATA"
                );


        if (programFiles != null) {

            caminhos.add(
                    Path.of(
                            programFiles,
                            "Google",
                            "Chrome",
                            "Application",
                            "chrome.exe"
                    )
            );
        }


        if (programFilesX86 != null) {

            caminhos.add(
                    Path.of(
                            programFilesX86,
                            "Google",
                            "Chrome",
                            "Application",
                            "chrome.exe"
                    )
            );
        }


        if (localAppData != null) {

            caminhos.add(
                    Path.of(
                            localAppData,
                            "Google",
                            "Chrome",
                            "Application",
                            "chrome.exe"
                    )
            );
        }


        for (
                Path caminho :
                caminhos
        ) {

            if (
                    Files.isRegularFile(
                            caminho
                    )
            ) {

                return caminho;
            }
        }


        return localizarChromeRegistro();
    }


    // ==========================================================
    // LOCALIZAR CHROME NO REGISTRO
    // ==========================================================

    private static Path localizarChromeRegistro() {

        String[] chaves = {

                "HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\chrome.exe",

                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\chrome.exe"
        };


        for (
                String chave :
                chaves
        ) {

            try {

                Process processo =
                        new ProcessBuilder(
                                "reg",
                                "query",
                                chave,
                                "/ve"
                        )
                                .redirectErrorStream(
                                        true
                                )
                                .start();


                String resultado =
                        new String(
                                processo
                                        .getInputStream()
                                        .readAllBytes()
                        );


                processo.waitFor();


                for (
                        String linha :
                        resultado.split(
                                "\\R"
                        )
                ) {

                    if (
                            !linha.contains(
                                    "REG_SZ"
                            )
                    ) {

                        continue;
                    }


                    int pos =
                            linha.indexOf(
                                    "REG_SZ"
                            );


                    String caminho =
                            linha.substring(
                                            pos
                                                    + "REG_SZ"
                                                    .length()
                                    )
                                    .trim();


                    Path chrome =
                            Path.of(
                                    caminho
                            );


                    if (
                            Files.isRegularFile(
                                    chrome
                            )
                    ) {

                        return chrome;
                    }
                }

            } catch (Exception ignored) {
            }
        }


        return null;
    }
}
