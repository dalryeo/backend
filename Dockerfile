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

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","app.jar"]
