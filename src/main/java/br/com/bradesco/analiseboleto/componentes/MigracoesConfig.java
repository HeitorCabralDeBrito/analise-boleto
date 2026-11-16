package br.com.bradesco.analiseboleto.componentes;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Migrações do banco (Flyway, em src/main/resources/db/migration).
 *
 * Bancos criados antes do Checkpoint 3 já têm as tabelas (o Hibernate as criava com ddl-auto=update), mas não têm
 * o histórico do Flyway. Com a linha de base na versão 1, nesses bancos o Flyway considera a V1 já aplicada e roda só
 * as migrações seguintes; num banco vazio, roda todas a partir da V1.
 */
@Configuration
public class MigracoesConfig {

    @Bean
    FlywayConfigurationCustomizer linhaDeBaseParaBancosExistentes() {
        return configuracao -> configuracao.baselineOnMigrate(true).baselineVersion("1");
    }
}
