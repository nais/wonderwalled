FROM gcr.io/distroless/java21-debian12
COPY build/libs/*-all.jar app.jar
ENV JDK_JAVA_OPTIONS="-Dlogback.configurationFile=logback-remote.xml"
EXPOSE 8080
CMD ["app.jar"]
