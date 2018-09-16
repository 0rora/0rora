#!/bin/bash

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
sbt dist
docker build \
    -f build/Dockerfile \
    -t luxeapp/luxe:latest \
    -t luxeapp/luxe:$TRAVIS_TAG \
    .
docker push luxeapp/luxe
