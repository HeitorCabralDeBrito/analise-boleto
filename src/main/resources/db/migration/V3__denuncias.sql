-- Checkpoint 3, Etapa 4b: "Denunciar boleto".
-- Cada usuário pode denunciar um mesmo CPF/CNPJ de beneficiário uma única vez. As outras pessoas veem só a
-- QUANTIDADE de usuários que denunciaram aquele documento, nunca quem denunciou nem o texto da denúncia.
-- Inspirado no compartilhamento de dados de fraude entre instituições (Resolução Conjunta BCB/CMN nº 6/2023);
-- aqui a base é só a deste protótipo.
CREATE TABLE denuncias (
    id uuid NOT NULL,
    usuario_id uuid NOT NULL,
    boleto_id uuid NOT NULL,
    documento_beneficiario varchar(14) NOT NULL,
    motivo varchar(30) NOT NULL,
    descricao varchar(500),
    data_denuncia timestamp(6) NOT NULL,
    CONSTRAINT denuncias_pkey PRIMARY KEY (id),
    CONSTRAINT uk_denuncias_usuario_documento UNIQUE (usuario_id, documento_beneficiario),
    CONSTRAINT denuncias_motivo_check CHECK (motivo IN ('NAO_RECONHECO', 'DADOS_DIFERENTES', 'GOLPE_CONFIRMADO', 'OUTRO')),
    CONSTRAINT fk_denuncias_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
    CONSTRAINT fk_denuncias_boleto FOREIGN KEY (boleto_id) REFERENCES boletos (id)
);

CREATE INDEX idx_denuncias_documento ON denuncias (documento_beneficiario);
