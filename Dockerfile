FROM jelovirt/kuhnuri_dita-ot:latest

RUN mkdir -p /opt/app/conf && \
    mkdir -p /opt/app/lib
COPY target/universal/stage/conf /opt/app/conf
COPY target/universal/stage/lib /opt/app/lib

COPY docker/run.sh /opt/app/run.sh
RUN chmod 755 /opt/app/run.sh

EXPOSE 9000
VOLUME ["/opt/workspace", "/opt/app/logs"]

WORKDIR /opt/app
ENTRYPOINT ["./run.sh"]
