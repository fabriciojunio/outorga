# Build em duas etapas: o jar sai de uma imagem com Maven, e a imagem final
# carrega so o runtime. Sem isso a imagem passaria de 700 MB e a camada
# gratuita de qualquer provedor reclamaria do tempo de deploy.
FROM maven:3.9-eclipse-temurin-21 AS construcao
WORKDIR /app

# As dependencias mudam muito menos que o codigo. Copiar o pom primeiro faz o
# cache do Docker aproveitar o download inteiro entre builds.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# maven.test.skip em vez de skipTests: alem de nao rodar, nem compila o teste,
# o que evita baixar o binario do Postgres embutido dentro da imagem.
RUN mvn -B -q clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine AS execucao
WORKDIR /app

# Usuario sem privilegio. Container que roda como root e um convite: qualquer
# execucao remota vira controle da maquina.
RUN addgroup -S mirante && adduser -S mirante -G mirante
COPY --from=construcao --chown=mirante:mirante /app/target/*.jar aplicacao.jar
USER mirante

EXPOSE 8080

# Ajuste para instancia pequena, do tipo que a camada gratuita oferece:
#   MaxRAMPercentage deixa a JVM enxergar o limite do container em vez de
#   chutar pelo total da maquina hospedeira;
#   SerialGC gasta menos memoria que G1 e, com uma CPU so, nao perde nada;
#   TieredStopAtLevel=1 corta o tempo de subida, que importa em plataforma que
#   dorme e acorda a cada requisicao.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-jar", "aplicacao.jar"]
