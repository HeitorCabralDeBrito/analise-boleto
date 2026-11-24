# Boleto Seguro — Publicar no Render

> Protótipo acadêmico — nenhuma transação financeira real.

O repositório já tem tudo o que o Render precisa:

| Arquivo | Para quê |
|---|---|
| `Dockerfile` | Monta a imagem: Java 21 + Tesseract (OCR em português), roda sem privilégios |
| `render.yaml` | "Blueprint": cria o site e o banco PostgreSQL de uma vez, já ligados |
| `src/main/resources/application.properties` | Configuração **sem segredos**; tudo sensível vem de variáveis de ambiente |
| `src/main/resources/db/migration` | Migrações Flyway: o banco da nuvem é criado vazio e as tabelas surgem sozinhas |

## O que funciona na nuvem

| Funcionalidade | Na nuvem |
|---|---|
| Login, análise, histórico, confirmação, denúncias | ✅ |
| Leitura de PDF (texto) e de código de barras | ✅ |
| OCR (Tesseract) de fotos e prints | ✅, porém **mais lento** no plano gratuito (pouca CPU) |
| Consulta de CNPJ (BrasilAPI), instituição pelo código | ✅ |
| IA (leitura difícil com `gpt-4o` e explicação reescrita com `gpt-4o-mini`) | ✅ se `OPENAI_API_KEY` estiver em **Environment** (veja os problemas comuns) |
| Acessibilidade, voz, temas, celular (câmera) | ✅ (HTTPS do Render ajuda a câmera do celular) |
| **Tema acadêmico Bradesco** | ✅ os logotipos (`marca-academica/`) vão na imagem; uso acadêmico autorizado pela professora, só para a apresentação. O site fica no ar só durante o período de apresentação |

## Limites do plano gratuito (confira em https://render.com/docs/free)

- O site **"dorme" depois de 15 minutos sem acesso**; o primeiro acesso depois disso demora cerca de 1 minuto.
  → Antes de apresentar, abra o site uns minutos antes.
- O **banco gratuito expira 30 dias depois de criado** (depois disso há 14 dias para fazer upgrade, senão ele é apagado).
  → Crie o banco perto da data de apresentação.
- 512 MB de memória e pouca CPU: o app já está configurado para isso (uma leitura de boleto por vez, tempos maiores para o OCR).

## Passo a passo

### 1. Código no GitHub
O Render publica a partir de um repositório do GitHub. O caminho mais simples é o **seu repositório próprio**:
```powershell
cd "C:\Users\Windows 11\Desktop\A3Bradesco\analise-boleto"
git remote rename origin heitor
git remote add origin https://github.com/SEU-USUARIO/boleto-seguro.git
git push -u origin checkpoint3:main
```
Antes do push, confira que nada sensível vai junto (deve aparecer **só** o `.example` e o `src/main/resources/application.properties`, que não tem segredos):
```powershell
git ls-files | Select-String "application.properties"
```

### 2. Criar no Render
1. Entre em https://dashboard.render.com e conecte sua conta do GitHub.
2. **New → Blueprint** → escolha o repositório `boleto-seguro` (branch `main`).
3. O Render lê o `render.yaml` e mostra o site `boleto-seguro` e o banco `boleto-seguro-db`. Ele pede o valor de
   **`OPENAI_API_KEY`**: cole a chave (ou deixe vazio para rodar sem IA).
4. **Apply**. O primeiro build demora alguns minutos (baixa dependências e instala o Tesseract).

### 3. Conferir
- Endereço: `https://boleto-seguro.onrender.com` (ou o nome que o Render mostrar).
- Login com Ag `0001` / Conta `12345-6` / Senha `senha123` (usuários de demonstração criados no primeiro início).
- Teste de fumaça automático (na sua máquina):
  ```powershell
  python "C:\Users\Windows 11\Desktop\Boleto_Seguro_Checkpoint3\06_TESTES\teste_fumaca.py" https://boleto-seguro.onrender.com
  ```
- Depois, a seção 10 do roteiro de testes (`06_TESTES\ROTEIRO_DE_TESTES.md`), inclusive pelo celular no 4G.

### 4. Logs e problemas comuns
No painel do serviço → **Logs**.

| Mensagem | O que fazer |
|---|---|
| `Chave do login ausente ou curta demais` | Em Environment, `JWT_SECRET` precisa existir (o Blueprint gera sozinho) |
| `Connection refused` / erro de banco | Banco ainda criando ou expirado (plano gratuito: 30 dias) |
| Leitura de foto demorando ou "tempo limite" | Normal no plano gratuito; PDF e código de barras continuam rápidos |
| `OutOfMemoryError` | Plano gratuito no limite: evite enviar várias fotos grandes ao mesmo tempo |
| `cannot have more than one active free tier database` | O plano gratuito permite **um** banco por conta. Apague o banco antigo (se não servir mais) e clique em **Manual sync** no Blueprint |
| Explicação sempre "das regras", IA nunca responde | Confira em **Environment** se `OPENAI_API_KEY` existe. No nosso deploy o valor digitado na criação do Blueprint não foi salvo; foi preciso **Add variable** → `OPENAI_API_KEY` → **Save, rebuild, and deploy** |
| Erro 502 logo depois de um envio ao GitHub | O Render está trocando de versão (deploy automático a cada push na `main`); espere ficar **Live** |

## Publicação feita (23/11/2026)

- Endereço: https://boleto-seguro-ca8q.onrender.com — teste de fumaça 12/12 OK, explicação gerada pela IA.
- Tempos medidos no plano gratuito: PDF ~6 s; foto nítida ~22 s; foto borrada (IA `gpt-4o` em paralelo com o OCR) ~40–50 s.
  O navegador espera até 130 s; o servidor encerra a leitura em 80 s (`BOLETO_SEGURO_LEITURA_PRAZO_SEGUNDOS`).

## Segurança (o que já está feito)
- Nenhum segredo no Git: senha do banco vem do Render, `JWT_SECRET` é gerada por ele, `OPENAI_API_KEY` é digitada no painel.
- Banco acessível só pela rede interna do Render (`ipAllowList: []`).
- HTTPS automático; respostas de erro sem detalhes internos.
- Arquivos enviados não são gravados: só existem temporários durante o OCR, apagados em seguida.
- Logs sem dados dos boletos (só tipo do arquivo, quantidade de campos lidos e tempo).
- Usuários de demonstração com senhas públicas: é um protótipo; não use dados bancários reais.
