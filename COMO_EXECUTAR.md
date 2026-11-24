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

## 2. Configurar a sua máquina (`config/application.properties`)

O `src/main/resources/application.properties` está no Git e **não tem segredos**. Os segredos da sua máquina ficam em
`config/application.properties`, que está fora do Git e é carregado por cima dele.

1. Copie o modelo `config/application.properties.example` para `config/application.properties`.
2. Preencha a senha do PostgreSQL e uma chave de login (32+ caracteres; o modelo mostra como gerar):
   ```properties
   spring.datasource.password=SUA_SENHA_DO_POSTGRES
   boleto.seguro.jwt.secret=UM_TEXTO_ALEATORIO_COM_MAIS_DE_32_CARACTERES
   ```
3. Se o PostgreSQL não estiver em `localhost:5433` com o usuário `postgres`, ajuste também `spring.datasource.url`
   e `spring.datasource.username` nesse mesmo arquivo.

Na nuvem, os mesmos valores vêm de variáveis de ambiente (`DB_HOST`, `DB_PASSWORD`, `JWT_SECRET`…): ver `DEPLOY_RENDER.md`.

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
- Beneficiário (obrigatório): nome `Maria da Silva`, CPF `529.982.247-25`
- Não informe valor/vencimento → verificações ficam como NAO_VERIFICADO
- Resultado esperado: score ~0, BAIXO risco, com a explicação "O que isso significa" logo abaixo da nota

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

### Cenário F — Beneficiário suspeito
- Mesma linha do Cenário A
- CPF com dígito errado (`529.982.247-26`) → +50 pontos (documento inexistente)
- Nome de empresa com CPF válido (`Condomínio Edifício Primavera` + `529.982.247-25`) → +30 pontos
- Com CNPJ válido (`Energia Paulista S.A.` + `11.222.333/0001-81`) → sem pontos

Os campos de linha digitável, CPF/CNPJ, valor, agência e conta se formatam enquanto você digita: basta digitar só os números.

---

## 6. Rodar os testes

```
.\mvnw.cmd test
```

---

## 7. Leitura de prints de baixa resolução (Tesseract — opcional)

Quando as barras do código de barras ficam finas demais (print de celular), o sistema lê os
**números impressos** da linha digitável com o Tesseract OCR, rodando no próprio computador.

1. Baixe o instalador do Windows (64 bits) em https://github.com/UB-Mannheim/tesseract/wiki
2. Instale no caminho padrão: `C:\Program Files\Tesseract-OCR`
3. Reinicie a aplicação. No log não deve aparecer "Tesseract não encontrado"

Se instalar em outro lugar, ajuste `boleto.seguro.tesseract.caminho` no `application.properties`.
Ordem de leitura da linha digitável: texto do PDF → código de barras → OCR local → IA (só com chave e créditos).
Os campos impressos (beneficiário, CPF/CNPJ, valor e vencimento) vêm do texto do PDF ou, em imagens e PDFs escaneados,
de uma passada de OCR em português (o pacote de idioma `por` precisa estar instalado no Tesseract).
Em fotos, prints e PDFs escaneados, o que ainda faltar (linha e/ou campos) a IA tenta ler numa só chamada, se houver chave;
ela nunca substitui o que a leitura local já achou, e tudo passa pelas mesmas conferências (DVs, data e valor válidos).
Tudo o que é lido aparece na tela **Conferir** com a origem de cada campo; o usuário corrige e confirma antes da análise.

Limites da leitura (opcionais no `application.properties`; os valores abaixo são os padrões):
```properties
boleto.seguro.tesseract.timeout-segundos=20      # tempo máximo de cada execução do Tesseract
boleto.seguro.leitura.max-simultaneas=2          # leituras ao mesmo tempo; as demais esperam
boleto.seguro.leitura.espera-vaga-segundos=10    # depois disso, responde 503 "tente de novo"
boleto.seguro.leitura.prazo-segundos=60          # prazo total de uma leitura
```
Arquivos aceitos: PNG, JPG ou PDF (tipo conferido pela assinatura do arquivo), até 10 MB, 40 megapixels e 10 páginas.
O arquivo não é gravado: só existem arquivos temporários durante o OCR, apagados em seguida.

---

## 8. Integração com IA (opcional)

Tudo funciona **sem IA**: a leitura do boleto usa texto do PDF, código de barras e OCR local, e a explicação do risco
é gerada pelas regras. Com uma chave da OpenAI (com créditos), a IA entra em dois pontos:

- **Leitura de boletos difíceis** — último recurso, só quando o código de barras e o OCR falham (envia a imagem).
- **Explicação reescrita** — depois que o resultado aparece, a IA reescreve o texto das regras de forma mais natural.
  Recebe só o nível, a nota, o nome/estado das verificações e o texto das regras — **nunca** nome, CPF/CNPJ ou a
  linha digitável. Respostas que suavizam um risco alto, prometem segurança ou contradizem as verificações são
  descartadas, e fica o texto das regras (sempre disponível em "Ver o texto original").

**Configurar a chave (uma vez, no PowerShell, em qualquer pasta):**
```powershell
setx OPENAI_API_KEY (Read-Host "Cole a chave e aperte Enter")
```
Feche e abra o terminal antes de subir o app. A chave fica no seu usuário do Windows; nunca no código nem no Git.
O `application.properties` lê a variável: `boleto.seguro.openai.api-key=${OPENAI_API_KEY:}`.

Opcional no `application.properties`:
```properties
boleto.seguro.openai.model=gpt-4o-mini          # explicação do risco
boleto.seguro.openai.model-leitura=gpt-4o       # leitura de imagens difíceis
boleto.seguro.openai.explicacao.habilitada=true        # false = sempre o texto das regras
boleto.seguro.openai.explicacao.timeout-segundos=20
```
Créditos: https://platform.openai.com (Billing — separado da assinatura do ChatGPT).

## 8.0.1 Denunciar boleto

No resultado e no histórico, o usuário pode **denunciar** um boleto (motivo + descrição opcional). A denúncia vale
para o CPF/CNPJ do beneficiário: cada usuário denuncia um documento uma vez, e quem analisar depois um boleto com o
mesmo documento vê só **quantas pessoas** denunciaram (nem quem, nem o texto). Denúncias não são verificadas:
1 denúncia gera "atenção" sem pontos; 2 ou mais pessoas diferentes somam +25 na análise.

---

## 8.1 Identidade visual: tema próprio e tema acadêmico Bradesco

O sistema tem dois temas com as mesmas telas e o mesmo backend; só mudam cores e logotipo (tokens CSS em `css/boleto-seguro.css`).

- **Tema próprio (Boleto Seguro)** — padrão, azul-marinho. Funciona sem nenhuma configuração.
- **Tema acadêmico (Bradesco)** — uso acadêmico autorizado. Os logotipos do Bradesco ficam na pasta
  `marca-academica/` na raiz do projeto (uso acadêmico autorizado pela professora, só para a apresentação; também vão para a imagem do Render):
  - `logo-horizontal.png` — logotipo horizontal (cabeçalho e login)
  - `logo-vertical.png` — logotipo vertical (reservado)

Com a pasta presente, aparece na tela de login o seletor **Identidade visual**. Sem ela, o tema acadêmico fica indisponível
e o sistema usa sempre o tema próprio. Opcional no `application.properties`:
```properties
boleto.seguro.tema.padrao=proprio                    # ou academico (só vale se os logotipos existirem)
boleto.seguro.tema.academico.pasta=marca-academica   # outra pasta, se preferir
```

---

## 8.2 Acessibilidade

Objetivo: WCAG 2.2 nível AA (verificado por testes, não é certificação). Detalhes e declaração na página `/acessibilidade.html`
(pública, com link na tela de login).

- Preferências opcionais, salvas no navegador: tamanho do texto (A− / A / A+, de 87,5% a 150%) e alto contraste.
  No computador ficam no cabeçalho; no celular, dentro do menu ☰.
- Leitura em voz alta **sob demanda** (Web Speech API) na conferência e no resultado, com "Parar leitura" e tecla Esc.
- Navegação completa pelo teclado, link "Pular para o conteúdo", foco visível, erros ligados aos campos (`aria-invalid`).
- Testes: `ContrasteTemasTest` mede o contraste dos dois temas e do alto contraste; a auditoria com axe-core e o fluxo
  só pelo teclado estão registrados nas evidências do Checkpoint 3.

---

## 8.3 Banco de dados: migrações (Flyway)

As tabelas são criadas e alteradas pelas migrações em `src/main/resources/db/migration` (V1, V2, …), aplicadas
automaticamente ao iniciar. Em um banco que já existia (criado pelo Hibernate antes do Checkpoint 3), o Flyway marca a V1
como linha de base e aplica só as seguintes. No `application.properties`, use:
```properties
spring.jpa.hibernate.ddl-auto=validate   # o Hibernate só confere; quem altera o banco são as migrações
```
Para mudar uma tabela, crie uma nova migração (ex.: `V3__descricao.sql`) — nunca edite uma que já foi aplicada.

## 8.4 Consulta pública de CNPJ e instituição do boleto

- **CNPJ do beneficiário** é consultado na BrasilAPI (dados abertos da Receita Federal, sem chave): razão social,
  nome fantasia e situação cadastral. **CPF nunca é consultado.** O resultado fica em cache no banco
  (`consulta_cnpj_cache`). Se a API falhar, a análise continua: sem dados a verificação fica "não verificada";
  um cache vencido aparece como "desatualizado". Esses dados **não comprovam** que o boleto pertence à empresa.
- **Instituição** é identificada pelos 3 primeiros dígitos da linha, pela tabela `src/main/resources/dados/instituicoes.csv`
  (atualizável sem mexer no código). É quem registrou o boleto, não necessariamente o banco do beneficiário.

Opcional no `application.properties` (valores padrão):
```properties
boleto.seguro.brasilapi.url=https://brasilapi.com.br/api
boleto.seguro.brasilapi.cache-horas=24
boleto.seguro.brasilapi.timeout-ms=5000
```

---

## 9. Endpoints da API REST

| Método | Endpoint | Descrição | Auth |
|--------|----------|-----------|------|
| POST | /api/auth/login | Login (retorna JWT) | Não |
| POST | /api/boletos/analisar | Submete e analisa boleto | JWT |
| POST | /api/boletos/{id}/confirmar | Confirma (simulado) | JWT |
| GET | /api/historico | Histórico do usuário | JWT |
| POST | /api/boletos/ocr | Lê imagem/PDF: linha digitável e campos impressos, com origem e confiança de cada um | JWT |
| GET | /api/config/tema | Tema padrão e se o tema acadêmico está disponível | Não |
| GET | /api/instituicoes | Tabela local de instituições (código → nome) | JWT |
| POST | /api/boletos/{id}/denunciar | Denuncia o beneficiário de um boleto do próprio usuário | JWT |
| POST | /api/boletos/{id}/explicacao-ia | Explicação reescrita pela IA (ou a das regras) | JWT |

---

## 10. Estrutura do projeto

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
