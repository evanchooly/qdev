#!/usr/bin/env bash

BASE=$( dirname $0 )
NEWER=false

for i in `find $BASE/src -type f`
do
  if [ $i -nt $BASE/target/qdev.jar ]
  then
    NEWER=true
  fi
done

if [ $NEWER == true ]
then
  echo Updates found.  rebuilding.
  mvn -q -f $BASE/pom.xml package
fi

java -jar ~/bin/qdev/target/qdev-runner.jar bytecode $* | grep -v INFO

