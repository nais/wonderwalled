FROM docker.io/curlimages/curl:latest as linkerd
ARG LINKERD_AWAIT_VERSION=v0.2.4
RUN curl -sSLo /tmp/linkerd-await https://github.com/linkerd/linkerd-await/releases/download/release%2F${LINKERD_AWAIT_VERSION}/linkerd-await-${LINKERD_AWAIT_VERSION}-amd64 && \
    chmod 755 /tmp/linkerd-await

FROM navikt/java:16
ENV JAVA_OPTS="-Dlogback.configurationFile=logback-remote.xml"
COPY build/libs/*-all.jar app.jar
COPY --from=linkerd /tmp/linkerd-await /linkerd-await
ENTRYPOINT ["/linkerd-await", "--shutdown", "--"]
CMD ["/entrypoint.sh"]
