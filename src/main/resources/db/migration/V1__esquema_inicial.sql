-- Esquema do Checkpoint 2, extraído do PostgreSQL real (pg_dump --schema-only) em 13/11/2026.
-- Em um banco que já tinha essas tabelas (criadas pelo Hibernate), o Flyway NÃO executa este arquivo:
-- ele marca a versão 1 como linha de base (baseline) e segue para as próximas migrações.
-- Em um banco novo (nuvem, outro computador), este arquivo cria tudo do zero.

CREATE TABLE usuarios (
    id uuid NOT NULL,
    agencia varchar(10) NOT NULL,
    conta varchar(20) NOT NULL,
    cpf varchar(14),
    data_criacao timestamp(6) NOT NULL,
    nome varchar(100) NOT NULL,
    senha_hash varchar(255) NOT NULL,
    CONSTRAINT usuarios_pkey PRIMARY KEY (id),
    CONSTRAINT uk_usuarios_agencia_conta UNIQUE (agencia, conta)
);

CREATE TABLE boletos (
    id uuid NOT NULL,
    data_submissao timestamp(6) NOT NULL,
    documento_beneficiario_declarado varchar(20),
    linha_digitavel varchar(54) NOT NULL,
    nome_beneficiario_declarado varchar(200),
    origem varchar(20),
    tipo_entrada varchar(20) NOT NULL,
    valor_declarado numeric(38, 2),
    vencimento_declarado date,
    usuario_id uuid NOT NULL,
    CONSTRAINT boletos_pkey PRIMARY KEY (id),
    CONSTRAINT boletos_origem_check CHECK (origem IN ('WHATSAPP', 'EMAIL', 'SITE', 'IMPRESSO', 'OUTRO')),
    CONSTRAINT boletos_tipo_entrada_check CHECK (tipo_entrada IN ('MANUAL', 'IMAGEM', 'PDF')),
    CONSTRAINT fk7crocx5aw0plo7i46lj97x2xl FOREIGN KEY (usuario_id) REFERENCES usuarios (id)
);

CREATE TABLE analises (
    id uuid NOT NULL,
    confirmacao_simulada boolean,
    data_analise timestamp(6) NOT NULL,
    justificativa_ia text,
    nivel_risco varchar(10) NOT NULL,
    score integer NOT NULL,
    status varchar(20) NOT NULL,
    verificacoes json,
    boleto_id uuid NOT NULL,
    explicacao_resultado text,
    CONSTRAINT analises_pkey PRIMARY KEY (id),
    CONSTRAINT uk1w9wqryqh8biehy3ejrvh6k10 UNIQUE (boleto_id),
    CONSTRAINT analises_nivel_risco_check CHECK (nivel_risco IN ('BAIXO', 'MEDIO', 'ALTO')),
    CONSTRAINT analises_status_check CHECK (status IN ('CONCLUIDA', 'FALHA', 'BLOQUEADA')),
    CONSTRAINT fk2qsxm7ctboh08rbggyxaeh2qf FOREIGN KEY (boleto_id) REFERENCES boletos (id)
);

CREATE TABLE alertas (
    id uuid NOT NULL,
    data_alerta timestamp(6) NOT NULL,
    mensagem text NOT NULL,
    nivel varchar(10) NOT NULL,
    tipo varchar(50) NOT NULL,
    analise_id uuid NOT NULL,
    CONSTRAINT alertas_pkey PRIMARY KEY (id),
    CONSTRAINT alertas_nivel_check CHECK (nivel IN ('INFO', 'AVISO', 'CRITICO')),
    CONSTRAINT fk8c3fe3x355xpul8312i0xeyun FOREIGN KEY (analise_id) REFERENCES analises (id)
);
