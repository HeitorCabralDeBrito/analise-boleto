# Boleto Seguro — Estado atual do projeto (Checkpoint 3)

> **Instrução para quem for ler (pessoa ou IA que vai gerar a documentação final):** este documento descreve o que está
> **realmente implementado** em 16/11/2026, ao fim do Checkpoint 3 (branch `checkpoint3`). Onde houver diferença entre o
> briefing e este documento, **este documento reflete o código atual**. As seções "Limitações" e "Próximos passos" são
> decisões conscientes e devem ser mantidas. Não apresente como pronto nada que esteja em "Próximos passos".

---

## 1. Identificação

| Item | Valor |
|---|---|
| Nome | Boleto Seguro |
| Tipo | Protótipo acadêmico de alta fidelidade (atividade A3 — Ciência da Computação) |
| Instituição | Universidade Anhembi Morumbi, em atividade acadêmica com o Bradesco |
| Equipe | Bruno Henrique, Heitor Cabral de Brito e Miguel Antonio dos Santos Silva |
| Orientação | Profa. Cassilene Assis |
| Repositório | https://github.com/HeitorCabralDeBrito/analise-boleto (branch `checkpoint3`) |
| Objetivo | Analisar o risco de golpe de um boleto bancário **antes** de o usuário confirmar o pagamento |

**Princípios:** o pagamento é **simulado**; não há integração com bancos nem com a CIP. Tudo o que é verificado é verificável
com dados disponíveis ao sistema (código de barras, documentos, base pública de CNPJ). A análise é **determinística**
(regras); a IA só reescreve textos e lê imagens difíceis, nunca decide. Nada lido automaticamente é aceito sem conferência
humana.

---

## 2. Stack técnica

| Camada | Tecnologia |
|---|---|
| Backend | Java 21 LTS, Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Security) |
| Banco | PostgreSQL; tabelas criadas por **migrações Flyway** (V1–V3); `ddl-auto=validate`. H2 nos testes |
| Autenticação | Spring Security + JWT sem estado (jjwt 0.12.6), senha BCrypt |
| Frontend | HTML + Bootstrap 5.3.3 (servido pelo próprio app) + JavaScript puro |
| Leitura de PDF | Apache PDFBox 3.0.5 |
| Código de barras | ZXing 3.5.3 (ITF, 44 dígitos) |
| OCR | Tesseract OCR 5 (processo local; idiomas: dígitos e português) |
| Dados externos | BrasilAPI (CNPJ, Receita Federal), sem chave |
| IA (opcional) | OpenAI (`gpt-4o-mini`), chamada direta via `RestClient` |
| Testes | JUnit 5 — **115 testes**, todos passando |
| Nuvem (preparado) | Docker + Render (`render.yaml`); ainda não publicado |

---

## 3. Fluxo do usuário

1. **Login** com agência, conta e senha (usuários fictícios).
2. **Início:** painel com resumo (analisados, risco alto, pagos apesar do alerta), últimas análises e dicas contra golpes.
3. **Pagar boleto → Importar:** fotografar com a câmera, selecionar ou arrastar imagem/PDF — ou **digitar manualmente**.
4. **Conferir:** campos editáveis (linha, beneficiário, CPF/CNPJ, valor, vencimento) com **selo de origem e confiança**;
   dados do código de barras (instituição, valor, vencimento) em quadro à parte; origem do boleto; "o banco deste boleto é o que você esperava?" (opcional: mostra o banco pelo código e a pessoa só confirma);
   confirmação obrigatória "Conferi os dados".
5. **Resultado:** nota 0–100, nível, explicação em linguagem simples (regras, depois reescrita pela IA), consulta pública
   do CNPJ, instituição, alertas, verificações detalhadas, botão **Denunciar** e "Ouvir resultado".
6. **Confirmação simulada** (no risco alto: "Confirmar mesmo assim").
7. **Histórico:** filtros por situação, risco e busca; selos "Pago", "Pago apesar do risco alto", "Denunciado por você";
   denunciar a partir do histórico.

---

## 4. Funcionalidades

### 4.1 Do Checkpoint 2 (mantidas)
- Validação técnica da linha digitável (47 dígitos, Módulo 10 dos campos e Módulo 11 geral), com bloqueio antes da análise.
- Fator de vencimento FEBRABAN com o reinício de 22/02/2025.
- Validação de CPF/CNPJ (dígitos verificadores) e coerência empresa × CPF.
- Explicação do risco gerada por regras; histórico; confirmação simulada; JWT; acesso só aos próprios boletos.

### 4.2 Identidade visual — dois temas, um código (Etapa 1)
- **Tema próprio "Boleto Seguro"** (padrão; azul-marinho `#0B1F3B` e azul `#1366D6`; logotipo SVG original).
- **Tema acadêmico Bradesco** (vermelho `#CC092F`), com os logotipos da pasta `marca-academica/`
  (uso acadêmico autorizado pela professora, só para a apresentação; vai também para o Render). Seletor na tela de login.
- Cores definidas por variáveis CSS por tema; contraste medido por teste automatizado (WCAG AA).
- Cabeçalho e menu lateral comuns; no celular o menu vira gaveta; aviso permanente "Protótipo acadêmico".
- Bootstrap servido pelo próprio app: as telas funcionam sem internet.

### 4.3 Importação prioritária e leitura inteligente (Etapa 2)
- Linha digitável, nesta ordem: texto do PDF → código de barras (ZXing, também girado e ampliado) → OCR dos números
  (Tesseract) → IA (último recurso, só com chave).
- **Campos impressos** (beneficiário, CPF/CNPJ, valor, vencimento) extraídos do texto do PDF ou por uma passada de OCR em
  português, localizados pelos rótulos. O CPF/CNPJ só é procurado perto de "Beneficiário" (nunca o do pagador).
- Cada campo volta com **fonte** (`TEXTO_PDF`, `CODIGO_BARRAS`, `OCR_LOCAL`, `IA`, `NAO_IDENTIFICADO`) e **confiança**
  (alta, média, baixa). Editar um campo o marca como "Informado por você".
- **Valor e vencimento nunca vêm da linha digitável**: são os impressos ou digitados, comparados de forma independente com
  os gravados no código de barras.
- Proteções do upload: tipo pela assinatura do arquivo; 10 MB; 40 megapixels (lidos do cabeçalho); 10 páginas; render de PDF
  limitado; até 2 leituras simultâneas (as demais recebem 503); prazo total por leitura; timeout real do Tesseract;
  temporários apagados; o arquivo não é guardado.
- Contas de consumo (arrecadação, começam com 8) recebem mensagem clara de tipo não suportado.

### 4.4 Acessibilidade (Etapa 3) — objetivo WCAG 2.2 AA, verificado (não certificado)
- HTML semântico, rótulos ligados, `aria-invalid`, `aria-live` (inclusive resumo da leitura do boleto), foco visível,
  "Pular para o conteúdo", navegação completa por teclado.
- Preferências opcionais (salvas no navegador): **texto A−/A/A+** (87,5% a 150%), **alto contraste**, **Ler ao apontar**.
- **Ouvir esta página** em todas as telas (Web Speech API, sob demanda, Esc para parar) — para baixa visão e dificuldade de
  leitura; leitores de tela (NVDA etc.) são atendidos pelo HTML.
- Página pública `/acessibilidade.html` com instruções e declaração.
- Verificação: axe-core sem violações em todas as telas (dois temas, alto contraste, texto 150%); fluxo completo só por
  teclado; 320 px sem rolagem lateral; teste preliminar com NVDA feito pela equipe sem problemas.

### 4.5 Validações externas (Etapa 4)
- **Consulta pública de CNPJ** (BrasilAPI): razão social, nome fantasia, situação cadastral e data; comparação do nome
  informado (ignora acentos, pontuação e forma jurídica). Cache no PostgreSQL (24 h), timeout de 5 s, tratamento de
  404/429/5xx e disjuntor (pausa após 3 falhas). Sem dados → "não verificado"; cache vencido → "desatualizado".
  **CPF nunca é consultado.** Sempre acompanhada do aviso de que não comprova que o boleto é da empresa.
- **Instituição pelo código** de 3 dígitos (tabela local `dados/instituicoes.csv`, 39 instituições, atualizável), descrita
  como "quem registrou o boleto, não necessariamente o banco de quem recebe". Comparação com o banco esperado, se informado.
- **Vencimento:** sem data no código de barras, o prazo é conferido pela data informada — muito vencida gera **Atenção**
  sem pontos (sem evidência independente). Termos "codificado" substituídos por "gravado no código de barras".
- **Origem de cada dado** e momento da conferência gravados no boleto (migração V2).

### 4.6 Denúncias e IA (Etapa 4b)
- **Denunciar boleto** (resultado e histórico): motivo + descrição opcional; vale para o CPF/CNPJ do beneficiário; um por
  usuário e documento; só o dono do boleto denuncia; outros veem só a **quantidade** de pessoas. 1 denúncia = atenção sem
  pontos; 2 ou mais pessoas = +25.
- **Explicação reescrita pela IA:** o resultado aparece na hora (regras) e a versão da IA substitui o texto depois
  (`POST /explicacao-ia`), salva em `justificativa_ia`; "Ver o texto original" mostra o das regras. A IA recebe só nível,
  nota e nome/estado das verificações. Respostas que suavizam risco alto, prometem segurança ou dizem "não há problemas"
  quando algo falhou são descartadas. Testado com a OpenAI real.

### 4.7 Motor de risco (nota 0–100; baixo 0–30, médio 31–70, alto 71–100)

| # | Verificação | Critério | Pontos |
|---|---|---|---|
| 1 | Valor | Informado ≠ gravado no código de barras | +40 |
| 2 | Vencimento | Informado ≠ gravado no código | +30 |
| 3 | Prazo | Vencido há > 60 dias pelo código · pela data só informada | +20 · atenção 0 |
| 4 | Origem | WhatsApp | +15 |
| 5 | Beneficiário | CPF/CNPJ inexistente · empresa em CPF · nome incompleto | +50 · +30 · +20 |
| 6 | Cadastro público do CNPJ | Não ativo · não encontrado · nome diferente | +30 · +20 · +20 |
| 7 | Instituição | Diferente da esperada (se informada) | +15 |
| 8 | Denúncias de outros usuários | 1 · 2 ou mais | atenção 0 · +25 |

Estados: **Passou**, **Falhou**, **Atenção**, **Não verificado** (dado ausente ou fonte indisponível — nunca conta como fraude).

---

## 5. API REST

| Método | Rota | Descrição |
|---|---|---|
| POST | `/api/auth/login` | Login; devolve JWT |
| GET | `/api/config/tema` | Tema padrão e disponibilidade do tema acadêmico (público) |
| POST | `/api/boletos/ocr` | Lê imagem/PDF: linha e campos com fonte e confiança |
| POST | `/api/boletos/analisar` | Valida e analisa |
| POST | `/api/boletos/{id}/explicacao-ia` | Explicação reescrita pela IA |
| POST | `/api/boletos/{id}/confirmar` | Confirmação simulada |
| POST | `/api/boletos/{id}/denunciar` | Denúncia do beneficiário |
| GET | `/api/historico` | Histórico do usuário |
| GET | `/api/instituicoes` | Tabela de instituições |

Exemplos reais de requisição e resposta: `docs/API.md`.

---

## 6. Estrutura do código (principais classes)

| Classe | Papel |
|---|---|
| `BoletoValidador` | Linha digitável: Módulo 10/11, conversão linha ↔ código de barras, arrecadação |
| `ArquivoBoletoValidador` | Assinatura, tamanho, pixels e páginas do arquivo enviado |
| `LeitorBoletoServico` | Orquestra a leitura (PDF → código de barras → OCR → IA), limites, prazo e tempo |
| `ExtratorCamposBoleto` | Campos impressos a partir do texto, com fonte e confiança |
| `OcrLocalServico` | Tesseract (dígitos e português) com tempo limite |
| `LeitorIaServico` | Leitura por IA (`gpt-4o`, imagem pequena ampliada antes): linha digitável e campos impressos que a leitura local não achou |
| `MotorRiscoServico` | As 8 verificações, nota e nível |
| `ExplicacaoRiscoServico` | Explicação por regras (cita exatamente o que faltou informar) |
| `ExplicacaoIaServico` | Reescrita da explicação pela IA, com conferências |
| `ConsultaCnpjServico` + `ComparadorNomes` | Consulta pública de CNPJ com cache e disjuntor; comparação de nomes |
| `InstituicaoServico` | Tabela local de instituições |
| `DenunciaServico` | Denúncias e contagem por documento |
| `BoletoServico` | Fluxo de análise, histórico, confirmação, explicação por IA |
| `TemaConfig`, `MigracoesConfig`, `DataSeeder` | Temas e logotipos locais; Flyway; usuários de demonstração |
| `SecurityConfig`, `JwtUtil`, `JwtAuthFilter` | Autenticação, CORS configurável, rotas públicas |
| `static/js/*` | `api.js`, `layout.js`, `tema.js`, `preferencias.js`, `leitura-voz.js`, `denuncia.js` |

Arquitetura e diagramas: `docs/ARQUITETURA.md`.

---

## 7. Modelo de dados

Seis tabelas: `usuarios`, `boletos`, `analises`, `alertas`, `denuncias`, `consulta_cnpj_cache` (+ `flyway_schema_history`).

| Relacionamento | Garantia |
|---|---|
| USUARIO 1:N BOLETO | FK `boletos.usuario_id` |
| BOLETO 1:0..1 ANALISE | FK + **UNIQUE** `analises.boleto_id` |
| ANALISE 1:0..N ALERTA | FK `alertas.analise_id` |
| USUARIO 1:N DENUNCIA, BOLETO 1:N DENUNCIA | FKs; **UNIQUE** (`usuario_id`, `documento_beneficiario`) |
| Login único | **UNIQUE** (`agencia`, `conta`) |
| Cache de CNPJ | ligação lógica pelo CNPJ (sem FK); só dados públicos |

Migrações: V1 (esquema do CP2, extraído do banco real), V2 (cache de CNPJ, origem dos dados, conferência, banco esperado),
V3 (denúncias). MER/DER e dicionário: `docs/MODELO_DE_DADOS.md`.

---

## 8. Testes automatizados (115, todos passando)

| Classe | Testes | Cobre |
|---|---|---|
| `MotorRiscoServicoTest` | 17 | Regras de pontuação, CNPJ (ativo, baixado, não encontrado, nome diferente, indisponível, desatualizado), instituição, denúncias, vencimento só informado, ausência do termo "codificado" |
| `BoletoValidadorTest` | 12 | Dígitos verificadores, formatos, bloqueios, arrecadação |
| `LeitorBoletoServicoTest` | 13 | Código de barras, PDF textual com campos, IA completando o que a leitura local não leu, IA em paralelo interrompendo o OCR lento, limite de leituras simultâneas, Tesseract real (quando instalado), arrecadação por imagem |
| `ExtratorCamposBoletoTest` | 8 | Campos de PDF e de OCR, CPF do pagador ignorado, rótulo vizinho, data impossível |
| `ConsultaCnpjServicoTest` | 7 | Servidor falso: 200 com cache, CPF não consultado, 404, 500, 429 com cache vencido, timeout, disjuntor |
| `LeitorIaServicoTest` | 9 | Linha e campos lidos pela IA, descarte do que não passa nas conferências, ampliação de imagens pequenas, divergência com o código pede conferência, prioridade da leitura local, sem chave |
| `ExplicacaoIaServicoTest` | 7 | Servidor falso: sem dados pessoais no pedido, descarte de respostas perigosas, timeout, sem chave |
| `PersistenciaIntegracaoTest` | 7 | Restrições do banco, explicação salva, alertas, confirmação só pelo dono |
| `ArquivoBoletoValidadorTest` | 6 | Assinatura do arquivo, arquivo disfarçado, imagem gigante pelo cabeçalho, páginas demais |
| `OcrLocalServicoTest` | 5 | Extração da linha, leitura errada descartada, Tesseract travado encerrado no prazo |
| `ExplicacaoRiscoServicoTest` | 5 | Textos por nível e dica com exatamente o que faltou |
| `ComparadorNomesEInstituicaoTest` | 4 | Comparação de nomes e tabela de instituições |
| `DocumentoValidadorTest` | 4 | CPF/CNPJ |
| `ContrasteTemasTest` | 3 | Contraste WCAG AA dos dois temas e do alto contraste, lido do CSS real |
| `TemaConfigTest` | 3 | Tema acadêmico só com os logotipos presentes |
| `DenunciaIntegracaoTest` | 3 | Só o dono denuncia, uma por usuário/documento, 1 = atenção e 2 = +25 |
| `MigracoesBancoTest` | 1 | Migrações em banco vazio validadas contra as entidades |
| `BradescoAnaliseboletoApplicationTests` | 1 | O app inteiro sobe |

Além disso: auditoria axe-core, fluxo completo por teclado, testes de ponta a ponta com a BrasilAPI e a OpenAI reais e
screenshots — registrados nas evidências do Checkpoint 3. Roteiro manual em `docs/testes/ROTEIRO_DE_TESTES.md`.

---

## 9. Cenários de demonstração

Linha de teste (fictícia, não pagável): `23790.91016 23456.789017 23456.000001 3 16260000015000` — R$ 150,00, vencimento
10/11/2026, instituição 237 (Bradesco). Boletos fictícios prontos em `docs/testes/amostras/`.

| Cenário | Entrada | Resultado esperado |
|---|---|---|
| A — Baixo risco | Linha + `Maria da Silva` / CPF `529.982.247-25` | Nota 0, baixo |
| B — Origem | A + WhatsApp | +15 |
| C — Bloqueio técnico | `12345678901234567890123456789012345678901234567` | Bloqueado antes da análise |
| D — Importação | `1_boleto_textual.pdf` / `2_boleto_nitido.png` | Campos lidos com origem e confiança |
| E — Valor divergente | A + valor ≠ 150,00 | +40 |
| F1 — Documento inexistente | CPF `529.982.247-26` | +50, médio |
| F2 — Empresa em CPF | `Condomínio Edifício Primavera` + CPF válido | +30 |
| F3 — CNPJ confere | `Banco Bradesco S.A.` + `60.746.948/0001-12` | CNPJ ativo, nome confere, 0 |
| F4 — Nome diferente do cadastro | `Energia Paulista S.A.` + `11.222.333/0001-81` (CNPJ real de uma escola) | +20 |
| G — Banco esperado | A + "Não, outro banco" → Itaú (341) | +15 |
| H — Risco alto | E + F1 | Alto; "Confirmar mesmo assim"; IA começa com "Não pague este boleto." |
| I — Denúncias | Mesmo CPF denunciado por 2 outros usuários | +25 |

Usuários: Ag `0001` / Conta `12345-6` / `senha123` · Ag `0001` / Conta `98765-4` / `senha456` · Ag `0002` / Conta `11111-1` / `teste123`.

---

## 10. Limitações conhecidas (decisões conscientes)

- **Quem recebe o dinheiro não é confirmado.** Só a base de boletos registrados (CIP/NPC) confirmaria; o protótipo não a acessa.
  O CNPJ na Receita mostra existência e situação da empresa, não a titularidade do boleto.
- **Boletos de arrecadação** (contas de luz, água, gás, telefone e tributos) não são analisados. O sistema os
  **reconhece** — pela linha digitada (começa com 8 e tem 48 números) e também por foto ou PDF, pelo código de barras —
  e mostra: *"Este parece ser um boleto de arrecadação (...). O Boleto Seguro analisa somente boletos bancários de
  cobrança (47 números)."* Nada é gravado. Por que ficou de fora:
  - seguem **outro padrão Febraban**: no lugar do banco, o código traz o segmento (energia, água, telefone, órgão público)
    e a **empresa que cobra** (concessionária) — a verificação "é o banco que você esperava?" não se aplica;
  - o **vencimento em geral não vem no código**, então não há como comparar com o impresso;
  - os **dígitos verificadores** são calculados de outro jeito (Módulo 10 ou 11 conforme o 3º dígito, em 4 blocos de 11);
  - é um canal com menos golpes de boleto adulterado do que o boleto de cobrança, foco do desafio.

  **Como seria a evolução** (não implementada): validar os 4 blocos e o dígito geral; conferir o valor impresso contra o
  do código; mostrar o segmento (ex.: "conta de energia"); identificar a empresa pelo código Febraban (ou CNPJ, em alguns
  segmentos) e alertar quando não bater com o nome impresso. Mexe na leitura, na validação e no motor de risco, por isso
  ficou para depois da entrega.
- **OCR depende da qualidade da imagem** e do Tesseract com o idioma português instalado; fotos muito ruins pedem digitação.
- **Denúncias não são verificadas** e a base é só a deste protótipo.
- **IA opcional:** depende de chave e créditos da OpenAI; sem ela, tudo funciona com o texto das regras.
- **Tema acadêmico**: logotipos do Bradesco com uso acadêmico autorizado pela professora, só para a apresentação; o site no Render sai do ar depois dela.
- **Máscara da conta aceita só números** (no Bradesco real, o dígito pode ser "P").
- **Testes com leitores de tela por usuários reais** (NVDA, TalkBack, VoiceOver) ainda não foram feitos; houve teste
  preliminar com NVDA pela equipe.
- **Pagamento simulado:** nenhum valor é movimentado.

---

## 11. Próximos passos

1. ~~Testes manuais pelo roteiro~~ — feitos pela equipe em 16/11/2026, sem defeitos abertos; dados de teste apagados do
   banco local na mesma data (backup guardado fora do repositório).
2. Publicação no Render (tudo preparado: `Dockerfile`, `render.yaml`, `DEPLOY_RENDER.md`) — **a confirmar com a professora
   se é necessária**; a apresentação pode ser feita a partir do notebook.
3. Repositório próprio da equipe no GitHub.
4. Teste com usuários de leitores de tela e TalkBack.
5. Futuro (não implementado): agente com Spring AI usando o histórico; consulta a fontes oficiais de boletos registrados
   mediante convênio; boletos de arrecadação (ver Limitações).

---

## 12. Histórico de entregas (commits do Checkpoint 3)

| Commit | Descrição |
|---|---|
| `52eee3f` | Design system com dois temas (próprio e acadêmico Bradesco) |
| `5b3c621`, `1b52fd2` | Ajustes visuais do login e do logotipo |
| `98137b5` | Importação prioritária com leitura dos campos impressos |
| `98f2570` | Painel em duas colunas na tela inicial |
| `9097ff3` | Acessibilidade: preferências, voz sob demanda, página de acessibilidade |
| `eed3e22`, `31eebe1` | "Ouvir esta página" em todas as telas e "Ler ao apontar" |
| `e6033a8` | Consulta pública de CNPJ, instituição pelo código, migrações Flyway |
| `93b3a1a` | Denunciar boleto e explicação reescrita pela IA |
| `83511f3` | Preparação para a nuvem sem segredos no repositório |
| `08a6430` | Bootstrap local e kit de testes |
| `81895cc` | Mensagem clara para contas de consumo |
| `a80c1fc` | Documentação final: README, arquitetura, modelo de dados, API, roteiro do vídeo |
| `9dacf20`, `7e4ef5d` | Hifenização desligada; login mais limpo, dicas no celular e pergunta do banco |
| `7f161bb` | Erros de requisição com status correto (400/405/413) e erros internos registrados no log |
| (este) | Documentação: limitação das contas de consumo, equipe e post |

Checkpoint 2: `1f494e3`, `505c43a`, `4b21efd`, `6348205`, `50338e3`.
