###Building the application

1. If you don't have mvn, install it at first!
2. Use this commands to build the application:
    1. If you work on IntelliJ IDEA, `./mvnw clean install -DskipTests`
    2. If installed the maven on your system, `mvn clean install -DskipTests`

###Running the application with your custom configuration file:

1. Use `java -jar target/methadologist.jar --spring.config.location=file:////your/custom/config/path/`
2. Your path should contain an application.properties file
3. Use the application-dev.properties content in your application.properties

###Test containers:

1. Use this command if you have 404 error status code on getting the docker
   image `docker pull testcontainers/ryuk:0.3.0`

###Docker commands

1. To remove all containers: `docker rm -f $(docker ps -aqs)`
2. To remove all volumes: `docker volume prune`
3. To rebuild the containers: `docker-compose up --build cryptocurrency`
4. To just up the containers(using cache): `docker-compose up cryptocurrency`

### JPA

1. Add `@Version` annotation in all the entities to let the JPA handles concurrent updates!
   For more details you can see [this](https://stackoverflow.com/questions/2572566/java-jpa-version-annotation).
   ```
    @Version
    @NotNull
    @Builder.Default
    private Long version = 0L;
   ```
    - It throws `OptimisticLockException` in the last concurrent update! So, the frontend applications should receive
      proper error and take care of the error properly!
    - If you have some native update queries you should add take care of versioning yourself.

### Mock Server

1.

Use [this](https://www.mock-server.com/mock_server/creating_expectations.html#button_match_request_by_negative_priority)
link to see more details on the expectations
