#!/bin/bash

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
sbt dist
docker build \
    -f build/Dockerfile \
    -t luxe-app/luxe:latest luxe-app/luxe:$TRAVIS_TAG \
    .
docker push luxe-app/luxe
