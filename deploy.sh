# mvn -Dtcnative.classifier=linux-x86_64 clean package
scp supichi-app/target/supichi-app-1.0-SNAPSHOT.jar dev:/tmp/
scp supichi-app/configs/supichi-app.staging.yml dev:/tmp/
