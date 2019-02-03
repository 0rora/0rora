#!/bin/bash

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
sbt dist
docker build \
    -f build/Dockerfile \
    -t 0rora/0rora:latest \
    -t 0rora/0rora:$TRAVIS_TAG \
    .
docker push 0rora/0rora
