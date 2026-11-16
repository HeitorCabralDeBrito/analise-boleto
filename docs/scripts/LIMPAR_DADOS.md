# Limpar os dados de teste do banco

Os testes (manuais e automáticos de ponta a ponta) deixam análises, boletos, denúncias e consultas de CNPJ no banco.
Antes do vídeo ou da apresentação, limpe para a tela inicial e o histórico começarem do zero.
Os **usuários de demonstração continuam** e as tabelas também (só ficam vazias).

## 1. Backup (recomendado)

**pgAdmin:** botão direito no banco `boleto_seguro` → **Backup…** → escolha um nome de arquivo (ex.: `antes-da-limpeza.backup`) → **Backup**.

**Ou pelo PowerShell** (troque a porta se a sua não for 5433; ele pede a senha do postgres):
```powershell
& "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe" -h localhost -p 5433 -U postgres -d boleto_seguro -Fc -f "$HOME\Desktop\antes-da-limpeza.dump"
```
Arquivos `.dump` ficam fora do Git (estão no `.gitignore`), mas guarde-os **fora da pasta do projeto** mesmo assim.

## 2. Limpar

1. Pare o sistema (Ctrl+C no terminal do `spring-boot:run`).
2. pgAdmin → banco `boleto_seguro` → **Query Tool**.
3. Abra `docs/scripts/limpar_dados_de_teste.sql` (ou cole o conteúdo) → **F5**.
4. O resultado final deve mostrar `usuarios = 3` e todo o resto `0`.
5. Suba o sistema de novo.

## 3. Se precisar voltar atrás

```powershell
& "C:\Program Files\PostgreSQL\18\bin\pg_restore.exe" -h localhost -p 5433 -U postgres -d boleto_seguro --clean "$HOME\Desktop\antes-da-limpeza.dump"
```
