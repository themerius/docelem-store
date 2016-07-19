#!/bin/bash

VERSION="0.5.0"

./activator "set test in assembly := {}" assembly

docker build -t docelem-store .

docker tag docelem-store docker-dev.arty.scai.fraunhofer.de/docelem-store:latest
docker tag docelem-store docker-dev.arty.scai.fraunhofer.de/docelem-store:$VERSION

# You need to do `docker login docker-dev.arty.scai.fraunhofer.de`
docker push docker-dev.arty.scai.fraunhofer.de/docelem-store:latest
docker push docker-dev.arty.scai.fraunhofer.de/docelem-store:$VERSION
