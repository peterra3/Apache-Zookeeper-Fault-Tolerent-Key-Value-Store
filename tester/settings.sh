#!/bin/bash

unset JAVA_TOOL_OPTIONS
if [ -f /usr/lib/jvm/default-java/bin/javac ]; then
    JAVA_HOME=/usr/lib/jvm/default-java
elif [ -f /usr/lib/jvm/java-11-openjdk-amd64/bin/javac ]; then
    JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/
elif [ -f /usr/lib/jvm/java-8-openjdk-amd64/bin/javac ]; then
    JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
elif [ -f /usr/lib/jvm/java-openjdk/bin/javac ]; then
    JAVA_HOME=/usr/lib/jvm/java-openjdk
else
    echo "Unable to find java compiler :("
    exit 1
fi

THRIFT_CC=/opt/bin/thrift

ZKPATH=../zookeeper-3.4.13
