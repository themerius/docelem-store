#!/bin/bash

./activator "set test in assembly := {}" assembly

docker build -t docelem-store .

docker tag docelem-store docker-dev.arty.scai.fraunhofer.de/docelem-store

# You need to do `docker login docker-dev.arty.scai.fraunhofer.de`
docker push docker-dev.arty.scai.fraunhofer.de/docelem-store
