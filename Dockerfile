# Baut die komplette Anwendung: Web-App (Flutter) + Backend (Spring Boot) in EIN Image.
# Start:  docker compose up -d --build   ->  http://localhost:8080
FROM ghcr.io/cirruslabs/flutter:stable AS web
WORKDIR /src/app
COPY app/ .
RUN flutter create . --platforms=web --org de.frauas --project-name girokonto_app > /dev/null \
 && flutter pub get \
 && dart run tool/setup_platforms.dart \
 && flutter build web --release --no-web-resources-cdn

FROM eclipse-temurin:21-jdk AS jar
WORKDIR /src/backend
COPY backend/ .
COPY --from=web /src/app/build/web src/main/resources/static
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -q

FROM eclipse-temurin:21-jre
COPY --from=jar /src/backend/build/libs/girokonto-backend-0.1.0.jar /app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
