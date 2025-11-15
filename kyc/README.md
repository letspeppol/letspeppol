# Know Your Customer

The Know Your Customer (KYC) is built with Java Spring Boot. To build this application make sure you have the following tools installed:
* Intellij: 2025.1.2
* Lombok plugin
* Gradle
* Git
* Java SDK >= 21

## Build JAR

### Using IntelliJ
To build the KYC JAR follow the next steps:
1. Clone this repository
2. Open IntelliJ
3. Open project `lp/kyc/`
4. Wait for Gradle to setup the project
5. Open Gradle on the left side
6. Go to `Tasks > build > build`
7. Right-click on `build` and select `Run 'kyc [build]'`
8. The build artifacts can be found in `lp/kyc/build/libs/`

### Using CLI
1. Clone this repository
2. Execute the command `./gradlew clean build`
3. The build artifacts can be found in `lp/kyc/build/libs/`

## Run project

### Using IntelliJ
To run the project follow the next steps:
1. Clone this repository
2. Open IntelliJ
3. Open project `lp/kyc/`
4. Wait for Gradle to setup the project
5. Go to `kyc > src > main > java > org.letspeppol.kyc > KycApplication`
6. Right-click on `KycApplication` and select `'Modify run configuration'`
7. In the `Build and run` section click on `Modify options` and select `Environment variables`
8. Now add a variable with name `ACCESS_TOKEN_KEY` with the same value as the proxy
9. In `active profiles` fill in the profile `sqlite` 
10. If you want to register using the proxy, also add the variables `PROXY_API_URL` and `PROXY_ENABLED=true`

### Using CLI
1. Build the project
2. Change directory to `lp/kyc/build/libs/`
3. `export ACCESS_TOKEN_KEY=...`
4. `export PROXY_API_URL=...`
5. `export PROXY_ENABLED=...`
6. Execute the command `java -Dspring.profiles.active=sqlite -jar kyc-0.0.1-SNAPSHOT.jar`

### PostgreSQL
If you want to use PostgreSQL instead of SqLite, set the active spring profile to `postgres` instead of `sqlite`.  
Also configure these environment variables: `DB_USER`, `DB_PASS`, `DB_HOST` & `DB_PORT`.

## Nix development environment

A devenv environment is available in the `dev/kyc` directory. Make sure you have [`devenv`](https://devenv.sh/getting-started/) installed, and optionally install [`direnv`](https://devenv.sh/automatic-shell-activation/) for automatic shell activation. If you don’t use `direnv`, you’ll need to run `devenv shell` manually from the `dev/kyc` directory. The development environment sets up IntelliJ, installs project plugins, and opens the KYC project.

## Key Rotation
1. To generate a new key you can use the OpenSSL utility: `openssl rand -base64 32`
2. Add a new key in `application.properties` with e.g. property `encryption.keys.s2=${ENCRYPTION_KEY_2}`
3. encryption.active-key-id=s1
