# Boleto Seguro — Como executar localmente
> Protótipo acadêmico | Checkpoint 2 | Anhembi Morumbi × Bradesco

---

## Pré-requisitos

| Ferramenta | Versão | Download |
|------------|--------|----------|
| Java JDK | 21 LTS | https://adoptium.net/temurin/releases/?version=21 |
| PostgreSQL | 16+ | https://www.enterprisedb.com/downloads/postgres-postgresql-downloads |
| Maven | via `mvnw.cmd` | já incluso no projeto |

---

## 1. Instalar e configurar o PostgreSQL

1. Execute o instalador do PostgreSQL com as opções padrão
2. Defina uma senha para o usuário `postgres` (ex: `bradesco123`)
3. Após instalar, abra o **pgAdmin 4** ou o **SQL Shell (psql)** e execute:
   ```sql
   CREATE DATABASE boleto_seguro;
   ```

---

## 2. Configurar `application.properties`

Abra o arquivo `src/main/resources/application.properties` e ajuste:
```properties
spring.datasource.password=SUA_SENHA_AQUI
```
Se o usuário ou porta forem diferentes do padrão, ajuste também:
```properties
spring.datasource.username=postgres
spring.datasource.url=jdbc:postgresql://localhost:5433/boleto_seguro
```

---

## 3. Iniciar a aplicação

No terminal, dentro da pasta do projeto:

**Windows (PowerShell ou CMD):**
```
.\mvnw.cmd spring-boot:run
```

**Primeira execução:** O Maven baixará as dependências (~200MB). Aguarde.

A aplicação estará disponível em: **http://localhost:8080**

---

## 4. Usuários de demonstração (fictícios, não reais)

| Agência | Conta | Senha |
|---------|-------|-------|
| 0001 | 12345-6 | senha123 |
| 0001 | 98765-4 | senha456 |
| 0002 | 11111-1 | teste123 |

---

## 5. Cenários de demonstração

### Cenário A — Boleto válido, baixo risco
Use a linha digitável:
```
23790.91016 23456.789017 23456.000001 3 16260000015000
```
- Não informe valor/vencimento → verificações ficam como NAO_VERIFICADO
- Resultado esperado: score ~0, BAIXO risco

### Cenário B — Alerta por origem suspeita
- Mesma linha acima
- Selecione origem: **WhatsApp**
- Resultado esperado: +15 pontos, orientação de risco contextual

### Cenário C — Linha digitável inválida (bloqueio técnico)
```
12345678901234567890123456789012345678901234567
```
- Resultado: bloqueio imediato, não chega à análise

### Cenário D — Upload de imagem (OCR)
- Selecione "Imagem / PDF"
- Envie uma imagem de boleto legível
- O sistema tentará extrair a linha digitável; você corrige antes de analisar

### Cenário E — Divergência de valor
- Use uma linha digitável válida
- Informe um valor diferente do codificado no boleto
- Resultado esperado: +40 pontos de risco, alerta de divergência

---

## 6. Rodar os testes

```
.\mvnw.cmd test
```

---

## 7. Integração com IA (Fase 6 — opcional)

Para ativar a explicação por IA, descomente em `application.properties`:
```properties
spring.ai.openai.api-key=sk-...
spring.ai.openai.chat.options.model=gpt-4o
```

Como obter a chave OpenAI:
1. Acesse https://platform.openai.com/api-keys
2. Crie uma conta separada do ChatGPT
3. Clique em "Create new secret key"
4. Adicione créditos em Billing (mínimo ~$5)

---

## 8. Endpoints da API REST

| Método | Endpoint | Descrição | Auth |
|--------|----------|-----------|------|
| POST | /api/auth/login | Login (retorna JWT) | Não |
| POST | /api/boletos/analisar | Submete e analisa boleto | JWT |
| POST | /api/boletos/{id}/confirmar | Confirma (simulado) | JWT |
| GET | /api/historico | Histórico do usuário | JWT |
| POST | /api/boletos/ocr | Upload para OCR | JWT |

---

## 9. Estrutura do projeto

```
src/main/java/br/com/bradesco/analiseboleto/
├── BradescoAnaliseboletoApplication.java   # Main
├── componentes/DataSeeder.java             # Seed de usuários de teste
├── controladores/                          # REST Controllers
│   ├── AuthControlador.java
│   ├── BoletoControlador.java
│   └── HistoricoControlador.java
├── dtos/                                   # Data Transfer Objects
├── entidades/                              # JPA Entities
│   ├── Usuario, Boleto, Analise, Alerta
├── excecoes/                               # Exception Handlers
├── repositorios/                           # Spring Data JPA
├── seguranca/                              # JWT + Spring Security
└── servicos/
    ├── AuthServico.java
    ├── BoletoServico.java
    ├── BoletoValidador.java                # Validação técnica da linha
    └── MotorRiscoServico.java              # Score determinístico

src/main/resources/static/
├── login.html, home.html, pagar-boleto.html
├── resultado.html, confirmacao.html, historico.html
├── css/boleto-seguro.css                   # Tema Bradesco
└── js/api.js                               # Comunicação com API
```

---

## Observações técnicas

- **Banco de dados:** As tabelas são criadas automaticamente na primeira execução (`ddl-auto=update`)
- **O histórico persiste** mesmo após reiniciar a aplicação (PostgreSQL)
- **Nenhum pagamento real** é executado em nenhum cenário
- **A IA é opcional** — o fluxo completo funciona sem chave de API
