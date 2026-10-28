# Boleto Seguro — Estado atual do projeto (para gerar a documentação final)

> **Instrução para quem for ler (GPT):** este documento descreve o que está **realmente implementado** no protótipo
> em 27/10/2026, após o Checkpoint 2. Use-o junto com o briefing original para produzir a documentação final e completa.
> Onde houver diferença entre o briefing e este documento, **este documento reflete o código atual**.
> As seções "Limitações" e "Próximos passos" devem ser mantidas: são decisões conscientes, não falhas esquecidas.

---

## 1. Identificação

| Item | Valor |
|---|---|
| Nome | Boleto Seguro |
| Tipo | Protótipo acadêmico de alta fidelidade (atividade A3 — Ciência da Computação) |
| Instituição | Universidade Anhembi Morumbi, em parceria com o Bradesco |
| Equipe | Bruno Henrique e Heitor Cabral de Brito |
| Repositório | https://github.com/HeitorCabralDeBrito/analise-boleto |
| Objetivo | Analisar o risco de fraude de um boleto bancário **antes** de o usuário confirmar o pagamento |

**Princípio do projeto:** o protótipo não simula chamadas reais a bancos nem à CIP (Câmara Interbancária de Pagamentos).
Todas as verificações são feitas com dados que o próprio sistema consegue conferir. O pagamento é **simulado**.

---

## 2. Stack técnica

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 LTS, Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security) |
| Banco de dados | PostgreSQL (porta local 5433, banco `boleto_seguro`); H2 em memória nos testes |
| Autenticação | Spring Security + JWT sem estado (jjwt 0.12.6) |
| Frontend | HTML + Bootstrap 5.3 + JavaScript puro, servido pelo próprio Spring Boot (`static/`) |
| Leitura de código de barras | ZXing 3.5.3 (formato ITF, 44 dígitos) |
| Leitura de PDF | Apache PDFBox 3.0.5 |
| OCR local | Tesseract OCR (programa instalado no Windows, chamado pelo backend) |
| IA (opcional) | API da OpenAI (modelo `gpt-4o-mini`), chamada direta via `RestClient` |
| Testes | JUnit 5 — 43 testes (unitários e de integração com H2) |

---

## 3. Fluxo do usuário

1. **Login** com agência, conta e senha (usuários fictícios de demonstração).
2. **Pagar boleto** — o usuário escolhe como informar o boleto:
   - digitando a linha digitável, ou
   - enviando imagem (PNG/JPG) ou PDF. No celular, pode tirar a foto na hora.
3. Preenche **quem vai receber** (obrigatório): nome e CPF/CNPJ do beneficiário.
4. Opcionalmente informa valor, vencimento e origem do boleto (WhatsApp, e-mail, site, impresso, outro).
5. **Análise** — validação técnica e cálculo do risco.
6. **Resultado** — nota de 0 a 100, nível de risco, explicação em linguagem simples, alertas e verificações detalhadas.
7. **Confirmação simulada** do pagamento. No risco alto, o botão muda para "Confirmar mesmo assim".
8. **Histórico** — todas as análises, com filtros por risco, por situação (pago / não pago) e por busca.

---

## 4. Funcionalidades implementadas

### 4.1 Validação técnica da linha digitável (bloqueio)
- Exige 47 dígitos e aceita a linha com ou sem pontos e espaços.
- Confere os dígitos verificadores dos campos 1, 2 e 3 (Módulo 10) e o dígito geral do código de barras (Módulo 11).
- Se falhar, a análise é **bloqueada** antes do motor de risco, com mensagem explicando o motivo.
- Calcula a data de vencimento a partir do fator FEBRABAN, considerando o reinício do fator em 22/02/2025.

### 4.2 Motor de risco determinístico
Nota de 0 a 100, limitada a 100. Classificação: **0–30 BAIXO**, **31–70 MÉDIO**, **71–100 ALTO**.
O risco ALTO sinaliza que o pagamento deveria ser bloqueado.

| # | Verificação | Critério | Pontos |
|---|---|---|---|
| 1 | Valor | Valor informado ≠ valor gravado no código de barras | +40 |
| 2 | Vencimento | Vencimento informado ≠ vencimento gravado no código de barras | +30 |
| 3 | Prazo de validade | Boleto vencido há mais de 60 dias | +20 |
| 4 | Origem | Boleto recebido pelo WhatsApp (orientação contextual, não acusação) | +15 |
| 5 | Beneficiário | CPF/CNPJ com dígitos verificadores inválidos (documento inexistente) | +50 |
| 5 | Beneficiário | Nome de empresa (LTDA, S.A., Banco, Condomínio…) recebendo em CPF | +30 |
| 5 | Beneficiário | Nome incompleto ou com sequência de números | +20 |

Cada verificação retorna um destes estados: **PASSOU**, **FALHOU** ou **NÃO VERIFICADO**. Este último aparece quando falta
dado para comparar, por exemplo em boletos com valor em aberto, sem vencimento no código, ou quando o usuário não informou o dado.

### 4.3 Validação do beneficiário (novo)
- Nome e CPF/CNPJ do beneficiário são **obrigatórios**, tanto no frontend quanto no backend.
- O CPF/CNPJ é validado pelos dígitos verificadores (Módulo 11 da Receita Federal), no navegador e no servidor.
- Coerência entre o tipo de pessoa e o documento: empresa recebendo em CPF é um sinal clássico de golpe.
- Um CPF/CNPJ com dígito errado **não impede** o envio: a análise segue e aponta o risco, deixando o caso registrado.
- **Não confere** se o CNPJ pertence de fato à empresa informada (ver Próximos passos).

### 4.4 Leitura automática do boleto (imagem/PDF)
A leitura tenta, nesta ordem:
1. **Texto do PDF** (PDFs digitais): procura a linha digitável impressa.
2. **Código de barras** (ZXing): na imagem original, girada 90° e ampliada.
3. **OCR local** (Tesseract): lê os números impressos da linha digitável. Resolve prints de celular em baixa resolução,
   em que as barras ficam finas demais para o leitor de código de barras. Roda no próprio computador.
4. **IA (OpenAI)**: só se as etapas anteriores falharem e houver chave configurada com créditos.

Toda linha lida passa pela validação dos dígitos verificadores antes de ser aceita, então uma leitura errada é descartada.
O usuário sempre confere e pode corrigir a linha antes da análise.
Privacidade: a imagem só sai do servidor na etapa 4.

### 4.5 Explicação do risco em linguagem simples (novo)
- Um cartão "O que isso significa" aparece logo abaixo da nota, explicando o resultado para quem não entende de boletos.
- O texto traz a conclusão pelo nível de risco, os motivos e o que fazer. Exemplo: "não pague; fale com a empresa por um canal
  oficial, nunca pelos contatos que vieram junto com o boleto".
- Inclui uma dica quando faltam dados que deixariam a análise mais completa.
- É **gerado pelas regras do motor, sem IA**: funciona sem internet e sem créditos, e os mesmos dados sempre produzem a mesma
  explicação. A tela indica se o texto veio das regras ou da IA.

### 4.6 Formatação automática dos campos (novo)
O usuário digita só números e o campo se formata sozinho:

| Campo | Formato |
|---|---|
| Linha digitável | `00000.00000 00000.000000 00000.000000 0 00000000000000` |
| CPF / CNPJ | `000.000.000-00`; a partir do 12º dígito vira `00.000.000/0000-00` |
| Valor | Estilo app de banco: digitar `15000` mostra `150,00` |
| Agência / Conta (login) | `0000` / `00000-0` |

O cursor é mantido no lugar ao editar no meio do campo, e apagar em cima de um separador remove o dígito anterior.

### 4.7 Histórico com situação de pagamento (novo)
- Cada análise mostra o selo **Não pago**, **✓ Pago** ou **✓ Pago apesar do risco alto** (em vermelho).
- Há filtro por situação, por nível de risco e busca por linha digitável ou beneficiário.

### 4.8 Segurança e acesso
- Login com JWT. Cada usuário só vê e confirma os próprios boletos; acessar o boleto de outro usuário retorna 403.
- A chave da OpenAI fica em variável de ambiente (`OPENAI_API_KEY`) e nunca no repositório.
- O `application.properties` está no `.gitignore` porque contém a senha do banco.

### 4.9 Responsividade e uso no celular
- As telas usam Bootstrap. A tela de login foi corrigida para celular.
- O app pode ser acessado pelo celular na mesma rede Wi-Fi: `http://<IP-do-notebook>:8080`.
  Para isso, a porta 8080 precisa estar liberada no firewall e a rede marcada como Privada.

---

## 5. API REST

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/login` | Autentica (agência, conta, senha) e retorna o JWT |
| POST | `/api/boletos/analisar` | Valida e analisa um boleto; retorna nota, nível, verificações, alertas e explicação |
| POST | `/api/boletos/ocr` | Recebe imagem/PDF (multipart) e retorna a linha digitável lida |
| POST | `/api/boletos/{boletoId}/confirmar` | Registra a confirmação simulada do pagamento |
| GET | `/api/historico` | Lista as análises do usuário, incluindo `pago` |

Campos obrigatórios em `/analisar`: `linhaDigitavel`, `nomeBeneficiarioDeclarado`, `documentoBeneficiarioDeclarado`
(com 11 ou 14 dígitos). Opcionais: `valorDeclarado`, `vencimentoDeclarado`, `origem`, `tipoEntrada`.

---

## 6. Estrutura do código (principais classes)

| Classe | Papel |
|---|---|
| `BoletoValidador` | Validação técnica da linha digitável (Módulo 10 e 11) e conversão linha ↔ código de barras |
| `MotorRiscoServico` | As 5 verificações, a nota e a classificação de risco |
| `DocumentoValidador` | Validação e formatação de CPF/CNPJ |
| `ExplicacaoRiscoServico` | Monta a explicação em linguagem simples |
| `LeitorBoletoServico` | Orquestra a leitura de imagem/PDF (PDF → código de barras → OCR → IA) |
| `OcrLocalServico` | Chama o Tesseract e extrai uma linha digitável válida do texto lido |
| `LeitorIaServico` | Leitura por IA (OpenAI), usada como último recurso |
| `BoletoServico` | Fluxo de análise, histórico e confirmação simulada |
| `SecurityConfig`, `JwtUtil`, `JwtAuthFilter` | Autenticação JWT |
| `DataSeeder` | Cria os usuários de demonstração na primeira execução |
| `static/js/api.js` | Comunicação com a API, máscaras de digitação e validação de CPF/CNPJ no navegador |

Entidades: `Usuario`, `Boleto`, `Analise` (nota, nível, verificações, explicação, confirmação simulada) e `Alerta`.

### Modelo de dados (confere com o PostgreSQL)

| Relacionamento | Garantia no banco |
|---|---|
| USUARIO 1:N BOLETO | `boletos.usuario_id` NOT NULL + FK |
| BOLETO 1:0..1 ANALISE | `analises.boleto_id` NOT NULL + FK + **UNIQUE** |
| ANALISE 1:0..N ALERTA | `alertas.analise_id` NOT NULL + FK |
| Login único | `usuarios`: **UNIQUE (agencia, conta)** (`uk_usuarios_agencia_conta`) |

Todas as tabelas usam UUID como chave primária. Na tabela `analises`, o campo `explicacao_resultado` guarda o texto mostrado
ao cliente, e `justificativa_ia` fica reservado para a integração futura com IA.

---

## 7. Testes automatizados (43, todos passando)

| Classe de teste | Testes | Cobre |
|---|---|---|
| `BoletoValidadorTest` | 10 | Dígitos verificadores, tamanho, formatos aceitos e bloqueios |
| `MotorRiscoServicoTest` | 7 | Fator de vencimento, risco baixo, valor em aberto e as regras do beneficiário |
| `LeitorBoletoServicoTest` | 6 | Leitura de código de barras e PDF |
| `DocumentoValidadorTest` | 4 | CPF/CNPJ válidos, inválidos, repetidos e formatação |
| `OcrLocalServicoTest` | 4 | Extração da linha no meio do texto, linha quebrada e descarte de leitura errada |
| `ExplicacaoRiscoServicoTest` | 4 | Textos de risco baixo (com e sem ponto de atenção) e alto, e dica de dados faltando |
| `PersistenciaIntegracaoTest` | 7 | Agência + conta únicas, uma análise por boleto, explicação salva e recuperada no histórico, alertas salvos, confirmação só pelo dono |
| `BradescoAnaliseboletoApplicationTests` | 1 | O app inteiro sobe |

---

## 8. Cenários de demonstração

Linha de teste (fictícia, não pagável): `23790.91016 23456.789017 23456.000001 3 16260000015000` — R$ 150,00, vencimento 10/11/2026.

| Cenário | Entrada | Resultado esperado |
|---|---|---|
| A — Baixo risco | Linha acima + `Maria da Silva` / CPF `529.982.247-25` | Nota 0, BAIXO |
| B — Origem suspeita | A + origem WhatsApp | +15 |
| C — Bloqueio técnico | `12345678901234567890123456789012345678901234567` | Bloqueado antes da análise |
| D — Upload | Imagem ou PDF do boleto | Linha preenchida automaticamente |
| E — Valor divergente | A + valor diferente de 150,00 | +40 |
| F1 — Documento inexistente | CPF `529.982.247-26` | +50, MÉDIO |
| F2 — Empresa em CPF | `Condomínio Edifício Primavera` + CPF válido | +30 |
| F3 — Empresa com CNPJ | `Energia Paulista S.A.` + CNPJ `11.222.333/0001-81` | Sem pontos |

Usuários de demonstração: Ag `0001` / Conta `12345-6` / Senha `senha123` e Ag `0001` / Conta `98765-4` / Senha `senha456`.

Caso real testado: um print de celular em baixa resolução de uma fatura de cartão do C6. O leitor de código de barras falhou e o
OCR local leu a linha corretamente. Esse tipo de fatura traz valor R$ 0,00 e vencimento 0000 no código de barras (valor em
aberto), por isso as verificações de valor, vencimento e prazo aparecem como "não verificado".

---

## 9. Limitações conhecidas (decisões conscientes)

- **O nome do beneficiário não é confirmado em base oficial.** O nome não vem no código de barras. A confirmação real exige a
  consulta à base de boletos registrados (CIP/Nova Plataforma de Cobrança), que o protótipo não acessa por princípio.
- **Faturas com valor em aberto ou sem vencimento** (como as de cartão) não permitem comparar valor, vencimento e prazo.
- **IA sem créditos:** a integração com a OpenAI está pronta, mas a conta está sem créditos. O sistema funciona sem ela.
- **OCR local depende do Tesseract instalado** (`C:\Program Files\Tesseract-OCR`). Sem ele, essa etapa é pulada.
- **Pagamento simulado:** nenhum valor é movimentado; a confirmação apenas registra a decisão do usuário no protótipo.
- **Máscara da conta aceita só números.** No Bradesco real, o dígito da conta pode ser a letra "P".
- Execução local; o acesso externo exige rede compartilhada ou túnel.

---

## 10. Próximos passos (planejados)

**Próximos checkpoints**
1. **Consulta de CNPJ na BrasilAPI** (base pública da Receita, gratuita e sem chave): comparar a razão social com o nome
   informado e verificar a situação cadastral (ativa, baixada, inapta).
2. **Mostrar o banco emissor:** os 3 primeiros dígitos da linha identificam o banco (ex.: 237 = Bradesco, 336 = C6).
3. **Boletos sem vencimento no código:** usar o vencimento informado na verificação de prazo e trocar o rótulo
   "valor/vencimento codificado" quando o dado veio do usuário.

**Versão final**
4. **Créditos na OpenAI:** ativar a leitura por IA e fazer a IA reescrever a explicação de risco de forma mais natural, mantendo a
   explicação por regras como reserva.
5. **Histórico de golpes do beneficiário:** botão "Denunciar boleto" e uma verificação "CPF/CNPJ denunciado por outros usuários".
   Inspirado no compartilhamento de dados de fraude entre instituições exigido pelo Banco Central (Resolução Conjunta nº 6/2023).

---

## 11. Histórico de entregas (commits)

| Commit | Descrição |
|---|---|
| `1f494e3` | Estrutura base da aplicação de prevenção a fraudes |
| `505c43a` | Protótipo do Checkpoint 2, com leitura de boleto e IA |
| `4b21efd` | Validação do beneficiário, máscaras, OCR local, explicação do risco e status de pagamento |
| `6348205` | Tela de login responsiva no celular |
| (próximo) | Correções de consistência: agência + conta únicas, uma análise por boleto explícita, explicação salva no histórico, textos revisados |
