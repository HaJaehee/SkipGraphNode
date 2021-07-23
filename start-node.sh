#!/bin/bash
# "Usage: java -jar target/SkipGraphNode-1.0-SNAPSHOT.jar [switch num] [locality id] [DHT node num] [introducer ip] [introducer port] logging"
java -jar target/SkipGraphNode-1.0-SNAPSHOT.jar $1 `hostname -f` 0 10.22.0.1 21100 logging