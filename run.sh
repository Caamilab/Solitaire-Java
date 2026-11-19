#!/bin/sh
mkdir -p out && javac -d out solitaire/*.java && java -cp out solitaire.Main "$@"
