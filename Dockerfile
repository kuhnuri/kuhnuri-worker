FROM openjdk:8-jre-alpine

RUN mkdir -p /opt/app/conf
COPY target/universal/stage/conf /opt/app/conf
RUN mkdir -p /opt/app/lib
COPY target/universal/stage/lib /opt/app/lib

RUN mkdir -p /opt/app/bin
COPY target/universal/stage/bin/dita /opt/app/bin/dita
RUN chmod 755 /opt/app/bin/dita
RUN mkdir -p /opt/app/config
COPY target/universal/stage/config /opt/app/config
RUN mkdir -p /opt/app/plugins
COPY target/universal/stage/plugins /opt/app/plugins
RUN mkdir -p /opt/app/xsl
COPY target/universal/stage/xsl /opt/app/xsl
COPY target/universal/stage/build.xml /opt/app/
COPY target/universal/stage/build_template.xml /opt/app/
COPY target/universal/stage/catalog-dita.xml /opt/app/
COPY target/universal/stage/catalog-dita_template.xml /opt/app/
COPY target/universal/stage/integrator.xml /opt/app/

COPY docker/run.sh /opt/app/run.sh
RUN chmod 755 /opt/app/run.sh
RUN mkdir -p /opt/workspace

EXPOSE 9000
VOLUME ["/opt/workspace"]

WORKDIR /opt/app
ENTRYPOINT ["./run.sh"]
