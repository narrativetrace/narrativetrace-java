FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY build/libs/notify-*.jar app.jar
# See ../shop/Dockerfile for why this is an exploded, flat-classpath launch.
RUN java -Djarmode=tools -jar app.jar extract --destination /app/extracted && rm app.jar
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
