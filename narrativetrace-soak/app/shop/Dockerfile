FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY build/libs/shop-*.jar app.jar
# Exploded, flat-classpath launch (not `java -jar`) — see entrypoint.sh for why: it collapses
# Boot's own fat-jar classloader down to the single system classloader the -javaagent also
# attaches to, so there is only ever one copy of slf4j-api/logback-classic/the app's own classes
# in the whole JVM.
RUN java -Djarmode=tools -jar app.jar extract --destination /app/extracted && rm app.jar
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
ENTRYPOINT ["/entrypoint.sh"]
