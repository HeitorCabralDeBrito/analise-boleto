# Preparar a sua máquina para testar o Boleto Seguro (Checkpoint 3)

> Protótipo acadêmico — nenhuma transação financeira real. Leva uns 20 minutos na primeira vez.

## 1. O que precisa estar instalado

| Programa | Para quê | Obrigatório? |
|---|---|---|
| **Git** | baixar o código | sim |
| **Java 21** (JDK) | rodar o sistema — https://adoptium.net/temurin/releases/?version=21 | sim |
| **PostgreSQL** 16 ou mais novo | banco de dados | sim |
| **Tesseract OCR 5** com idioma **Português** | ler fotos e prints de boletos | recomendado (sem ele, PDF e código de barras ainda funcionam) |
| **Python 3** | rodar o teste rápido automático (`teste_fumaca.py`) | opcional |
| **Google Chrome** | navegador dos testes | recomendado |

**Tesseract:** instalador para Windows em https://github.com/UB-Mannheim/tesseract/wiki. Na instalação, em
*Additional language data*, marque **Portuguese**. Se você já tem o Tesseract, confira o idioma no PowerShell:
```powershell
& "C:\Program Files\Tesseract-OCR\tesseract.exe" --list-langs     # precisa aparecer "por"
```
Faltando o `por`, baixe `por.traineddata` em https://github.com/tesseract-ocr/tessdata e copie para
`C:\Program Files\Tesseract-OCR\tessdata\`.

## 2. Pegar o código

Na pasta onde você já tem o projeto (`analise-boleto`):
```powershell
git fetch origin
git checkout checkpoint3
git pull
```
Primeira vez, sem o projeto: `git clone <endereço do repositório>` e depois os comandos acima dentro da pasta.

## 3. Banco de dados

**Recomendado: um banco novo e vazio.** As tabelas são criadas sozinhas na primeira execução (Flyway).
No pgAdmin (Query Tool no banco `postgres`) ou no SQL Shell:
```sql
CREATE DATABASE boleto_seguro_cp3;
```
> Também funciona com o banco antigo do Checkpoint 2, desde que ele esteja na última versão do CP2. Se ao iniciar
> aparecer erro de **"Schema-validation"**, use o banco novo acima.

## 4. Sua configuração local (`config/application.properties`)

Os segredos ficam num arquivo **só seu**, fora do Git:

1. Copie `config/application.properties.example` para `config/application.properties`.
2. Preencha:
   ```properties
   # Senha do SEU usuário postgres
   spring.datasource.password=SUA_SENHA

   # Seu banco e porta (a porta padrão do PostgreSQL é 5432)
   spring.datasource.url=jdbc:postgresql://localhost:5432/boleto_seguro_cp3

   # Qualquer texto aleatório com 32+ caracteres. Para gerar no PowerShell:
   #   [Convert]::ToBase64String((1..48 | % { Get-Random -Max 256 }))
   boleto.seguro.jwt.secret=COLE_AQUI_O_TEXTO_GERADO

   # Só se o Tesseract estiver em outra pasta:
   # boleto.seguro.tesseract.caminho=C:/Program Files/Tesseract-OCR/tesseract.exe
   ```
3. **Nunca** coloque senha no `src/main/resources/application.properties` (esse vai para o Git).

## 5. Opcionais

- **Tema acadêmico Bradesco:** os logotipos **não estão no Git** (uso acadêmico, sem autorização para publicar).
  Peça ao Bruno a pasta `marca-academica` (por mensagem privada) e coloque em `analise-boleto/marca-academica/`.
  Sem ela, o sistema usa só o tema próprio — o resto funciona igual.
- **IA (OpenAI):** sem chave tudo funciona, e a explicação vem "das regras". Para testar a IA, use **a sua própria chave**
  (não compartilhem chaves em grupo). No PowerShell, em qualquer pasta:
  ```powershell
  setx OPENAI_API_KEY (Read-Host "Cole a chave e aperte Enter")
  ```
  Feche e abra o terminal depois.

## 6. Rodar

```powershell
cd analise-boleto
.\mvnw.cmd test               # testes automáticos: devem passar todos (0 falhas)
.\mvnw.cmd spring-boot:run    # sobe o sistema
```
Quando aparecer `Started BradescoAnaliseboletoApplication`, abra http://localhost:8080.

Usuários de demonstração (fictícios):

| Agência | Conta | Senha |
|---|---|---|
| 0001 | 12345-6 | senha123 |
| 0001 | 98765-4 | senha456 |
| 0002 | 11111-1 | teste123 |

**Teste rápido automático** (opcional, com o sistema rodando, em outro terminal):
```powershell
python docs\testes\teste_fumaca.py
```
Deve terminar com `12/12 verificações OK` (a leitura de foto precisa do Tesseract; a consulta de CNPJ, de internet).

## 7. Testar e mandar o resultado

Siga o **`docs/testes/ROTEIRO_DE_TESTES.md`**. Boletos fictícios para começar estão em `docs/testes/amostras/`;
depois use boletos reais seus (sem colocar no Git nem mandar em grupo).

Para devolver: copie o roteiro, marque `[x]` no que testou, escreva **OK** ou o problema ao lado de cada item, e mande
junto os prints do que deu errado (sem dados pessoais aparecendo).

## Problemas comuns

| Mensagem | Solução |
|---|---|
| `Chave do login ausente ou curta demais` | Falta `boleto.seguro.jwt.secret` (32+ caracteres) no `config/application.properties` |
| `password authentication failed` | Senha do PostgreSQL errada no `config/application.properties` |
| `Connection refused` | PostgreSQL desligado ou porta errada na `spring.datasource.url` |
| `Schema-validation` | Use um banco novo e vazio (passo 3) |
| `Port 8080 was already in use` | Já tem algo na porta 8080 (outra cópia do sistema aberta) |
| Fotos não são lidas, PDF sim | Tesseract não instalado ou sem o idioma `por` (passo 1) |
