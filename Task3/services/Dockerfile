# Простая runtime-сборка из готового fat-jar (собирается локально:
#   cd services && mvn -q package -DskipTests)
#
# Сборка образов:
#   docker build -t service-a:latest -f Dockerfile --build-arg SERVICE=service-a services/
#   docker build -t service-b:latest -f Dockerfile --build-arg SERVICE=service-b services/
FROM eclipse-temurin:21-jre
ARG SERVICE=service-a
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}.jar app.jar
EXPOSE 8080
USER 1000:1000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
