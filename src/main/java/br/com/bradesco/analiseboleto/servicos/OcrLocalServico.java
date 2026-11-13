package br.com.bradesco.analiseboleto.servicos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Lê os NÚMEROS impressos da linha digitável com o Tesseract OCR instalado na máquina.
 * Serve para prints de baixa resolução: as barras ficam finas demais para o leitor de
 * código de barras, mas os números impressos continuam legíveis.
 *
 * Roda localmente (nenhuma imagem sai do servidor). Sem o Tesseract instalado, fica desativado.
 * A linha só é aceita se passar na validação técnica (DVs Módulo 10 e 11), então um dígito
 * lido errado faz a leitura ser descartada em vez de gerar uma linha falsa.
 */
@Service
public class OcrLocalServico {

    private static final Logger log = LoggerFactory.getLogger(OcrLocalServico.class);
    private static final int DIGITOS_LINHA = 47;

    private final BoletoValidador validador;
    private final File executavel;
    private final long timeoutSegundos;

    @Autowired
    public OcrLocalServico(BoletoValidador validador,
                           @Value("${boleto.seguro.tesseract.caminho:C:/Program Files/Tesseract-OCR/tesseract.exe}")
                           String caminho,
                           @Value("${boleto.seguro.tesseract.timeout-segundos:20}") long timeoutSegundos) {
        this.validador = validador;
        this.executavel = new File(caminho);
        this.timeoutSegundos = timeoutSegundos;
        if (!executavel.canExecute()) {
            log.info("Tesseract não encontrado em {}: leitura OCR local desativada.", caminho);
        }
    }

    /** Para testes: tempo limite padrão de 20 s. */
    public OcrLocalServico(BoletoValidador validador, String caminho) {
        this(validador, caminho, 20);
    }

    public boolean disponivel() {
        return executavel.canExecute();
    }

    /** Retorna a linha digitável (47 dígitos válidos) lida da imagem, ou vazio. */
    public Optional<String> lerLinhaDigitavel(BufferedImage imagem) {
        if (!disponivel() || imagem == null) return Optional.empty();

        // Texto pequeno de print: ampliar com suavização e trocar o modo de leitura
        // (6 = bloco de texto, 11 = texto solto pela imagem) aumenta a chance de acerto
        // Imagem grande (PDF renderizado, foto) já tem letras do tamanho certo: não amplia
        List<Integer> fatores = imagem.getWidth() < 1500 ? List.of(3, 2) : List.of(1);
        for (int fator : fatores) {
            BufferedImage preparada = prepararImagem(imagem, fator);
            for (String modo : List.of("6", "11")) {
                Optional<String> linha = executarTesseract(preparada, "--psm", modo,
                        "-c", "tessedit_char_whitelist=0123456789. ")
                    .flatMap(this::extrairLinha);
                if (linha.isPresent()) return linha;
            }
        }
        return Optional.empty();
    }

    /**
     * Lê todo o texto impresso (letras e números, em português) para localizar beneficiário, valor e vencimento.
     * Uma única passada: o texto é só sugestão para o usuário conferir.
     */
    public Optional<String> lerTexto(BufferedImage imagem) {
        if (!disponivel() || imagem == null) return Optional.empty();
        int fator = imagem.getWidth() < 1500 ? 2 : 1;
        return executarTesseract(prepararImagem(imagem, fator), "-l", "por", "--psm", "3");
    }

    /**
     * Procura, em cada linha de texto do OCR, uma sequência de 47 dígitos que seja uma
     * linha digitável válida. Também tenta o texto todo junto, para o caso de a linha
     * digitável ter sido quebrada em duas linhas na imagem.
     */
    Optional<String> extrairLinha(String textoOcr) {
        List<String> blocos = new java.util.ArrayList<>(List.of(textoOcr.split("\\R")));
        blocos.add(textoOcr);
        for (String bloco : blocos) {
            String digitos = bloco.replaceAll("\\D", "");
            for (int i = 0; i + DIGITOS_LINHA <= digitos.length(); i++) {
                String candidata = digitos.substring(i, i + DIGITOS_LINHA);
                if (validador.validar(candidata).valida()) return Optional.of(candidata);
            }
        }
        return Optional.empty();
    }

    /**
     * Executa o Tesseract com tempo limite. A saída vai para um arquivo temporário (e não é lida do pipe
     * antes do waitFor): assim um Tesseract travado é encerrado no prazo em vez de prender a requisição.
     * Os arquivos temporários (imagem e texto) são apagados sempre.
     */
    Optional<String> executarTesseract(BufferedImage imagem, String... opcoes) {
        Path entrada = null;
        Path saida = null;
        try {
            entrada = Files.createTempFile("boleto-ocr-", ".png");
            saida = Files.createTempFile("boleto-ocr-", ".txt");
            ImageIO.write(imagem, "png", entrada.toFile());

            List<String> comando = new java.util.ArrayList<>(List.of(executavel.getAbsolutePath(), entrada.toString(), "stdout"));
            comando.addAll(List.of(opcoes));
            Process processo = new ProcessBuilder(comando)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(saida.toFile())
                .start();

            if (!processo.waitFor(timeoutSegundos, TimeUnit.SECONDS)) {
                encerrar(processo);
                log.warn("Tesseract excedeu o tempo limite de {} s e foi encerrado.", timeoutSegundos);
                return Optional.empty();
            }
            return Optional.of(Files.readString(saida, StandardCharsets.UTF_8));
        } catch (IOException e) {
            log.warn("Falha ao executar o Tesseract: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            apagar(entrada);
            apagar(saida);
        }
    }

    /** Encerra o processo e os filhos dele, e espera que terminem de fato (no Windows eles seguram os arquivos abertos). */
    private static void encerrar(Process processo) throws InterruptedException {
        List<ProcessHandle> filhos = processo.descendants().toList();
        filhos.forEach(ProcessHandle::destroyForcibly);
        processo.destroyForcibly();
        processo.waitFor(3, TimeUnit.SECONDS);
        for (ProcessHandle filho : filhos) {
            try {
                filho.onExit().get(3, TimeUnit.SECONDS);
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ignorado) {
                // segue: o arquivo será apagado na próxima tentativa ou ao desligar
            }
        }
    }

    private static void apagar(Path arquivo) {
        if (arquivo == null) return;
        // O sistema pode levar alguns instantes para liberar o arquivo de um processo recém-encerrado
        for (int tentativa = 0; tentativa < 5; tentativa++) {
            try {
                Files.deleteIfExists(arquivo);
                return;
            } catch (IOException e) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        arquivo.toFile().deleteOnExit();
    }

    // Tons de cinza + ampliação bicúbica: o Tesseract lê melhor letras com ~30px de altura
    private BufferedImage prepararImagem(BufferedImage src, int fator) {
        BufferedImage dst = new BufferedImage(src.getWidth() * fator, src.getHeight() * fator, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, dst.getWidth(), dst.getHeight(), null);
        g.dispose();
        return dst;
    }
}
