#!/bin/bash
# Development run script for miot-calendar

# Set Netty workarounds for macOS
export JAVA_OPTS="-Depoll=false -Dio.netty.noUnsafe=true"

# Run Quarkus in dev mode
./mvnw quarkus:dev
