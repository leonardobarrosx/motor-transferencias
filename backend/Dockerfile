# syntax=docker/dockerfile:1

# ---- build: compila e extrai as camadas do jar (dependências mudam menos que o código)
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/motor-transferencias-*.jar extract --layers --launcher --destination extracted

# ---- runtime: só o JRE, usuário sem privilégio
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S motor && adduser -S motor -G motor
USER motor
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
EXPOSE 8080
# MaxRAMPercentage: respeita o limite de memória do container (Render/Railway)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
