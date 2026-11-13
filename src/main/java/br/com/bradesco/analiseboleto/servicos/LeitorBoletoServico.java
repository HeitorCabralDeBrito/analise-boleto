package br.com.bradesco.analiseboleto.servicos;

import br.com.bradesco.analiseboleto.excecoes.BoletoInvalidoException;
import br.com.bradesco.analiseboleto.excecoes.ServicoOcupadoException;
import br.com.bradesco.analiseboleto.servicos.ArquivoBoletoValidador.ArquivoBoleto;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Campo;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Campos;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Confianca;
import br.com.bradesco.analiseboleto.servicos.ExtratorCamposBoleto.Fonte;
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
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê um boleto enviado como imagem ou PDF: a linha digitável e os campos impressos
 * (beneficiário, CPF/CNPJ, valor e vencimento), informando de onde veio cada dado e com que confiança.
 *
 * Linha digitável, em ordem:
 *   PDF digital → texto do PDF (PDFBox) procurando a linha digitável impressa
 *   PDF/imagem  → código de barras ITF de 44 dígitos (ZXing) e reconstrução da linha
 *   PDF/imagem  → OCR local (Tesseract) dos números impressos, para prints de baixa resolução
 *   PDF/imagem  → IA (OpenAI), só se tudo acima falhar e houver chave configurada
 * Campos impressos: texto do PDF ou, se não houver texto, uma passada de OCR local.
 *
 * Proteções: tipo verificado pela assinatura do arquivo, limites de tamanho, pixels e páginas
 * (ArquivoBoletoValidador), no máximo N leituras ao mesmo tempo, prazo total por leitura e tempo limite
 * por chamada do Tesseract. Nada é gravado em disco além dos temporários do OCR, que são apagados.
 *
 * O resultado é sempre uma sugestão: o usuário confere e confirma cada campo antes da análise.
 */
@Service
public class LeitorBoletoServico {

    private static final Logger log = LoggerFactory.getLogger(LeitorBoletoServico.class);

    // Linha digitável impressa: 5.5 5.6 5.6 1 14, com pontuação/espaços opcionais
    private static final Pattern LINHA_IMPRESSA = Pattern.compile(
        "\\d{5}[.\\s]?\\d{5}\\s*\\d{5}[.\\s]?\\d{6}\\s*\\d{5}[.\\s]?\\d{6}\\s*\\d\\s*\\d{14}");

    // Texto de PDF com menos que isso é um PDF escaneado (só imagem): os campos vêm do OCR
    private static final int TEXTO_MINIMO_PDF = 40;
    // Página de PDF renderizada nunca passa disso, mesmo que o PDF declare uma página enorme
    private static final double PIXELS_MAXIMO_RENDER = 25_000_000;

    private static final Map<DecodeHintType, Object> HINTS = new EnumMap<>(DecodeHintType.class);
    static {
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.ITF));
        HINTS.put(DecodeHintType.ALLOWED_LENGTHS, new int[]{44});
        HINTS.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
    }

    private final BoletoValidador validador;
    private final LeitorIaServico leitorIa;
    private final OcrLocalServico ocrLocal;
    private final ArquivoBoletoValidador validadorArquivo;
    private final ExtratorCamposBoleto extrator;
    private final InstituicaoServico instituicoes;
    private final Semaphore leiturasSimultaneas;
    private final long esperaVagaSegundos;
    private final long prazoSegundos;

    @Autowired
    public LeitorBoletoServico(BoletoValidador validador, LeitorIaServico leitorIa, OcrLocalServico ocrLocal,
                               ArquivoBoletoValidador validadorArquivo, ExtratorCamposBoleto extrator,
                               InstituicaoServico instituicoes,
                               @Value("${boleto.seguro.leitura.max-simultaneas:2}") int maxSimultaneas,
                               @Value("${boleto.seguro.leitura.espera-vaga-segundos:10}") long esperaVagaSegundos,
                               @Value("${boleto.seguro.leitura.prazo-segundos:60}") long prazoSegundos) {
        this.validador = validador;
        this.leitorIa = leitorIa;
        this.ocrLocal = ocrLocal;
        this.validadorArquivo = validadorArquivo;
        this.extrator = extrator;
        this.instituicoes = instituicoes;
        this.leiturasSimultaneas = new Semaphore(maxSimultaneas, true);
        this.esperaVagaSegundos = esperaVagaSegundos;
        this.prazoSegundos = prazoSegundos;
    }

    /** Para testes: limites padrão. */
    public LeitorBoletoServico(BoletoValidador validador, LeitorIaServico leitorIa, OcrLocalServico ocrLocal) {
        this(validador, leitorIa, ocrLocal, new ArquivoBoletoValidador(), new ExtratorCamposBoleto(), new InstituicaoServico(), 2, 10, 60);
    }

    public ResultadoLeitura ler(MultipartFile multipart) {
        ArquivoBoleto arquivo = validadorArquivo.validar(multipart);
        try {
            if (!leiturasSimultaneas.tryAcquire(esperaVagaSegundos, TimeUnit.SECONDS)) {
                throw new ServicoOcupadoException(
                    "Muitas leituras de boleto ao mesmo tempo. Aguarde alguns segundos e tente de novo, ou digite os dados.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicoOcupadoException("Leitura interrompida. Tente de novo.");
        }
        try {
            return lerArquivo(arquivo);
        } finally {
            leiturasSimultaneas.release();
        }
    }

    private ResultadoLeitura lerArquivo(ArquivoBoleto arquivo) {
        long inicio = System.nanoTime();
        long limite = inicio + TimeUnit.SECONDS.toNanos(prazoSegundos);
        List<String> avisos = new ArrayList<>();
        try {
            Optional<Leitura> linha;
            Campos campos = Campos.vazios();
            BufferedImage imagemOcr;

            if (arquivo.pdf()) {
                try (PDDocument doc = Loader.loadPDF(arquivo.bytes())) {
                    String texto = new PDFTextStripper().getText(doc);
                    linha = linhaNoTexto(texto);
                    boolean temTexto = texto.strip().length() >= TEXTO_MINIMO_PDF;
                    if (temTexto) campos = extrator.extrair(texto, Fonte.TEXTO_PDF);
                    if (linha.isEmpty()) linha = codigoBarrasNasPaginas(doc, limite);
                    // OCR só é preciso se faltou a linha ou se o PDF é escaneado (sem texto)
                    imagemOcr = linha.isEmpty() || !temTexto ? renderizar(doc, doc.getNumberOfPages() - 1, 300, ImageType.RGB) : null;
                }
            } else {
                BufferedImage imagem = ImageIO.read(new ByteArrayInputStream(arquivo.bytes()));
                if (imagem == null) throw new BoletoInvalidoException("Imagem inválida ou corrompida.");
                linha = lerCodigoBarras(imagem);
                imagemOcr = imagem;
            }

            // Plano B: OCR local dos números impressos (barras finas demais em prints de baixa resolução)
            if (linha.isEmpty() && ocrLocal.disponivel() && dentroDoPrazo(limite, avisos)) {
                linha = ocrLocal.lerLinhaDigitavel(imagemOcr).map(l -> new Leitura(l, Fonte.OCR_LOCAL));
            }

            // Campos impressos de imagens e PDFs escaneados: uma passada de OCR do texto todo
            if (campos.identificados() == 0 && imagemOcr != null && ocrLocal.disponivel() && dentroDoPrazo(limite, avisos)) {
                campos = ocrLocal.lerTexto(imagemOcr).map(t -> extrator.extrair(t, Fonte.OCR_LOCAL)).orElse(campos);
            }

            // Plano C: IA lê a linha digitável impressa (ex.: código de barras coberto ou borrado)
            if (linha.isEmpty() && leitorIa.disponivel() && dentroDoPrazo(limite, avisos)) {
                byte[] imagemIa = arquivo.pdf() ? comoPng(imagemOcr) : arquivo.bytes();
                String mime = arquivo.pdf() || arquivo.tipo() == ArquivoBoletoValidador.Tipo.PNG ? "image/png" : "image/jpeg";
                linha = leitorIa.lerLinhaDigitavel(imagemIa, mime).map(l -> new Leitura(l, Fonte.IA));
            }

            if (linha.isEmpty() && campos.identificados() == 0) {
                throw new BoletoInvalidoException(
                    "Não foi possível ler este boleto. Use uma imagem nítida, sem cortes, " +
                    "com o código de barras na horizontal — ou digite os dados manualmente.");
            }
            if (linha.isEmpty()) {
                avisos.add("Não identificamos a linha digitável. Digite-a a partir do boleto (são 47 números).");
            }

            long tempoMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - inicio);
            // Log sem dados do boleto: só o que é preciso para medir desempenho
            log.info("Leitura de boleto: tipo={}, linha={}, campos={}/4, {} ms",
                arquivo.tipo(), linha.map(l -> l.fonte().name()).orElse("NAO_IDENTIFICADA"), campos.identificados(), tempoMs);

            return new ResultadoLeitura(
                linha.map(Leitura::linha).orElse(null),
                linha.map(l -> l.fonte().name()).orElse(null),
                linha.map(l -> new Campo(l.linha(), l.fonte(), confiancaDaLinha(l.fonte()), null)).orElse(Campo.naoIdentificado()),
                campos,
                linha.map(l -> dadosDoCodigo(l.linha())).orElse(null),
                tempoMs,
                avisos);
        } catch (IOException e) {
            throw new BoletoInvalidoException("Arquivo inválido ou corrompido.");
        }
    }

    // A linha só é aceita depois de passar nos dígitos verificadores; pelo código de barras ou pelo
    // texto do PDF ela é exata. OCR e IA passaram nos DVs, mas ainda pedem conferência visual.
    private static Confianca confiancaDaLinha(Fonte fonte) {
        return fonte == Fonte.CODIGO_BARRAS || fonte == Fonte.TEXTO_PDF ? Confianca.ALTA : Confianca.MEDIA;
    }

    private boolean dentroDoPrazo(long limite, List<String> avisos) {
        if (System.nanoTime() < limite) return true;
        String aviso = "A leitura atingiu o tempo limite; alguns dados podem não ter sido lidos.";
        if (!avisos.contains(aviso)) avisos.add(aviso);
        return false;
    }

    /** Valor e vencimento gravados no código de barras, só para exibição e comparação. */
    private DadosCodigo dadosDoCodigo(String linha) {
        String codigo = validador.extrairCodigoBarras(linha);
        int fator = Integer.parseInt(codigo.substring(5, 9));
        BigDecimal valor = new BigDecimal(codigo.substring(9, 19)).movePointLeft(2);
        LocalDate vencimento = fator == 0 ? null : MotorRiscoServico.calcularDataVencimento(fator);
        return new DadosCodigo(
            linha.substring(0, 3),
            instituicoes.porCodigo(linha.substring(0, 3)).map(InstituicaoServico.Instituicao::nome).orElse(null),
            valor.signum() == 0 ? null : valor,
            vencimento == null ? null : vencimento.toString());
    }

    private Optional<Leitura> linhaNoTexto(String texto) {
        Matcher m = LINHA_IMPRESSA.matcher(texto);
        while (m.find()) {
            String linha = validador.normalizar(m.group());
            if (validador.validar(linha).valida()) return Optional.of(new Leitura(linha, Fonte.TEXTO_PDF));
        }
        return Optional.empty();
    }

    // Em faturas o boleto costuma ficar nas últimas páginas: varre de trás para frente
    private Optional<Leitura> codigoBarrasNasPaginas(PDDocument doc, long limite) throws IOException {
        int ultima = doc.getNumberOfPages() - 1;
        for (int pagina = ultima; pagina >= Math.max(0, ultima - 4) && System.nanoTime() < limite; pagina--) {
            Optional<Leitura> r = lerCodigoBarras(renderizar(doc, pagina, 300, ImageType.GRAY));
            if (r.isPresent()) return r;
        }
        return Optional.empty();
    }

    private BufferedImage renderizar(PDDocument doc, int pagina, int dpi, ImageType tipo) throws IOException {
        PDRectangle caixa = doc.getPage(pagina).getCropBox();
        double escala = dpi / 72.0;
        double pixels = caixa.getWidth() * escala * caixa.getHeight() * escala;
        if (pixels > PIXELS_MAXIMO_RENDER) escala *= Math.sqrt(PIXELS_MAXIMO_RENDER / pixels);
        return new PDFRenderer(doc).renderImage(pagina, (float) escala, tipo);
    }

    private static byte[] comoPng(BufferedImage imagem) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(imagem, "png", out);
        return out.toByteArray();
    }

    private Optional<Leitura> lerCodigoBarras(BufferedImage imagem) {
        // Tenta na orientação original e girada 90° (foto tirada com o celular em pé),
        // e ampliada quando a imagem é pequena (print de tela: barras finas demais)
        List<BufferedImage> tentativas = new ArrayList<>(List.of(imagem, girar90(imagem)));
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
                    return Optional.of(new Leitura(linha, Fonte.CODIGO_BARRAS));
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

    private record Leitura(String linha, Fonte fonte) {}

    /**
     * Dados gravados no código de barras. banco = código de 3 dígitos; instituicao = nome pela tabela local
     * (null se o código não está nela). É a instituição que registrou o boleto, não necessariamente a do beneficiário.
     */
    public record DadosCodigo(String banco, String instituicao, BigDecimal valor, String vencimento) {}

    /**
     * @param linhaDigitavel linha lida (47 dígitos) ou null se não identificada
     * @param metodo         fonte da linha (TEXTO_PDF, CODIGO_BARRAS, OCR_LOCAL, IA) ou null
     * @param linha          a mesma linha como campo, com fonte e confiança
     * @param campos         campos impressos: sugestões para o usuário conferir
     * @param codigo         valor e vencimento gravados no código de barras (para comparação), ou null
     */
    public record ResultadoLeitura(String linhaDigitavel, String metodo, Campo linha, Campos campos,
                                   DadosCodigo codigo, long tempoMs, List<String> avisos) {}
}
