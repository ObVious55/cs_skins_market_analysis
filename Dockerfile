FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

ENV SERVER_PORT=3000

COPY --from=build /workspace/target/*.jar app.jar

EXPOSE 3000

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
