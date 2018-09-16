#!/bin/bash

echo "$DOCKER_PASSWORD" | docker login -u "$DOCKER_USERNAME" --password-stdin
sbt dist
docker build -f build/Dockerfile -t luxe-app/luxe .
docker tag luxe-app/luxe:$TRAVIS_TAG
docker tag luxe-app/luxe:latest
docker push luxe-app/luxe
