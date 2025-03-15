#!/bin/bash

# Invoke the JAR file's BatchInsert.main() with provided arguments
# Ensure java points to a JDK higher than openjdk 21 2023-09-19 (Provided JAR was compiled on that version)

# Sample usage
# ./batchinsert.sh 6 2 ~/ASU/dbmsi/cse510_project/javaminibase/src/tests/scriptTestDataFiles/sample75_000.txt shellRun

java -cp cse510_project.jar scripts.BatchInsert "$1" "$2" "$3" "$4"