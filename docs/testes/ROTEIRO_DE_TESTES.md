# Boleto Seguro — Roteiro de testes (Checkpoint 3)

**Testador:** ____________ **Data:** ___/___/____ **Máquina / navegador:** ____________ **Celular:** ____________

Antes de começar: [`PREPARAR_AMBIENTE.md`](PREPARAR_AMBIENTE.md). Marque `[x]` em cada item testado e anote ao lado
**OK** ou o que aconteceu (com print, sem dados pessoais aparecendo, se der errado).

> **Boletos reais podem ser usados à vontade** (seus ou de quem autorizou): a leitura roda no seu próprio computador.
> Só não coloque os arquivos no Git nem em grupos. Com a IA ligada, a imagem só vai para a OpenAI quando a leitura
> local falha; a explicação reescrita pela IA nunca recebe nome nem CPF/CNPJ.
>
> **Boletos fictícios prontos** (não pagáveis) em `docs/testes/amostras/`:
> `1_boleto_textual.pdf`, `2_boleto_nitido.png`, `3_boleto_ruim.jpg`, `4_boleto_sem_codigo.png`, `5_arquivo_falso.png`.

---

## 1. Boletos para separar

Numa pasta **fora do projeto** (ex.: `Desktop\boletos-teste\`):

**PDFs:** boleto digital (escola, condomínio, loja) · fatura de cartão com boleto na última página · PDF com senha ·
PDF escaneado (sem texto, só imagem) · um PDF qualquer com mais de 10 páginas

**Fotos de celular (boleto em papel):** boa (reta, iluminada) · celular em pé (girada 90°) · inclinada · tremida ·
escura ou com reflexo · cortada · de longe · na resolução máxima da câmera · foto de iPhone, se houver

**Prints de tela:** boleto aberto no app do banco ou no e-mail · só a linha digitável

**Casos especiais:** conta de luz/água/telefone com linha começando com **8** (arrecadação — o sistema não suporta e deve
recusar) · fatura com valor em aberto · boleto vencido há mais de 60 dias · foto que não é boleto · arquivo de texto
renomeado para `.png`

---

## 2. Login e identidade visual
- [ ] Login com Ag `0001` / Conta `12345-6` / Senha `senha123`
- [ ] Senha errada → mensagem de erro, sem travar
- [ ] Botão de olho mostra/esconde a senha
- [ ] Seletor "Identidade visual" (só aparece com a pasta `marca-academica`) troca o tema sem recarregar
- [ ] Fechar e abrir o navegador → o tema escolhido continua
- [ ] Aviso "Protótipo acadêmico" aparece em todas as telas
- [ ] **Sair** volta ao login; abrir `/home.html` depois disso → volta ao login

## 3. Tela inicial
- [ ] No computador: duas colunas (resumo, últimas análises, dicas). No celular: tudo empilhado
- [ ] Números do resumo e "Suas últimas análises" batem com o histórico

## 4. Importação (Pagar boleto)
Para cada boleto da seção 1, anote: **linha lida? quantos campos? quanto tempo?**
- [ ] `1_boleto_textual.pdf` → 5 campos com selo "Lido do texto do PDF"
- [ ] `2_boleto_nitido.png` → linha "Lido do código de barras · confiável", campos "Lido por OCR · confira"
- [ ] `3_boleto_ruim.jpg` → mensagem amigável com opção de digitar (não trava)
- [ ] `4_boleto_sem_codigo.png` → campos lidos, linha "Não identificado — preencha" e aviso
- [ ] `5_arquivo_falso.png` → "Formato não suportado"
- [ ] PDF digital real → campos com selo "Lido do texto do PDF"
- [ ] Fatura de cartão → acha o boleto na última página
- [ ] PDF com senha → mensagem explicando como tirar a senha
- [ ] PDF escaneado → campos "Lido por OCR · confira"
- [ ] PDF com mais de 10 páginas → recusado com mensagem de limite
- [ ] Foto boa / girada 90° → linha lida
- [ ] Foto ruim / escura / cortada → lê, ou mensagem amigável (nunca erro técnico)
- [ ] Print de baixa resolução → linha lida pelo OCR
- [ ] Conta de consumo (começa com 8), digitada ou por foto → mensagem "boleto de arrecadação… não analisado"
- [ ] Botão **Cancelar** durante a leitura → "Leitura cancelada"
- [ ] Arrastar e soltar o arquivo na área pontilhada (computador)
- [ ] No celular: **Fotografar com a câmera** abre a câmera traseira
- [ ] **Digitar manualmente** → conferência com campos vazios

## 5. Conferência
- [ ] Cada campo mostra de onde veio (selo); os não lidos aparecem como "Não identificado — preencha"
- [ ] Editar um campo lido muda o selo para "Informado por você"
- [ ] Quadro "No código de barras: instituição … · valor … · vencimento …"
- [ ] Campo opcional "Por qual banco essa empresa costuma cobrar?" lista os bancos
- [ ] Analisar **sem marcar** "Conferi os dados…" → não deixa e mostra o aviso
- [ ] Linha com um número errado → análise bloqueada com explicação
- [ ] CPF/CNPJ com dígito errado → aviso, mas deixa analisar (e a análise aponta o risco)
- [ ] **Voltar** limpa os dados e volta para a importação
- [ ] No celular: a linha digitável aparece inteira (em duas linhas)

## 6. Análise e resultado
Linha de teste: `23790.91016 23456.789017 23456.000001 3 16260000015000` (R$ 150,00, vencimento 10/11/2026, Bradesco)

| | Cenário | Esperado | OK? |
|---|---|---|---|
| A | `Maria da Silva` / CPF `529.982.247-25` | nota 0, risco baixo | |
| B | A + origem WhatsApp | +15 | |
| C | linha `12345678901234567890123456789012345678901234567` | bloqueada antes da análise | |
| E | A + valor diferente de 150,00 | +40 | |
| F1 | CPF `529.982.247-26` | +50, risco médio | |
| F2 | `Condomínio Edifício Primavera` + CPF válido | +30 | |
| F3 | `Banco Bradesco S.A.` + CNPJ `60.746.948/0001-12` | CNPJ ativo, nome confere, 0 pontos | |
| F4 | `Energia Paulista S.A.` + CNPJ `11.222.333/0001-81` | "nome diferente do cadastro", +20 | |
| G | A + banco esperado "Itaú Unibanco (341)" | "instituição diferente da esperada", +15 | |
| H | E + F1 juntos | risco ALTO, botão vermelho "Prosseguir mesmo assim" | |

- [ ] Cartão "Consulta pública do CNPJ" (F3, F4) com razão social, situação e o aviso "não comprovam que este boleto é dela"
- [ ] "Instituição indicada no código: Bradesco (237)"
- [ ] Sem internet → CNPJ aparece como "Consulta indisponível" e a análise continua
- [ ] "O que isso significa": faz sentido para quem não entende de boleto
- [ ] Com IA configurada: o texto troca para "reescrita por IA a partir das regras" e "Ver o texto original" mostra o das regras
- [ ] No risco alto, a explicação da IA começa com "Não pague este boleto."

## 7. Denunciar boleto (precisa de 3 usuários)
Use um **mesmo CPF** de beneficiário inventado nos três usuários (ex.: `111.444.777-35`):
- [ ] Usuário 3 analisa → "Denúncias de outros usuários: Passou"
- [ ] Usuário 1 analisa e **denuncia** (resultado ou histórico) → "Você denunciou este beneficiário…"
- [ ] Denunciar de novo o mesmo CPF → não cria outra denúncia
- [ ] Usuário 3 analisa → "Atenção" (1 denúncia), **sem** pontos
- [ ] Usuário 2 analisa e denuncia → usuário 3 analisa → "Falhou", **+25**
- [ ] No histórico de quem denunciou: selo "Denunciado por você"
- [ ] Ninguém vê **quem** denunciou nem o texto escrito

## 8. Confirmação e histórico
- [ ] Confirmar (simulado) → mensagem de sucesso
- [ ] Histórico: selos "Pago" / "Pago apesar do risco alto" / "Não pago"
- [ ] Filtros (situação, risco) e busca funcionam
- [ ] "Ver explicação" abre o texto salvo
- [ ] Outro usuário **não** vê os boletos do primeiro

## 9. Celular e responsividade
- [ ] Acessar pelo celular na mesma rede (`http://IP-do-notebook:8080`)
      *(se o Wi-Fi bloquear, use o roteador do celular e conecte o notebook nele)*
- [ ] Menu ☰ abre e fecha; tocar fora fecha
- [ ] Nenhuma tela precisa de rolagem para o lado
- [ ] Zoom do navegador em 200% no computador → tudo utilizável

## 10. Acessibilidade
- [ ] Site inteiro **só com o teclado** (Tab, Shift+Tab, Enter, Espaço, Esc); contorno de foco sempre visível
- [ ] Primeiro Tab de cada tela mostra "Pular para o conteúdo"
- [ ] **A− / A / A+** mudam o texto em todas as telas e a escolha continua depois de recarregar
- [ ] **Alto contraste** (meio círculo) deixa tudo preto e branco; desligar volta ao tema
- [ ] **Ouvir esta página** (alto-falante no topo, em todas as telas e no celular) lê a tela dizendo o que é cada item;
      **Esc** ou o mesmo botão param
- [ ] **Ler ao apontar** (ícone de mão) ligado: ao passar o mouse, fala botões, textos e um boleto inteiro do histórico
- [ ] Nada fala sozinho ao abrir uma página
- [ ] No celular, as preferências ficam dentro do menu ☰
- [ ] Página **Acessibilidade** abre também sem login (link na tela de entrada)

**Leitor de tela NVDA (Windows, gratuito — https://www.nvaccess.org/download/):** liga com **Ctrl+Alt+N**, desliga com
**Insert+Q**; **Tab** avança, **H** pula de título em título, **Insert+↓** lê tudo, **Ctrl** cala.
- [ ] Login: fala "Agência", "Conta", "Senha"; com campo vazio, fala o erro
- [ ] Depois de importar: fala "Leitura concluída. X de 5 campos identificados…"
- [ ] Conferência: fala o nome do campo **e** de onde veio ("Lido por OCR, confira")
- [ ] Resultado: cada verificação diz "Passou", "Falhou", "Atenção" ou "Não verificado"

**TalkBack (Android), se possível:** Configurações → Acessibilidade → TalkBack. Mesmo roteiro do NVDA.

## 11. Falhas e limites
- [ ] **Sem internet** (desligue o Wi-Fi do notebook com o sistema aberto e recarregue): as telas continuam com o visual normal
- [ ] Desligar o Wi-Fi do celular no meio da leitura → mensagem de falha de conexão
- [ ] Enviar 3 arquivos ao mesmo tempo (3 abas) → nenhum trava; no máximo um pede para tentar de novo
- [ ] Sem o Tesseract → PDFs digitais e fotos com código de barras ainda funcionam
- [ ] Sem chave da OpenAI → tudo funciona e a explicação vem "das regras"

---

**Resumo:** total de itens testados ____ · OK ____ · com problema ____

**Problemas encontrados** (o que fez, o que esperava, o que aconteceu):

1.
2.
3.
