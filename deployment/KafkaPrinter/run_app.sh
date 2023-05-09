#!/bin/bash

bootstrap_server="$1"
topic_name="$2"

while true
do
  #nc -v -l -p 9220 |  java -jar KafkaPrinter-1.0-SNAPSHOT.jar -o "$target_endpoint" "$topic_name"
  java -jar KafkaPrinter-1.0-SNAPSHOT.jar -o "$target_endpoint" "$topic_name"
  >&2 echo "Command has encountered error. Restarting now ..."
  sleep 1
done