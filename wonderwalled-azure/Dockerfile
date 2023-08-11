FROM ghcr.io/navikt/baseimages/temurin:17
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml"
COPY build/libs/*-all.jar app.jar
