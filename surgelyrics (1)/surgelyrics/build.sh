#!/bin/sh
# Requires Java 21+ and Gradle 9+ on PATH. Jar ends up in mc1_21_11/build/libs/
set -e
gradle :mc1_21_11:build
ls mc1_21_11/build/libs/*.jar
