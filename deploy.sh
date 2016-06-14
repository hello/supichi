mvn -Dtcnative.classifier=linux-x86_64 clean package
scp target/speech-server-1.0-SNAPSHOT.jar dev:/tmp/
