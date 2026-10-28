package br.com.bradesco.analiseboleto.servicos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public OcrLocalServico(BoletoValidador validador,
                           @Value("${boleto.seguro.tesseract.caminho:C:/Program Files/Tesseract-OCR/tesseract.exe}")
                           String caminho) {
        this.validador = validador;
        this.executavel = new File(caminho);
        if (!executavel.canExecute()) {
            log.info("Tesseract não encontrado em {}: leitura OCR local desativada.", caminho);
        }
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
                Optional<String> linha = executarTesseract(preparada, modo).flatMap(this::extrairLinha);
                if (linha.isPresent()) return linha;
            }
        }
        return Optional.empty();
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

    private Optional<String> executarTesseract(BufferedImage imagem, String modoPagina) {
        Path entrada = null;
        try {
            entrada = Files.createTempFile("boleto-ocr-", ".png");
            ImageIO.write(imagem, "png", entrada.toFile());

            Process processo = new ProcessBuilder(
                    executavel.getAbsolutePath(), entrada.toString(), "stdout",
                    "--psm", modoPagina,
                    "-c", "tessedit_char_whitelist=0123456789. ")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

            String saida = new String(processo.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!processo.waitFor(20, TimeUnit.SECONDS)) {
                processo.destroyForcibly();
                return Optional.empty();
            }
            return Optional.of(saida);
        } catch (IOException e) {
            log.warn("Falha ao executar o Tesseract: {}", e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (entrada != null) entrada.toFile().delete();
        }
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
