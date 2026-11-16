package br.com.bradesco.analiseboleto.componentes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Identidade visual: tema próprio (padrão) e tema acadêmico Bradesco.
 *
 * Os logotipos do Bradesco NÃO ficam no repositório: são lidos de uma pasta local fora do git
 * (por padrão ./marca-academica) e servidos em /marca-academica/**. Sem essa pasta, o tema acadêmico
 * fica indisponível e o sistema usa sempre a identidade própria.
 */
@Configuration
public class TemaConfig implements WebMvcConfigurer {

    public static final String ROTA_MARCA = "/marca-academica/";
    public static final String LOGO_HORIZONTAL = "logo-horizontal.png";

    private final String temaPadrao;
    private final Path pastaMarca;

    public TemaConfig(@Value("${boleto.seguro.tema.padrao:proprio}") String temaPadrao,
                      @Value("${boleto.seguro.tema.academico.pasta:marca-academica}") String pastaMarca) {
        this.temaPadrao = temaPadrao;
        this.pastaMarca = Path.of(pastaMarca).toAbsolutePath().normalize();
    }

    public boolean academicoDisponivel() {
        return Files.isRegularFile(pastaMarca.resolve(LOGO_HORIZONTAL));
    }

    /** O tema acadêmico só vale como padrão se os logotipos estiverem presentes. */
    public String temaPadrao() {
        return "academico".equals(temaPadrao) && academicoDisponivel() ? "academico" : "proprio";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(ROTA_MARCA + "**")
                .addResourceLocations(pastaMarca.toUri().toString());
    }
}
