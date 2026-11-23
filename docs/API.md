# API REST do Boleto Seguro

> Exemplos reais, capturados do sistema em 13/11/2026 com dados fictícios (linha de teste, usuário de demonstração).
> Base: `http://localhost:8080` (local) ou o endereço da nuvem. Formato: JSON em UTF-8.

## Autenticação

Faça login em `POST /api/auth/login` e envie o token recebido em todas as outras chamadas:
```
Authorization: Bearer <token>
```
O token vale 24 horas. Sem token ou com token inválido, as rotas protegidas respondem **401/403**.

## Resumo

| Método | Rota | Login | Descrição |
|---|---|---|---|
| POST | `/api/auth/login` | não | Entra com agência, conta e senha; devolve o token |
| GET | `/api/config/tema` | não | Tema padrão e se o tema acadêmico está disponível |
| POST | `/api/boletos/ocr` | sim | Lê imagem/PDF: linha digitável e campos impressos, com fonte e confiança |
| POST | `/api/boletos/analisar` | sim | Valida e analisa um boleto; devolve nota, nível, verificações e explicação |
| POST | `/api/boletos/{boletoId}/explicacao-ia` | sim | Explicação reescrita pela IA (ou a das regras) |
| POST | `/api/boletos/{boletoId}/confirmar` | sim | Registra a confirmação **simulada** do pagamento |
| POST | `/api/boletos/{boletoId}/denunciar` | sim | Denuncia o CPF/CNPJ do beneficiário de um boleto do próprio usuário |
| GET | `/api/historico` | sim | Análises do usuário, da mais recente para a mais antiga |
| GET | `/api/instituicoes` | sim | Tabela local de instituições (código → nome) |

Rotas com `{boletoId}` só funcionam para boletos **do próprio usuário**; de outro usuário → `403 {"erro": "Acesso negado."}`.

---

## POST `/api/auth/login`
```json
{ "agencia": "0001", "conta": "12345-6", "senha": "senha123" }
```
**200**
```json
{ "token": "eyJhbGciOiJIUzUxMiJ9…", "nome": "João Silva", "agencia": "0001", "conta": "12345-6" }
```
**401** `{ "erro": "Agência, conta ou senha incorretos." }`

## GET `/api/config/tema`
**200** `{ "padrao": "proprio", "academicoDisponivel": true }`
(`academicoDisponivel` é `true` só quando a pasta `marca-academica` com os logotipos existe.)

## POST `/api/boletos/ocr`
`multipart/form-data` com o campo **`arquivo`** (PNG, JPG ou PDF; até 10 MB, 40 megapixels e 10 páginas; o tipo é
conferido pela assinatura do arquivo).

**200** (exemplo com `docs/testes/amostras/1_boleto_textual.pdf`)
```json
{
  "linhaDigitavel": "23790910162345678901723456000001316260000015000",
  "metodo": "TEXTO_PDF",
  "linha": { "valor": "2379091016…015000", "fonte": "TEXTO_PDF", "confianca": "ALTA", "observacao": null },
  "campos": {
    "nomeBeneficiario":     { "valor": "ENERGIA PAULISTA S.A.", "fonte": "TEXTO_PDF", "confianca": "MEDIA",
                              "observacao": "Nome localizado pelo rótulo \"Beneficiário\". Confira se está completo." },
    "documentoBeneficiario": { "valor": "11222333000181", "fonte": "TEXTO_PDF", "confianca": "ALTA", "observacao": null },
    "valor":                 { "valor": "150,00",        "fonte": "TEXTO_PDF", "confianca": "ALTA", "observacao": null },
    "vencimento":            { "valor": "2026-11-10",    "fonte": "TEXTO_PDF", "confianca": "ALTA", "observacao": null }
  },
  "codigo": { "banco": "237", "instituicao": "Bradesco", "valor": 150.0, "vencimento": "2026-11-10" },
  "tempoMs": 87,
  "avisos": []
}
```
- `fonte`: `TEXTO_PDF`, `CODIGO_BARRAS`, `OCR_LOCAL`, `IA` ou `NAO_IDENTIFICADO` (com `valor: null`).
- `confianca`: `ALTA`, `MEDIA` ou `BAIXA` (OCR e IA nunca são `ALTA` nos campos impressos).
- `codigo`: dados gravados no código de barras — só para comparação; `valor`/`vencimento` nulos quando não gravados.
- `linhaDigitavel` nula + aviso quando só os campos foram lidos.

**400** `{ "erro": "Formato não suportado. Envie uma imagem PNG ou JPG, ou um PDF." }` (e outros erros amigáveis:
PDF com senha, páginas demais, imagem grande demais, conta de consumo, nada lido)
**503** `{ "erro": "Muitas leituras de boleto ao mesmo tempo…" }` com o cabeçalho `Retry-After: 10`.

## POST `/api/boletos/analisar`
```json
{
  "linhaDigitavel": "23790.91016 23456.789017 23456.000001 3 16260000015000",
  "tipoEntrada": "PDF",
  "origem": "EMAIL",
  "nomeBeneficiarioDeclarado": "Banco Bradesco S.A.",
  "documentoBeneficiarioDeclarado": "60.746.948/0001-12",
  "valorDeclarado": "150,00",
  "vencimentoDeclarado": "2026-11-10",
  "fontes": { "linha": "TEXTO_PDF", "nome": "USUARIO", "documento": "USUARIO", "valor": "TEXTO_PDF", "vencimento": "TEXTO_PDF" },
  "dadosConferidos": true,
  "instituicaoEsperada": "237"
}
```
Obrigatórios: `linhaDigitavel`, `nomeBeneficiarioDeclarado`, `documentoBeneficiarioDeclarado` (11 ou 14 dígitos).
Opcionais: `tipoEntrada` (`MANUAL`, `IMAGEM`, `PDF`), `origem` (`WHATSAPP`, `EMAIL`, `SITE`, `IMPRESSO`, `OUTRO`),
`valorDeclarado`, `vencimentoDeclarado` (AAAA-MM-DD), `fontes`, `dadosConferidos`, `instituicaoEsperada` (3 dígitos).

**200** (resumido)
```json
{
  "boletoId": "c79b2b8c-aaa1-4827-b7fd-4a01345d1731",
  "analiseId": "1de785b8-e059-4c34-963d-1370f3bdfcce",
  "score": 0,
  "nivelRisco": "BAIXO",
  "bloquearPagamento": false,
  "verificacoes": [
    { "nome": "Valor do boleto", "estado": "PASSOU", "pontosAdicionados": 0,
      "descricao": "Valor informado (R$ 150,00) igual ao valor gravado no código de barras." },
    { "nome": "Cadastro público do CNPJ", "estado": "PASSOU", "pontosAdicionados": 0,
      "descricao": "Razão social: BANCO BRADESCO S.A. (nome fantasia: BRADESCO EST UNIF); situação cadastral: ATIVA…" },
    { "nome": "Instituição do boleto", "estado": "PASSOU", "pontosAdicionados": 0,
      "descricao": "Instituição indicada no código: 237 – Bradesco, a mesma que você esperava…" },
    { "nome": "Denúncias de outros usuários", "estado": "PASSOU", "pontosAdicionados": 0,
      "descricao": "Nenhum outro usuário deste protótipo denunciou boletos com este CPF/CNPJ." }
  ],
  "alertas": [],
  "explicacao": "Não encontramos sinais de golpe nas verificações que conseguimos fazer.\n\nMesmo assim…",
  "explicacaoRegras": "Não encontramos sinais de golpe…",
  "explicacaoGeradaPorIa": false,
  "iaDisponivel": true,
  "linhaDigitavel": "23790910162345678901723456000001316260000015000",
  "nomeBeneficiario": "Banco Bradesco S.A.",
  "documentoBeneficiario": "60.746.948/0001-12",
  "valor": "R$ 150,00",
  "vencimento": "10/11/2026",
  "instituicao": { "codigo": "237", "nome": "Bradesco" },
  "consultaCnpj": {
    "estado": "ENCONTRADO", "cnpj": "60.746.948/0001-12", "razaoSocial": "BANCO BRADESCO S.A.",
    "nomeFantasia": "BRADESCO EST UNIF", "situacaoCadastral": "ATIVA", "dataSituacaoCadastral": "03/11/2005",
    "consultadoEm": "13/11/2026 15:46", "fonte": "BrasilAPI", "semelhancaNome": "CONFERE"
  },
  "denunciasOutrosUsuarios": 0,
  "denunciadoPorMim": false
}
```
- `estado` de cada verificação: `PASSOU`, `FALHOU`, `ATENCAO` ou `NAO_VERIFICADO`.
- `nivelRisco`: `BAIXO` (0–30), `MEDIO` (31–70), `ALTO` (71–100); `bloquearPagamento` é `true` no risco alto.
- `consultaCnpj` é nulo para CPF. `estado`: `ENCONTRADO`, `NAO_ENCONTRADO`, `DESATUALIZADO` ou `INDISPONIVEL`.

**400** — linha inválida (nada é gravado):
`{ "erro": "Dígito verificador inválido no campo 1. Verifique se a linha foi digitada corretamente." }`
Campos obrigatórios ausentes: `{ "nomeBeneficiarioDeclarado": "Nome do beneficiário obrigatório" }`.

## POST `/api/boletos/{boletoId}/explicacao-ia`
Sem corpo. **200**
```json
{
  "explicacao": "Não encontramos sinais de golpe nas verificações que conseguimos fazer. …",
  "geradaPorIa": true,
  "textoRegras": "Não encontramos sinais de golpe nas verificações que conseguimos fazer.\n\nMesmo assim…"
}
```
Sem chave da OpenAI, ou quando a resposta da IA é descartada pelas conferências, `geradaPorIa` é `false` e `explicacao`
traz o texto das regras. A versão da IA fica salva: chamar de novo não consulta a OpenAI outra vez.

## POST `/api/boletos/{boletoId}/confirmar`
Sem corpo. **200**
```json
{ "mensagem": "Confirmação simulada registrada. ESTE NÃO É UM PAGAMENTO REAL.",
  "aviso": "Protótipo acadêmico — nenhuma transação financeira foi realizada." }
```

## POST `/api/boletos/{boletoId}/denunciar`
```json
{ "motivo": "NAO_RECONHECO", "descricao": "Não reconheço esta cobrança." }
```
`motivo`: `NAO_RECONHECO`, `DADOS_DIFERENTES`, `GOLPE_CONFIRMADO` ou `OUTRO`. `descricao` é opcional (até 500 caracteres).

**200** `{ "motivo": "OUTRO", "data": "13/11/2026 17:29", "jaExistia": false }`
(`jaExistia: true` quando o usuário já tinha denunciado esse CPF/CNPJ — nenhuma denúncia nova é criada.)
**400** `{ "motivo": "Escolha o motivo da denúncia" }`

## GET `/api/historico`
**200** — lista; cada item:
```json
{
  "boletoId": "c79b2b8c-aaa1-4827-b7fd-4a01345d1731",
  "analiseId": "1de785b8-e059-4c34-963d-1370f3bdfcce",
  "linhaDigitavel": "23790910162345678901723456000001316260000015000",
  "nomeBeneficiario": "Banco Bradesco S.A.",
  "valor": "R$ 150,00",
  "vencimento": "10/11/2026",
  "score": 0,
  "nivelRisco": "BAIXO",
  "dataSubmissao": "2026-11-13T17:29:38.398189",
  "tipoEntrada": "PDF",
  "origem": "EMAIL",
  "pago": true,
  "explicacao": "…",
  "explicacaoGeradaPorIa": true,
  "denunciado": true
}
```

## GET `/api/instituicoes`
**200** — 39 instituições, em ordem alfabética:
```json
[ { "codigo": "085", "nome": "Ailos (cooperativas de crédito)" }, { "codigo": "461", "nome": "Asaas" }, … ]
```
