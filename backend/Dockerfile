FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 tripdiary \
    && useradd --system --uid 10001 --gid tripdiary --home-dir /app tripdiary
WORKDIR /app
COPY --chown=tripdiary:tripdiary build/libs/trip-diary-api-*.jar app.jar

USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
