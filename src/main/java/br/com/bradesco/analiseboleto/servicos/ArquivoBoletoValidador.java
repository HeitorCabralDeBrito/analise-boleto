package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * Confere o arquivo enviado ANTES de qualquer processamento pesado.
 *
 * O tipo é decidido pela assinatura real dos bytes (não pelo nome nem pelo Content-Type enviado pelo navegador),
 * e as dimensões da imagem são lidas só do cabeçalho, sem decodificar a imagem inteira: um arquivo pequeno
 * que declara 100.000 x 100.000 pixels é recusado antes de ocupar a memória do servidor.
 */
@Component
public class ArquivoBoletoValidador {

    static final long TAMANHO_MAXIMO = 10L * 1024 * 1024;
    static final long PIXELS_MAXIMO = 40_000_000L;
    static final int LADO_MINIMO = 100;
    static final int PAGINAS_MAXIMO = 10;

    public enum Tipo { PNG, JPEG, PDF }

    public record ArquivoBoleto(byte[] bytes, Tipo tipo) {
        public boolean pdf() { return tipo == Tipo.PDF; }
    }

    public ArquivoBoleto validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BoletoInvalidoException("Nenhum arquivo enviado.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new BoletoInvalidoException("O arquivo tem mais de 10 MB. Envie uma imagem menor ou um PDF mais leve.");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new BoletoInvalidoException("Não foi possível receber o arquivo. Tente novamente.");
        }

        Tipo tipo = detectarTipo(bytes);
        if (tipo == null) {
            throw new BoletoInvalidoException("Formato não suportado. Envie uma imagem PNG ou JPG, ou um PDF.");
        }
        if (tipo == Tipo.PDF) conferirPdf(bytes);
        else conferirImagem(bytes);
        return new ArquivoBoleto(bytes, tipo);
    }

    /** Tipo pela assinatura (magic bytes) do arquivo. */
    static Tipo detectarTipo(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return Tipo.PNG;
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return Tipo.JPEG;
        }
        if (b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-') {
            return Tipo.PDF;
        }
        return null;
    }

    private void conferirImagem(byte[] bytes) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> leitores = ImageIO.getImageReaders(entrada);
            if (!leitores.hasNext()) throw new BoletoInvalidoException("Imagem inválida ou corrompida.");
            ImageReader leitor = leitores.next();
            try {
                leitor.setInput(entrada, true, true);
                long largura = leitor.getWidth(0);
                long altura = leitor.getHeight(0);
                if (largura * altura > PIXELS_MAXIMO) {
                    throw new BoletoInvalidoException("Imagem grande demais (máximo de 40 megapixels). Reduza a resolução e envie de novo.");
                }
                if (largura < LADO_MINIMO || altura < LADO_MINIMO) {
                    throw new BoletoInvalidoException("Imagem pequena demais para leitura. Envie uma foto ou print maior do boleto.");
                }
            } finally {
                leitor.dispose();
            }
        } catch (IOException e) {
            throw new BoletoInvalidoException("Imagem inválida ou corrompida.");
        }
    }

    private void conferirPdf(byte[] bytes) {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            int paginas = doc.getNumberOfPages();
            if (paginas == 0) throw new BoletoInvalidoException("O PDF não tem páginas.");
            if (paginas > PAGINAS_MAXIMO) {
                throw new BoletoInvalidoException("O PDF tem " + paginas + " páginas; o limite é " + PAGINAS_MAXIMO +
                    ". Envie só as páginas do boleto.");
            }
        } catch (InvalidPasswordException e) {
            throw new BoletoInvalidoException(
                "Este PDF é protegido por senha (comum em faturas de cartão). " +
                "Abra o PDF, use \"Imprimir → Salvar como PDF\" para gerar uma cópia sem senha, " +
                "ou envie um print ampliado só da área do código de barras.");
        } catch (IOException e) {
            throw new BoletoInvalidoException("PDF inválido ou corrompido.");
        }
    }
}
