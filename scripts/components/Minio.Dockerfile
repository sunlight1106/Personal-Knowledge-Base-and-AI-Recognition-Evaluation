# Build the final official community source release. The repository has been
# archived upstream: this is reproducible development tooling, not ongoing support.
FROM debian:trixie-slim AS build
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
 && rm -rf /var/lib/apt/lists/*
COPY scripts/components/minio-build.sh /tmp/minio-build.sh
ENV MINIO_TOOLING=/opt/minio-build
RUN bash /tmp/minio-build.sh

FROM build AS source-build
# Include the exact dependency sources used by the redistributed binary.
RUN cd /opt/minio-build/minio-RELEASE.2025-10-15T17-29-55Z \
 && PATH=/opt/minio-build/go/bin:$PATH GOPATH=/opt/minio-build/gopath GOTOOLCHAIN=local /opt/minio-build/go/bin/go mod vendor \
 && cp /tmp/minio-build.sh ./pkb-build.sh \
 && tar -czf /opt/minio-build/minio-sources.tar.gz -C /opt/minio-build minio-RELEASE.2025-10-15T17-29-55Z
FROM scratch AS source-export
COPY --from=source-build /opt/minio-build/minio-sources.tar.gz /

FROM debian:trixie-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --gid 10001 minio \
 && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin minio \
 && mkdir -p /data \
 && chown 10001:10001 /data
COPY --from=build /opt/minio-build/bin/minio /usr/local/bin/minio
COPY --from=build /opt/minio-build/minio-RELEASE.2025-10-15T17-29-55Z/LICENSE /usr/share/licenses/minio/LICENSE
USER 10001:10001
EXPOSE 9000 9001
HEALTHCHECK --interval=5s --timeout=3s --retries=30 \
  CMD curl --fail --silent http://127.0.0.1:9000/minio/health/live || exit 1
ENTRYPOINT ["/usr/local/bin/minio"]
CMD ["server", "/data", "--address", ":9000", "--console-address", ":9001"]
