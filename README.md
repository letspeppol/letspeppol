# LetsPeppol

![Let’s Peppol](./docs/logo.png)


We improve the Peppol network, writing software and letting people sign up for free. We will never run analytics on data that our users generate.
We are still in the launching phase.
This repo will contain all the code involved in running the Let’s Peppol project:
* a [docs](./docs/) folder with the website that runs on GitHub Pages at https://letspeppol.org
* a [proxy](./proxy/) folder that contains the proxy component
* a [kyc](./kyc/) folder that contains the Know-Your-Customer component (initially only for Belgian VAT numbers)
* an [app](./app/) folder that contains our web interface

Architecture and API documentation:

* [KYC and registration flows](./readme/kyc.md) guides users through the onboarding scenarios and
  their matching `RegistrationTest` examples.
* [API network flows](./readme/api-network-flows.md) groups every implemented API by caller,
  authentication mechanism, and downstream service, with focused diagrams and executable proof.
* Unified interactive [Swagger UI](https://be.letspeppol.org/api-docs.html) for the KYC, App, and
  Proxy APIs.
* Raw OpenAPI JSON: [KYC](https://be.letspeppol.org/kyc/v3/api-docs),
  [App](https://be.letspeppol.org/app/v3/api-docs), and
  [Proxy](https://be.letspeppol.org/proxy/v3/api-docs).

# KYC
The main challenge of giving free-of-charge access to the Peppol e-invoicing network is authentication of legal entities. For this, we:
* implemented a KYC component that gives our JWT tokens, that the API proxy from milestone 5a can check and trust
* used the open source [https://web-eid.eu](https://web-eid.eu) software
* let the user connect a card reader to their computer, and put their Belgian identity card into it
* let the user install some desktop software as well as a browser plugin (both open source from [https://web-eid.eu](https://web-eid.eu))
* with this the user can issue a QES legally binding signature (this only works for Belgian, Estonian, and 4 other countries, this is not yet possible for the Netherlands)
* We created a legal entity under Belgian law and had a lawyer write a contract that the person signs, stating they are one of the directors of a Belgian company
* this legal entity is in the process of getting full access to the Belgian companies registry, so we can check the first and last name that the person claims to be.

[Here is a screencast](https://drive.google.com/file/d/1HHSM9V1_2ez59vlnkL0YO3190X1uOh1j/view) that shows our fully automated business authentication in production.

# Dashboard application
A public instance of this code will soon be available, please keep an eye on [LetsPeppol.org](https://letspeppol.org).

[Here is a screencast](https://drive.google.com/file/d/1HHSM9V1_2ez59vlnkL0YO3190X1uOh1j/view?usp=drive_link) of how the application can be used to send and receive invoices via Let’s Peppol.

# Usage
## With Nix
[FIXME: docs under construction]
A `devenv` environment is available in the `dev/proxy` directory to host the proxy locally and run a small test. Make sure you have [`devenv`](https://devenv.sh/getting-started/) installed, and optionally install [`direnv`](https://devenv.sh/automatic-shell-activation/) for automatic shell activation. If you don’t use `direnv`, you’ll need to run `devenv shell` manually in the `dev/proxy` directory. Next, create a `dev/.env` file with the following contents (without quotes):
```sh
PORT=3000
SCRADA_API_KEY="from-scrada"
SCRADA_API_PWD="from-scrada"
SCRADA_COMPANY_ID="from-scrada"
ACCESS_TOKEN_KEY=some-other-secret
DATABASE_URL="postgres://letspeppol:letspeppol@localhost:5432/letspeppol?sslmode=disable"
```
Then run:
```sh
cd dev
# if the environment is blocked then run `direnv allow` to approve its content
# if you don't use direnv then run `devenv shell`
start-proxy
```

Open a new shell to test the proxy and run:
```sh
cd dev
test-proxy
```
## With Docker
Runs the whole stack. Only Docker is needed, no `.env` file and no certificates.

```sh
cd docker/local
docker compose up -d --build
```

| What | Where |
| --- | --- |
| Application | http://localhost:3001 |
| Design system | http://localhost:3002 |
| API docs | http://localhost:3001/api-docs.html |
| Mail sent by the application | http://localhost:8025 |
| Adminer | http://localhost:8081 |
| PostgreSQL | `localhost:5432`, databases `kyc`, `app` and `proxy` |

Sign in with `letspeppol@itaa.be` / `letspeppol`. Run the same command again after changing code.

Copy `docker/local/.env_example` to `docker/local/.env` to change ports, to add access point credentials for sending and receiving over Peppol, or to serve the application from another origin. Registering with a Belgian eID needs an HTTPS origin.

## Local development
Runs the database in Docker, the services from Gradle or your IDE and the UI from the Vite dev server. Needs Docker, JDK 25 and Node.js 22.

```sh
cd docker/local && docker compose up -d db mail
cd kyc && ./gradlew bootRun              # port 8084
cd app/backend && ./gradlew bootRun      # port 8085
cd proxy && ./gradlew bootRun            # port 8086
cd app/ui && npm install && npm start    # http://localhost:9000
```

`bootRun` starts each service with the `local` Spring profile, which holds working defaults for a developer machine and seeds the same accounts as above. From an IDE, run the `KycApplication`, `AppApplication` and `ProxyApplication` classes with `local` as active profile. Environment variables override the defaults. Never use the `local` profile in a deployed environment.

The design system runs on its own with `npm run storybook` in `app/ui`, on http://localhost:6006.

# Sponsored by NLNet
![NLNet](./docs/nlnet.svg)
