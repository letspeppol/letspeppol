# LetsPeppol

![Let's Peppol](./docs/logo.png)


We improve the Peppol network, writing software and letting people sign up for free. We will never run analytics on data that our users generate.
We are still in the launching phase.
This repo will contain all the code involved in running the Let's Peppol project:
* a [docs](./docs/) folder with the website that runs on GitHub Pages at https://letspeppol.org
* a [proxy](./proxy/) folder that contains the proxy component
* a [kyc](./kyc/) folder that contains the Know-Your-Customer component (initially only for Belgian VAT numbers)
* an [app](./app/) folder that contains our web interface

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

# Dasboard application
A public instance of this code will soon be available, please keep an eye on [LetsPeppol.org](https://letspeppol.org).

[Here is a screencast](https://drive.google.com/file/d/1HHSM9V1_2ez59vlnkL0YO3190X1uOh1j/view?usp=drive_link) of how the application can be used to send and receive invoices via Let's Peppol.

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
