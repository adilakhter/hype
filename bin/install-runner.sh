#!/usr/bin/env bash

VERSION=0.0.3
DEST=/usr/local/bin
JAR=${DEST}/hype-run.jar
MAVEN_REPO=https://repo.maven.apache.org/maven2

echo "=== HYPE RUN INSTALLER (v$VERSION) ==="
set -ex

# download runner capsule
curl -#fL --output ${JAR} \
  ${MAVEN_REPO}/com/spotify/hype-run/${VERSION}/hype-run-${VERSION}-capsule.jar

# invoke once in noop mode to download hype caplet
/usr/bin/java -Dcapsule.mode=noop -jar ${JAR}


cat > ${DEST}/hype-run <<EOF
#/usr/bin/env bash

exec /usr/bin/java \${JVM_ARGS} -jar ${JAR} "\$@"
EOF

chmod +x ${DEST}/hype-run
