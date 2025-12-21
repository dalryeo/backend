FROM gradle:8.6-jdk17 AS build
WORKDIR /app

COPY build.gradle settings.gradle gradle/ ./

RUN gradle --no-daemon dependencies || true

COPY . .
RUN gradle --no-daemon clean bootJar

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
