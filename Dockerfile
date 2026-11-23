FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /builder

# Copy Maven wrapper and pom.xml first to cache dependencies
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline

# Copy source code and build the fat JAR
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Extract the JAR into layers using Spring Boot 4 tools mode
RUN cp target/*.jar application.jar && \
    java -Djarmode=tools -jar application.jar extract --layers --destination extracted


# Stage 2: Create the lightweight runtime image
FROM eclipse-temurin:21-jre-jammy
WORKDIR /application

# Copy the extracted layers in order from least to most frequently changed
COPY --from=builder /builder/extracted/dependencies/ ./
COPY --from=builder /builder/extracted/spring-boot-loader/ ./
COPY --from=builder /builder/extracted/snapshot-dependencies/ ./
COPY --from=builder /builder/extracted/application/ ./

# Run as a non-root user for security
USER 1000

# Expose to 9000
EXPOSE 9000

# Start the application
ENTRYPOINT ["java", "-jar", "application.jar"]