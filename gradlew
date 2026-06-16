#!/usr/bin/env sh

DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/c/Users/55480/WorkBuddy/Claw/jdk17/jdk-17.0.2}"
CLASSPATH="$DIR/gradle/wrapper/gradle-wrapper.jar"
exec "$JAVA_HOME/bin/java.exe" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
