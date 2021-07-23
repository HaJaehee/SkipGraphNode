#!/bin/bash
# "Usage: java -jar target/SkipGraphNode-1.0-SNAPSHOT.jar [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] logging"
cd /home/wins/SkipGraphNode
num=1
cat /home/wins/SkipGraphNode/node_num | while read line
do
        num=`expr $line + 1`
        echo $num > node_num
done
java -jar target/SkipGraphNode-1.0-SNAPSHOT.jar $num `hostname -f` 0 10.22.0.1 21100