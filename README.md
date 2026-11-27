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
* [Direct PHOSS AP/SMP integration](./readme/phoss-access-point.md) covers the optional proxy provider and isolated local AS4 verification.
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
## Local development with Nix

See the [local development how-to](dev/README.md) for a step-by-step Mailpit test and optional HTTPS and Recommand Playground setup.

Install [devenv 2.1 or newer](https://devenv.sh/getting-started/). From the repository root:

```sh
devenv shell -- npm --prefix app/ui ci
devenv up
```

Open **http://localhost:9000** for the UI (use `localhost`, not `127.0.0.1`, so the OAuth URLs match) and **http://127.0.0.1:8025** for the Mailpit inbox. The stack starts PostgreSQL, KYC, the app backend, the Java proxy, and Vite. On the first run, Gradle needs time to download dependencies and build the Java services. KYC seeds sample accounts in the `dev` profile; for example, use `alice@letspeppol.org` with password `alice` locally. Press Ctrl+C to stop the stack (confirm if the TUI prompts). The database, local mail, and generated JWT signing keys persist under `.devenv/state/`.

Use `devenv shell` for Java 25, Node 22, and npm at the root. With [direnv](https://devenv.sh/automatic-shell-activation/), run `direnv allow` once at the root to activate the shell automatically. Use `devenv up postgres mailpit` if you prefer to start the applications separately; inside the shell use `dev-backend kyc`, `dev-backend app`, `dev-backend proxy`, or `dev-ui`. `open-kyc` opens the project if an IntelliJ launcher is on `PATH`.

### Local credentials

**No `.env` is required for local startup.** The Java launchers read the checked-in [`.env.defaults`](.env.defaults), then apply overrides from an optional, git-ignored root `.env` using Bash assignment syntax. The defaults are for loopback development and throwaway data only; never reuse them in a deployment.

To test Recommand integration, copy [`.env.example`](.env.example) to `.env` and fill in **both** `RECOMMAND_API_KEY` and `RECOMMAND_API_SECRET` from your own [Recommand](https://app.recommand.eu) Playground team. Restart the proxy after changing them. Leave both blank if you aren't testing Peppol registration or document exchange. Don't use a production team or share its credentials.

Mailpit replaces real SMTP locally. KBO bulk sync, Scrada, and e-invoice.be aren't needed for the default stack. Leave `OPEN_COLLECTIVE_API_KEY` blank to keep donation statistics local; set it in `.env` and restart the app backend to include live OpenCollective data. The root `.env` is for this Nix workflow, not Docker Compose.

## With Docker
To run this project locally using docker-compose, follow these steps:
1. Clone this repository.
2. Point a (sub)domain to localhost and generate a TLS certificate (e.g. letspeppol.example.com).
3. Navigate to docker/local.
4. Copy the file .env_example to a new file named .env.
5. Update the .env file with the required environment variables.
6. Run `docker compose up -d`
7. Visit https://letspeppol.example.com in your browser.

# Sponsored by NLNet
![NLNet](./docs/nlnet.svg)
