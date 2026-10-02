FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN groupadd --system --gid 10001 slotix && useradd --system --uid 10001 --gid slotix slotix
COPY --from=build --chown=slotix:slotix /workspace/target/reservation-core-0.0.1-SNAPSHOT.jar /app/app.jar
USER slotix
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
