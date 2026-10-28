package br.com.bradesco.analiseboleto;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "boleto.seguro.jwt.secret=chave-secreta-de-teste-para-testes-unitarios-com-mais-de-64-caracteres",
    "boleto.seguro.jwt.expiration-ms=3600000"
})
class BradescoAnaliseboletoApplicationTests {

    @Test
    void contextLoads() {
    }
}
