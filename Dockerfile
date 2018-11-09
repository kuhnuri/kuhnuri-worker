FROM jelovirt/kuhnuri_dita-ot:3.1

RUN mkdir -p /opt/app/conf && \
    mkdir -p /opt/app/lib
COPY target/universal/stage/conf /opt/app/conf
COPY target/universal/stage/lib /opt/app/lib

COPY docker/run.sh /opt/app/run.sh
RUN chmod 755 /opt/app/run.sh

EXPOSE 9000
VOLUME ["/var/log/app", "/tmp/app", "/var/lib/app"]

WORKDIR /opt/app
ENTRYPOINT ["./run.sh"]
