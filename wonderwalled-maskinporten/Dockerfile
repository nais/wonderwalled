FROM cgr.dev/chainguard/jre
COPY build/install/*/lib /app/lib
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback-remote.xml"
ENTRYPOINT ["java", "-cp", "/app/lib/*", "io.nais.WonderwalledKt"]
