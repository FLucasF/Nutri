# NutriPlan — imagem única: a API e a tela no mesmo serviço.
#
# São três estágios. Os dois primeiros compilam e ficam para trás; só o
# terceiro vira a imagem que sobe. O JDK e o Maven pesam mais de 600 MB e não
# fazem falta nenhuma para rodar — carregá-los junto seria pagar download e
# disco em todo deploy por nada.

# ---------------------------------------------------------------- 1. a tela
FROM node:22-alpine AS frontend

WORKDIR /app

# O package.json vem antes do resto do código de propósito: enquanto as
# dependências não mudam, esta camada é reaproveitada do cache e o `npm ci`
# não roda de novo. Copiar tudo de uma vez invalidaria o cache a cada linha
# alterada num componente.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
# `npm run build` roda `tsc -b` antes do Vite: um erro de tipo derruba a
# construção da imagem, em vez de virar um defeito em produção.
RUN npm run build

# ------------------------------------------------------------- 2. o servidor
FROM maven:3.9-eclipse-temurin-21 AS backend

WORKDIR /build

COPY backend/pom.xml ./
# Baixa as dependências numa camada própria, pelo mesmo motivo do npm ci.
RUN mvn -B -q dependency:go-offline

COPY backend/src ./src
# A tela compilada entra no JAR como recurso estático. É daqui que o Spring a
# serve — ver SpaConfig.
COPY --from=frontend /app/dist ./src/main/resources/static

# Os testes não rodam aqui: eles sobem um contexto Spring inteiro por classe e
# levam minutos, e o plano gratuito tem tempo de build limitado. Eles rodam
# antes, na máquina de quem desenvolve, e é lá que uma falha precisa aparecer.
RUN mvn -B -q clean package -DskipTests

# --------------------------------------------------------------- 3. a imagem
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Não roda como root: se algum dia houver uma brecha na aplicação, ela esbarra
# nas permissões de um usuário comum em vez de encontrar a máquina aberta.
RUN addgroup -S nutriplan && adduser -S nutriplan -G nutriplan
USER nutriplan

COPY --from=backend --chown=nutriplan:nutriplan /build/target/*.jar app.jar

EXPOSE 8080

# A JVM enxerga o limite do contêiner e calcula o heap sobre ele. Sem isto,
# num contêiner de 512 MB ela reserva heap demais, o resto da memória não cabe
# e o processo é morto sem aviso — o sintoma é o serviço reiniciando sozinho
# sem nada no log da aplicação.
#
#   MaxRAMPercentage=60  deixa ~40% para metaspace, pilhas e o que é fora do heap
#   SerialGC             um núcleo fracionado não tem o que paralelizar, e o G1
#                        cobra memória por região que aqui não se paga
#   TieredStopAtLevel=1  compila menos e sobe mais rápido, que é o que importa
#                        num serviço que hiberna e precisa voltar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
