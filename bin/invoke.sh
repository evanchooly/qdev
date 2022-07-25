#!/bin/bash

BASEDIR=$(dirname $0)
ROOTDIR=${BASEDIR}/..
APP_JAR=${ROOTDIR}/target/quarkus-app/quarkus-run.jar

if [ "`find ${ROOTDIR}/src/main/ -type f -newer ${APP_JAR}`" ]
then
  echo rebuilding jar
  mvn -q -f ${ROOTDIR} -e package || exit
fi

java -jar ${APP_JAR} $(basename $0) $*