#!/bin/sh

cp ../assemblies/client/target/hop.war ./
cp ../assemblies/plugins/dist/target/hop-assemblies-plugins-dist-0.30-SNAPSHOT.zip ./hop-assemblies-plugins-dist.zip
docker build -t hop .