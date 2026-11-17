#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"
if [ ! -f d3-model/target/d3-model-1.0.jar ] || [ -n "$(find d3-model/src PetriObjLib-master/src PetriObjLib-modified/src -newer d3-model/target/d3-model-1.0.jar -name '*.java' | head -1)" ]; then
  mvn -q package
fi
java -jar d3-model/target/d3-model-1.0.jar "$@"
