package br.com.bradesco.analiseboleto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Banco vazio: o Flyway roda todas as migrações e o Hibernate VALIDA (ddl-auto=validate) que as tabelas criadas
 * pelas migrações correspondem exatamente às entidades. Se alguém mudar uma entidade sem criar a migração,
 * este teste falha.
 */
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:migracoes;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "boleto.seguro.jwt.secret=chave-secreta-de-teste-para-testes-unitarios-com-mais-de-64-caracteres",
    "boleto.seguro.jwt.expiration-ms=3600000"
})
class MigracoesBancoTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Migrações aplicadas em banco vazio e esquema validado contra as entidades")
    void migracoesAplicadasEValidadas() {
        List<String> versoes = jdbc.queryForList(
            // version nula = registro de criação do esquema feito pelo próprio Flyway (não é uma migração)
            "select \"version\" from \"flyway_schema_history\" where \"success\" = true and \"version\" is not null " +
            "order by \"installed_rank\"", String.class);
        assertEquals(List.of("1", "2"), versoes);

        List<String> tabelas = jdbc.queryForList(
            "select lower(table_name) from information_schema.tables where table_schema = 'public'", String.class);
        assertTrue(tabelas.containsAll(List.of("usuarios", "boletos", "analises", "alertas", "consulta_cnpj_cache")), tabelas.toString());
    }
}
