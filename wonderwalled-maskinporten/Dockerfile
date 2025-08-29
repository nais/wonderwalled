FROM gcr.io/distroless/java21-debian12
COPY build/install/*/lib /app/lib
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback-remote.xml"
ENTRYPOINT ["java", "-cp", "/app/lib/*", "io.nais.WonderwalledKt"]
