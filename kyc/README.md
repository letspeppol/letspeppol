# Know Your Customer

The Know Your Customer (KYC) is built with Java Spring Boot. To build this application make sure you have the following tools installed:
* Intellij: 2025.1.2
* Lombok plugin
* Gradle
* Git
* Java SDK >= 25

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

Start the database first, see [Local development](../README.md#local-development).

### Using IntelliJ
1. Open project `lp/kyc/`
2. Go to `kyc > src > main > java > org.letspeppol.kyc > KycApplication`
3. Right-click on `KycApplication` and select `'Modify run configuration'`
4. In `active profiles` fill in the profile `local`
5. Run the configuration

### Using CLI
Execute the command `./gradlew bootRun`. It uses the `local` profile unless `SPRING_PROFILES_ACTIVE` is set.

### PostgreSQL
PostgreSQL is the only supported database. The `local` profile includes the `postgres` profile and connects to `localhost:5432`.
Deployed environments use the `postgres` profile and configure these environment variables: `DB_USER`, `DB_PASS`, `DB_HOST` & `DB_PORT`.

## Nix development environment

A devenv environment is available in the `dev/kyc` directory. Make sure you have [`devenv`](https://devenv.sh/getting-started/) installed, and optionally install [`direnv`](https://devenv.sh/automatic-shell-activation/) for automatic shell activation. If you don’t use `direnv`, you’ll need to run `devenv shell` manually from the `dev/kyc` directory. The development environment sets up IntelliJ, installs project plugins, and opens the KYC project.

## Key Rotation
1. To generate a new key you can use the OpenSSL utility: `openssl rand -base64 32`
2. Add a new key in `application.properties` with e.g. property `encryption.keys.s2=${ENCRYPTION_KEY_2}`
3. encryption.active-key-id=s1

## Oauth2 keys

`openssl genrsa -out kyc/src/main/resources/kyc-private.pem 2048`

`openssl rsa -in kyc/src/main/resources/kyc-private.pem -pubout -out kyc/src/main/resources/kyc-public.pem`
