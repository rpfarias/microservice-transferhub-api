# syntax=docker/dockerfile:1

# ============================================================================
# Stage 1: BUILD — JDK + Maven completos. Nada disto vai para a imagem final.
# Uso a imagem oficial do Maven (em vez do mvnw) para evitar problemas de
# line-ending/permissão do wrapper quando o build roda a partir do Windows.
# ============================================================================
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Camada de dependências primeiro: enquanto o pom.xml não mudar, o Docker
# reaproveita este passo do cache — rebuilds ficam rápidos.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -q dependency:go-offline

COPY src ./src
# Testes rodam no CI (Etapa 13), não no build da imagem: o build precisa ser
# reproduzível e rápido; Testcontainers dentro de docker build nem funcionaria.
RUN --mount=type=cache,target=/root/.m2 mvn -q package -DskipTests

# ============================================================================
# Stage 2: EXTRACT — separa o fat-jar nas camadas do Spring Boot.
# dependencies/ muda raramente; application/ muda a cada commit. Em camadas
# separadas, um push/pull só transfere o que mudou.
# ============================================================================
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ============================================================================
# Stage 3: RUNTIME — só JRE + as camadas. Sem compilador, sem código-fonte,
# sem Maven: imagem menor e menos superfície de ataque.
# ============================================================================
FROM eclipse-temurin:21-jre-alpine
# Usuário não-root: container rodando como root é finding clássico de auditoria.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring
WORKDIR /app

COPY --from=extract /app/extracted/dependencies/ ./
COPY --from=extract /app/extracted/spring-boot-loader/ ./
COPY --from=extract /app/extracted/snapshot-dependencies/ ./
COPY --from=extract /app/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
