FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle gradle/
RUN chmod +x gradlew

COPY build.gradle settings.gradle ./
RUN ./gradlew --no-daemon dependencies || true

COPY . .
RUN ./gradlew --no-daemon clean bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

ARG AI_AGENT_VERSION=3.7.7

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends curl ca-certificates; \
    rm -rf /var/lib/apt/lists/*; \
    curl -fL --retry 5 --retry-delay 2 \
      -o /app/applicationinsights-agent-${AI_AGENT_VERSION}.jar \
      "https://github.com/microsoft/ApplicationInsights-Java/releases/download/${AI_AGENT_VERSION}/applicationinsights-agent-${AI_AGENT_VERSION}.jar"

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-javaagent:/app/applicationinsights-agent-3.7.7.jar","-jar","app.jar"]