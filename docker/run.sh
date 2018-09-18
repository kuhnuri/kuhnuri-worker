#!/usr/bin/env sh

CLASSPATH=conf/
for i in lib/*.jar; do
    CLASSPATH=$CLASSPATH:$i
done
export DITA_HOME=$PWD
. /opt/app/config/env.sh

if [ -f /opt/app/RUNNING_PID ]; then
    rm /opt/app/RUNNING_PID
fi
exec java $JAVA_OPTS -Duser.dir=/opt/app -Dconfig.file=conf/$ENVIRONMENT.conf -cp $CLASSPATH play.core.server.ProdServerStart
