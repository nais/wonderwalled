FROM cgr.dev/chainguard/jre:openjdk-jre-17

ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml"
COPY build/libs/*-all.jar /app/app-all.jar

CMD ["-jar", "/app/app-all.jar"]
