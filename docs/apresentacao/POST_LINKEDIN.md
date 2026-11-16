# Rascunho de post para o LinkedIn

> Ajuste o texto à sua voz, marque os colegas e a universidade, e anexe 2–3 imagens de `docs/imagens/`
> (somente o tema próprio — não publique imagens com a marca Bradesco) ou o vídeo.

---

🔒 **Boleto Seguro — análise de risco de boletos antes do pagamento**

No projeto A3 de Ciência da Computação da Universidade Anhembi Morumbi, desenvolvemos um protótipo que ajuda a
identificar boletos falsos **antes** de a pessoa pagar.

Como funciona:
📷 A pessoa fotografa ou envia o boleto (imagem ou PDF). O sistema lê o código de barras e os dados impressos.
✅ Ela confere cada dado — o sistema mostra de onde cada informação veio e nada é analisado sem confirmação.
📊 Uma análise por regras gera uma nota de risco de 0 a 100: compara valor e vencimento com o código de barras, valida
o CPF/CNPJ, consulta dados públicos do CNPJ na Receita Federal e considera denúncias de outros usuários.
💬 O resultado vem explicado em linguagem simples, com apoio de IA — sem enviar dados pessoais.
♿ Acessível: teclado, leitor de tela, alto contraste, texto ampliado e leitura em voz alta.

O que aprendemos:
• Regras determinísticas para decidir e IA só como apoio: o resultado é explicável e não depende de créditos nem de internet.
• Privacidade desde o desenho: o arquivo do boleto não é guardado, e a IA não recebe nome nem CPF/CNPJ.
• Acessibilidade se verifica com teste, não com promessa: auditoria automática, navegação só por teclado e leitor de tela.

Tecnologias: Java 21 · Spring Boot · PostgreSQL · Flyway · PDFBox · ZXing · Tesseract OCR · BrasilAPI · OpenAI ·
Bootstrap · JUnit (104 testes) · Docker

Projeto acadêmico, sem movimentação financeira real.

#Java #SpringBoot #Segurança #PrevencaoAFraudes #Acessibilidade #OCR #InteligenciaArtificial #AnhembiMorumbi
