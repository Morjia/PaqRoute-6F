# Etapa 1: build con Maven + JDK 17
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:resolve -q
COPY src src
RUN mvn package -DskipTests -q

# Etapa 2: ejecución con solo el JRE y usuario sin privilegios
FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --shell /sbin/nologin paqroute
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/logs && chown -R paqroute:paqroute /app
USER paqroute
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]