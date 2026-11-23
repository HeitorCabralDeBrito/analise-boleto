# Boleto Seguro — imagem para a nuvem (Render) ou para testar localmente com Docker.
# Etapa 1 compila o projeto; etapa 2 tem só o Java + Tesseract (OCR em português) e roda sem privilégios.

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
# Dependências primeiro: ficam em cache enquanto só o código muda
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Os testes rodam antes do deploy (mvnw test), não dentro da imagem
RUN ./mvnw -q -B -DskipTests package

FROM eclipse-temurin:21-jre
# Tesseract com o idioma português (leitura dos campos impressos) e fontes para o PDFBox desenhar páginas
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-por fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 1001 --no-create-home boleto
WORKDIR /app
COPY --from=build /src/target/*.jar app.jar
# Logotipos do tema acadêmico Bradesco (uso acadêmico autorizado pela professora; só para a apresentação)
COPY marca-academica marca-academica
USER boleto

ENV TESSERACT_CAMINHO=/usr/bin/tesseract \
    JAVA_OPTS="-XX:MaxRAMPercentage=55 -XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -Xss512k -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
