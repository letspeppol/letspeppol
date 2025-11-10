# LetsPeppol

![Let's Peppol](./docs/logo.png)


We improve the Peppol network, writing software and letting people sign up for free. We will never run analytics on data that our users generate.
We are still in the launching phase.
This repo will contain all the code involved in running the Let's Peppol project:
* a [docs](./docs/) folder with the website that runs on GitHub Pages at https://letspeppol.org
* a [proxy](./proxy/) folder that contains the proxy component
* a [kyc](./kyc/) folder that contains the Know-Your-Customer component (initially only for Belgian VAT numbers)
* an [app](./app/) folder that contains our web interface

# Usage
## With Docker Compose
[FIXME: docs under construction]
```sh
docker compose up -d
```

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

# Sponsored by NLNet
![NLNet](./docs/nlnet.svg)