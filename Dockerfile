# Build one service: docker build --build-arg MODULE=job-service .
ARG MODULE=job-service

FROM eclipse-temurin:21-jdk AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN chmod +x mvnw && ./mvnw -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
RUN useradd --system --no-create-home app
USER app
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT-exec.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
