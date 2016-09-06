mvn -Dtcnative.classifier=linux-x86_64 clean package
scp supichi-app/target/supichi-app-0.3.0-SNAPSHOT.jar dev:/tmp/
# scp supichi-app/configs/supichi-app.staging.yml dev:/tmp/
scp supichi-app/configs/supichi-app.demo.yml dev:/tmp/
