-- Boleto Seguro — apaga os DADOS DE TESTE (análises, boletos, alertas, denúncias e cache de CNPJ).
-- Mantém: usuários de demonstração e o controle de migrações do Flyway (as tabelas continuam lá, vazias).
--
-- Quando usar: antes de gravar o vídeo, antes de apresentar ou antes de levar o banco para a nuvem.
-- ANTES, faça um backup (pgAdmin: botão direito no banco > Backup…; ou o comando em docs/scripts/LIMPAR_DADOS.md).
--
-- Como rodar no pgAdmin: abra o banco boleto_seguro > Query Tool > cole este arquivo inteiro > F5.
-- Tudo roda numa transação: se qualquer linha falhar, nada é apagado.

BEGIN;

-- Ordem: primeiro quem aponta para outras tabelas (chaves estrangeiras)
DELETE FROM denuncias;
DELETE FROM alertas;
DELETE FROM analises;
DELETE FROM boletos;
DELETE FROM consulta_cnpj_cache;

COMMIT;

-- Conferência: tudo zero, menos usuarios (3 de demonstração)
SELECT 'usuarios' AS tabela, count(*) FROM usuarios
UNION ALL SELECT 'boletos', count(*) FROM boletos
UNION ALL SELECT 'analises', count(*) FROM analises
UNION ALL SELECT 'alertas', count(*) FROM alertas
UNION ALL SELECT 'denuncias', count(*) FROM denuncias
UNION ALL SELECT 'consulta_cnpj_cache', count(*) FROM consulta_cnpj_cache;
