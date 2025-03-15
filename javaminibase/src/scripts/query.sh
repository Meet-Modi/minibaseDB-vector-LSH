#!/bin/bash

# Invoke the JAR file's Query.main() with provided arguments
# Ensure java points to a JDK higher than openjdk 21 2023-09-19 (Provided JAR was compiled on that version)

# Sample usage
# ./query.sh shellRun  ~/ASU/dbmsi/cse510_project/javaminibase/src/tests/scriptTestDataFiles/queryDataFiles/rquery1.txt Y 10

java -cp cse510_project.jar scripts.Query "$1" "$2" "$3" "$4"