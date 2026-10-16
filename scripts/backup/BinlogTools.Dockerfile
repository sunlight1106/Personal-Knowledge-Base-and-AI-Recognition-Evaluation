FROM mysql:8.4
# The official server image contains a minimal client without mysqlbinlog.
# Keep recovery tooling in a separate image, out of the live database container.
RUN microdnf remove -y mysql-community-server-minimal \
    && sed -i 's@/docker/el/9/@/el/9/@' /etc/yum.repos.d/mysql-community-minimal.repo \
    && microdnf install -y "mysql-community-client-${MYSQL_VERSION}" \
    && microdnf clean all \
    && mysqlbinlog --version
ENTRYPOINT ["mysqlbinlog"]
