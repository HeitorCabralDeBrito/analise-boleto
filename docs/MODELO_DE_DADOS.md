# Modelo de dados (MER / DER)

> Extraído do **PostgreSQL real** em 13/11/2026, depois das migrações Flyway V1, V2 e V3
> (`src/main/resources/db/migration`). O teste `MigracoesBancoTest` confere, a cada execução, que as tabelas criadas
> pelas migrações correspondem exatamente às entidades Java.

## Modelo entidade-relacionamento (conceitual)

```mermaid
erDiagram
    USUARIO ||--o{ BOLETO : "analisa"
    BOLETO ||--o| ANALISE : "recebe"
    ANALISE ||--o{ ALERTA : "gera"
    USUARIO ||--o{ DENUNCIA : "faz"
    BOLETO ||--o{ DENUNCIA : "motiva"
    CONSULTA_CNPJ_CACHE }o..o{ BOLETO : "CNPJ do beneficiário"
```

| Relacionamento | Cardinalidade | Garantia no banco |
|---|---|---|
| Usuário analisa boletos | 1 : 0..N | `boletos.usuario_id` NOT NULL + FK |
| Boleto recebe análise | 1 : 0..1 | `analises.boleto_id` NOT NULL + FK + **UNIQUE** |
| Análise gera alertas | 1 : 0..N | `alertas.analise_id` NOT NULL + FK |
| Usuário faz denúncias | 1 : 0..N | `denuncias.usuario_id` NOT NULL + FK |
| Boleto motiva denúncias | 1 : 0..N | `denuncias.boleto_id` NOT NULL + FK |
| Uma denúncia por usuário e documento | — | **UNIQUE** (`usuario_id`, `documento_beneficiario`) |
| Login único | — | **UNIQUE** (`agencia`, `conta`) |
| Cache de CNPJ ↔ boleto | lógico | sem FK: liga pelo CNPJ (`boletos.documento_beneficiario_declarado` = `consulta_cnpj_cache.cnpj`); o cache é compartilhado por todos os usuários e só guarda dados públicos de empresas |

## Diagrama entidade-relacionamento (físico)

```mermaid
erDiagram
    usuarios ||--o{ boletos : "usuario_id"
    boletos ||--o| analises : "boleto_id (único)"
    analises ||--o{ alertas : "analise_id"
    usuarios ||--o{ denuncias : "usuario_id"
    boletos ||--o{ denuncias : "boleto_id"

    usuarios {
        uuid id PK
        varchar agencia UK "10 - único com conta"
        varchar conta UK "20"
        varchar cpf "14, opcional"
        timestamp data_criacao
        varchar nome "100"
        varchar senha_hash "255, BCrypt"
    }
    boletos {
        uuid id PK
        uuid usuario_id FK
        varchar linha_digitavel "54"
        varchar tipo_entrada "MANUAL, IMAGEM, PDF"
        varchar origem "WHATSAPP, EMAIL, SITE, IMPRESSO, OUTRO"
        varchar nome_beneficiario_declarado "200"
        varchar documento_beneficiario_declarado "20, só dígitos"
        numeric valor_declarado
        date vencimento_declarado
        timestamp data_submissao
        varchar fonte_linha "V2: origem do dado"
        varchar fonte_nome "V2"
        varchar fonte_documento "V2"
        varchar fonte_valor "V2"
        varchar fonte_vencimento "V2"
        timestamp dados_conferidos_em "V2"
        varchar instituicao_esperada "V2, 3 dígitos"
    }
    analises {
        uuid id PK
        uuid boleto_id FK "único"
        integer score "0 a 100"
        varchar nivel_risco "BAIXO, MEDIO, ALTO"
        varchar status "CONCLUIDA, FALHA, BLOQUEADA"
        json verificacoes "nome, estado, descrição, pontos"
        text explicacao_resultado "texto das regras"
        text justificativa_ia "texto reescrito pela IA"
        boolean confirmacao_simulada
        timestamp data_analise
    }
    alertas {
        uuid id PK
        uuid analise_id FK
        varchar tipo "50"
        varchar nivel "INFO, AVISO, CRITICO"
        text mensagem
        timestamp data_alerta
    }
    denuncias {
        uuid id PK
        uuid usuario_id FK
        uuid boleto_id FK
        varchar documento_beneficiario "14, único com usuario_id"
        varchar motivo "NAO_RECONHECO, DADOS_DIFERENTES, GOLPE_CONFIRMADO, OUTRO"
        varchar descricao "500, opcional"
        timestamp data_denuncia
    }
    consulta_cnpj_cache {
        varchar cnpj PK "14"
        boolean encontrado "false = 404 na fonte"
        varchar razao_social "255"
        varchar nome_fantasia "255"
        varchar situacao_cadastral "50"
        date data_situacao_cadastral
        varchar fonte "BrasilAPI"
        timestamp consultado_em "validade do cache: 24 h"
    }
```

## Dicionário de dados

### `usuarios`
Usuários de demonstração (fictícios), criados pelo `DataSeeder` na primeira execução. Senha guardada só como hash BCrypt.

### `boletos`
Cada boleto submetido para análise. Guarda o que o usuário **confirmou** na tela de conferência:

| Coluna | Observação |
|---|---|
| `linha_digitavel` | Normalizada (só dígitos), 47 posições |
| `tipo_entrada` | Como o boleto chegou: digitado, imagem ou PDF |
| `fonte_*` | De onde veio cada dado: `TEXTO_PDF`, `CODIGO_BARRAS`, `OCR_LOCAL`, `IA` ou `USUARIO` (digitado ou corrigido). Nulo quando o campo ficou vazio |
| `dados_conferidos_em` | Momento em que o usuário marcou "Conferi os dados" — nenhuma leitura automática é aceita sem isso |
| `instituicao_esperada` | Código do banco que o usuário esperava (opcional), comparado com o código da linha |

O arquivo enviado (imagem/PDF) **não é guardado** em lugar nenhum.

### `analises`
Resultado do motor de risco: nota, nível, a lista de verificações (JSON) e as duas versões da explicação:
`explicacao_resultado` (gerada pelas regras, sempre presente) e `justificativa_ia` (reescrita pela IA, quando disponível).
`confirmacao_simulada` registra a decisão de "pagar" no protótipo — nenhum valor é movimentado.

### `alertas`
Mensagens de alerta da análise, uma por linha (nível `CRITICO` quando o risco é alto).

### `denuncias`
Denúncia de um usuário contra o CPF/CNPJ do beneficiário de um boleto que **ele** analisou. Outros usuários veem apenas a
**quantidade** de pessoas que denunciaram o documento (nunca quem, nem a descrição).

### `consulta_cnpj_cache`
Última resposta da consulta pública de CNPJ (BrasilAPI / Receita Federal). Evita repetir a consulta e permite mostrar o
último dado conhecido, marcado como **desatualizado**, quando a fonte está fora do ar. **CPF nunca é consultado nem gravado aqui.**

### `flyway_schema_history`
Controle interno do Flyway (versões aplicadas). Não faz parte do modelo de negócio.

## Histórico das migrações

| Versão | Arquivo | Conteúdo |
|---|---|---|
| V1 | `V1__esquema_inicial.sql` | Esquema do Checkpoint 2 (usuários, boletos, análises, alertas) |
| V2 | `V2__consulta_cnpj_e_origem_dos_dados.sql` | `consulta_cnpj_cache` e colunas de origem dos dados, conferência e instituição esperada |
| V3 | `V3__denuncias.sql` | `denuncias` (+ índice por documento) |
