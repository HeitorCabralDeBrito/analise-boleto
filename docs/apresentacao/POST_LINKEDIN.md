# Post para o LinkedIn

> Texto final. Anexe uma imagem só do tema próprio (nunca com a marca Bradesco). Ao colar no LinkedIn, refaça cada
> marcação digitando `@` e escolhendo a pessoa ou página na lista.

---

🔒 Boleto Seguro: como saber se um boleto é golpe antes de pagar?

Esse foi o desafio do nosso projeto A3 de Ciência da Computação na @Universidade Anhembi Morumbi, em atividade acadêmica com o Bradesco. Junto com @Heitor Cabral de Brito e @Miguel Antonio dos Santos Silva, e com a orientação da professora @Cassilene Assis, desenvolvemos um protótipo que analisa o risco de um boleto antes do pagamento.

Como funciona:
📷 A pessoa fotografa ou envia o boleto (imagem ou PDF). O sistema lê o código de barras e os dados impressos.
✅ Ela confere cada dado. O sistema mostra de onde veio cada informação, e nada é analisado sem essa confirmação.
📊 Uma análise por regras dá uma nota de risco de 0 a 100. Ela compara o valor e o vencimento impressos com os gravados no código de barras, valida o CPF/CNPJ, consulta os dados públicos do CNPJ na Receita Federal, confere o banco do boleto e considera denúncias de outros usuários.
💬 O resultado vem explicado em linguagem simples, com apoio de IA, sem enviar nome nem CPF/CNPJ para ela.
♿ É acessível: navegação por teclado, leitor de tela, alto contraste, texto ampliado e leitura em voz alta.

O que aprendemos:
• As regras decidem e a IA só ajuda a explicar. Assim o resultado é explicável e não depende de internet nem de créditos.
• Privacidade desde o começo do projeto: o arquivo do boleto não é guardado, e a IA não recebe dados pessoais.
• Acessibilidade se comprova com teste: auditoria automática, uso só pelo teclado e leitor de tela.
• Saber o que o sistema não faz também é importante. Ele não garante quem recebe o dinheiro; isso só a base oficial de boletos registrados confirmaria.

Tecnologias: Java 21 · Spring Boot · PostgreSQL · Flyway · PDFBox · ZXing · Tesseract OCR · BrasilAPI · OpenAI · Bootstrap · JUnit (104 testes automatizados) · Docker

Projeto acadêmico, sem movimentação financeira real.

#Java #SpringBoot #PostgreSQL #DesenvolvimentoDeSoftware #CienciaDaComputacao #SegurancaDaInformacao #PrevencaoAFraudes #Golpes #OCR #InteligenciaArtificial #Acessibilidade #UX #Fintech #ProjetoAcademico #AnhembiMorumbi
