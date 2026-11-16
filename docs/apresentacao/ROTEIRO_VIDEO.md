# Roteiro do vídeo — Boleto Seguro (até 10 minutos)

## Antes de gravar (checklist)

- [ ] Limpar os dados de teste do banco (`docs/scripts/LIMPAR_DADOS.md`) — a tela inicial e o histórico começam do zero
- [ ] Sistema rodando (`.\mvnw.cmd spring-boot:run`) com a chave da OpenAI configurada (para mostrar a IA)
- [ ] Internet ligada (consulta de CNPJ e IA); o visual funciona mesmo sem internet
- [ ] Navegador em tela cheia, zoom 100%, sem abas pessoais nem favoritos à mostra; notificações silenciadas
- [ ] Boleto fictício `docs/testes/amostras/2_boleto_nitido.png` **impresso** (para a cena da câmera do celular)
- [ ] Celular na mesma rede do notebook (ou notebook no roteador do celular) com `http://IP-do-notebook:8080` aberto
- [ ] Nenhum dado pessoal real na tela (use só os boletos fictícios e os usuários de demonstração)

## Roteiro

| Tempo | Cena | O que mostrar | Fala (sugestão) |
|---|---|---|---|
| 0:00–0:40 | **Problema** | Slide ou tela inicial | "Golpes de boleto falso são comuns: o boleto chega pelo WhatsApp ou e-mail, parece verdadeiro, mas o dinheiro vai para outra pessoa. O Boleto Seguro analisa o boleto **antes** de pagar." |
| 0:40–1:20 | **Login e temas** | Tela de login; trocar entre tema próprio e Bradesco | "É um protótipo acadêmico, com usuários fictícios. Tem duas identidades visuais com o mesmo código: a nossa e uma inspirada no app do Bradesco, para a atividade." |
| 1:20–1:50 | **Tela inicial** | Painel: resumo, últimas análises, dicas | "A tela inicial mostra o resumo das análises e dicas contra golpes." |
| 1:50–3:20 | **Importar pelo celular** | No celular: Pagar boleto → **Fotografar com a câmera** → foto do boleto impresso → tela Conferir | "O caminho principal é fotografar. O sistema lê o código de barras e também o que está impresso: beneficiário, CNPJ, valor, vencimento. Cada campo diz de onde veio — código de barras é confiável, OCR pede conferência. Nada é analisado sem a pessoa conferir e confirmar." |
| 3:20–4:00 | **PDF no computador** | Importar `1_boleto_textual.pdf`; mostrar os selos "Lido do texto do PDF"; editar um campo → "Informado por você" | "Com PDF, a leitura é instantânea. Se eu corrijo um campo, ele passa a valer como informado por mim." |
| 4:00–5:30 | **Resultado — baixo risco (F3)** | Beneficiário `Banco Bradesco S.A.` + CNPJ `60.746.948/0001-12` → resultado | "Nota zero. A consulta pública mostra que o CNPJ existe, está ativo e o nome confere — mas o sistema avisa: isso não prova que o boleto é da empresa. A explicação aparece em linguagem simples e é reescrita pela IA, sem enviar nome nem CPF." |
| 5:30–6:40 | **Resultado — golpe (H)** | Valor `99,00` + CPF `529.982.247-26` → risco alto | "Agora um boleto adulterado: o valor digitado não bate com o código de barras e o CPF não existe. Risco alto, a explicação começa com 'Não pague este boleto', e o botão vira 'Confirmar mesmo assim'." |
| 6:40–7:20 | **Denunciar** | Botão Denunciar → motivo → enviado; histórico com o selo | "Quem cai num golpe pode denunciar. Outras pessoas que analisarem um boleto com o mesmo CPF/CNPJ vão ver que houve denúncias — sem saber quem denunciou. Uma denúncia só gera atenção; a partir de duas pessoas, aumenta o risco." |
| 7:20–8:40 | **Acessibilidade** | A+ / alto contraste; "Ouvir esta página"; navegação só com Tab; página Acessibilidade | "Foi feito para todos: dá para usar só pelo teclado e com leitor de tela, aumentar o texto, usar alto contraste, e ouvir a página — para quem enxerga com dificuldade." |
| 8:40–9:30 | **Bastidores técnicos** | Slide com a arquitetura (`docs/diagramas/arquitetura.png`) e o modelo de dados | "Java 21 e Spring Boot, PostgreSQL com migrações, leitura com PDFBox, ZXing e Tesseract, consulta pública de CNPJ, IA opcional. São 104 testes automatizados, e as regras de risco são determinísticas: a IA nunca decide." |
| 9:30–10:00 | **Encerramento** | Tela inicial | "O Boleto Seguro mostra como a análise preventiva pode proteger as pessoas antes do pagamento. Obrigado!" |

## Dicas de gravação

- Grave as cenas separadas (computador e celular) e junte depois; é mais fácil regravar só o trecho que errou.
- Para a tela do celular, use o espelhamento do próprio Windows (Vincular ao Celular) ou grave o celular com o gravador de
  tela dele.
- Deixe o mouse parado enquanto fala; mova só para clicar.
- Fale os números de risco em voz alta ("nota zero", "risco alto") — ajuda quem assiste sem ver bem a tela.
