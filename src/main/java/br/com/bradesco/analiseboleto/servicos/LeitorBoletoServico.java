package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.GlobalHistogramBinarizer;
import com.google.zxing.common.HybridBinarizer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrai a linha digitável de uma imagem ou PDF de boleto, sem serviços externos.
 *
 * Estratégias (em ordem):
 *   PDF digital → texto do PDF (PDFBox) procurando a linha digitável impressa
 *   PDF/imagem  → leitura do código de barras ITF de 44 dígitos (ZXing) e reconstrução da linha
 *
 * O resultado sempre deve ser conferido pelo usuário antes da análise.
 */
@Service
public class LeitorBoletoServico {

    // Linha digitável impressa: 5.5 5.6 5.6 1 14, com pontuação/espaços opcionais
    private static final Pattern LINHA_IMPRESSA = Pattern.compile(
        "\\d{5}[.\\s]?\\d{5}\\s*\\d{5}[.\\s]?\\d{6}\\s*\\d{5}[.\\s]?\\d{6}\\s*\\d\\s*\\d{14}");

    private static final Map<DecodeHintType, Object> HINTS = new EnumMap<>(DecodeHintType.class);
    static {
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.ITF));
        HINTS.put(DecodeHintType.ALLOWED_LENGTHS, new int[]{44});
        HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    private final BoletoValidador validador;
    private final LeitorIaServico leitorIa;

    public LeitorBoletoServico(BoletoValidador validador, LeitorIaServico leitorIa) {
        this.validador = validador;
        this.leitorIa = leitorIa;
    }

    public ResultadoLeitura ler(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new BoletoInvalidoException("Nenhum arquivo enviado.");
        }
        try {
            byte[] bytes = arquivo.getBytes();
            boolean pdf = "application/pdf".equals(arquivo.getContentType())
                || (arquivo.getOriginalFilename() != null && arquivo.getOriginalFilename().toLowerCase().endsWith(".pdf"));

            Optional<ResultadoLeitura> resultado = pdf ? lerPdf(bytes) : lerImagem(ImageIO.read(new java.io.ByteArrayInputStream(bytes)));

            // Plano B: IA lê a linha digitável impressa (ex.: código de barras coberto ou borrado)
            if (resultado.isEmpty() && leitorIa.disponivel()) {
                byte[] imagemIa = pdf ? ultimaPaginaComoPng(bytes) : bytes;
                String mime = pdf ? "image/png" : Optional.ofNullable(arquivo.getContentType()).orElse("image/jpeg");
                resultado = leitorIa.lerLinhaDigitavel(imagemIa, mime)
                    .map(linha -> new ResultadoLeitura(linha, "IA"));
            }

            return resultado.orElseThrow(() -> new BoletoInvalidoException(
                "Não foi possível ler o código de barras. Use uma imagem nítida, sem cortes, " +
                "com o código de barras na horizontal — ou digite a linha manualmente."));
        } catch (InvalidPasswordException e) {
            throw new BoletoInvalidoException(
                "Este PDF é protegido por senha (comum em faturas de cartão). " +
                "Abra o PDF, use \"Imprimir → Salvar como PDF\" para gerar uma cópia sem senha, " +
                "ou envie um print ampliado só da área do código de barras.");
        } catch (IOException e) {
            throw new BoletoInvalidoException("Arquivo inválido ou corrompido.");
        }
    }

    private Optional<ResultadoLeitura> lerPdf(byte[] bytes) throws IOException {
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            String texto = new PDFTextStripper().getText(doc);
            Matcher m = LINHA_IMPRESSA.matcher(texto);
            while (m.find()) {
                String linha = validador.normalizar(m.group());
                if (validador.validar(linha).valida()) {
                    return Optional.of(new ResultadoLeitura(linha, "TEXTO_PDF"));
                }
            }

            // Em faturas o boleto costuma ficar nas últimas páginas: varre de trás para frente
            PDFRenderer renderer = new PDFRenderer(doc);
            int ultima = doc.getNumberOfPages() - 1;
            for (int pagina = ultima; pagina >= Math.max(0, ultima - 4); pagina--) {
                BufferedImage imagem = renderer.renderImageWithDPI(pagina, 300, ImageType.GRAY);
                Optional<ResultadoLeitura> r = lerImagem(imagem);
                if (r.isPresent()) return r;
            }
        }
        return Optional.empty();
    }

    private byte[] ultimaPaginaComoPng(byte[] pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            BufferedImage pagina = new PDFRenderer(doc).renderImageWithDPI(doc.getNumberOfPages() - 1, 150, ImageType.RGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            ImageIO.write(pagina, "png", out);
            return out.toByteArray();
        }
    }

    private Optional<ResultadoLeitura> lerImagem(BufferedImage imagem) {
        if (imagem == null) {
            throw new BoletoInvalidoException("Formato de imagem não suportado. Envie PNG, JPG ou PDF.");
        }
        // Tenta na orientação original e girada 90° (foto tirada com o celular em pé),
        // e ampliada quando a imagem é pequena (print de tela: barras finas demais)
        List<BufferedImage> tentativas = new java.util.ArrayList<>(List.of(imagem, girar90(imagem)));
        if (imagem.getWidth() < 2500) {
            BufferedImage ampliada = redimensionar(imagem, 2);
            tentativas.add(ampliada);
            tentativas.add(girar90(ampliada));
        }
        for (BufferedImage tentativa : tentativas) {
            Optional<String> codigo = decodificar(tentativa);
            if (codigo.isPresent()) {
                String linha = validador.montarLinhaDigitavel(codigo.get());
                if (validador.validar(linha).valida()) {
                    return Optional.of(new ResultadoLeitura(linha, "CODIGO_BARRAS"));
                }
            }
        }
        return Optional.empty();
    }

    private Optional<String> decodificar(BufferedImage imagem) {
        LuminanceSource fonte = new BufferedImageLuminanceSource(imagem);
        for (BinaryBitmap bitmap : List.of(
                new BinaryBitmap(new HybridBinarizer(fonte)),
                new BinaryBitmap(new GlobalHistogramBinarizer(fonte)))) {
            try {
                String texto = new MultiFormatReader().decode(bitmap, HINTS).getText();
                if (texto.matches("\\d{44}")) return Optional.of(texto);
            } catch (NotFoundException ignored) {
                // tenta o próximo binarizador
            }
        }
        return Optional.empty();
    }

    private BufferedImage redimensionar(BufferedImage src, int fator) {
        BufferedImage dst = new BufferedImage(src.getWidth() * fator, src.getHeight() * fator, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = dst.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.drawImage(src, 0, 0, dst.getWidth(), dst.getHeight(), null);
        g.dispose();
        return dst;
    }

    private BufferedImage girar90(BufferedImage src) {
        BufferedImage dst = new BufferedImage(src.getHeight(), src.getWidth(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < src.getWidth(); x++) {
            for (int y = 0; y < src.getHeight(); y++) {
                dst.setRGB(src.getHeight() - 1 - y, x, src.getRGB(x, y));
            }
        }
        return dst;
    }

    public record ResultadoLeitura(String linhaDigitavel, String metodo) {}
}
