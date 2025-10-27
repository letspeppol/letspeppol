# LetsPeppol Proxy
This is what runs on api.letspeppol.org.

## Usage (V2)
First, set which proxy host you want to use. By default, use:
```sh
export PROXY_HOST=https://api.letspeppol.org
```

### Get an access token
Next, get an access token (this requires the local `ACCESS_TOKEN_KEY` env var to be the same as the proxy instance you will be talking to):
```sh
export ACCESS_TOKEN_KEY=...
export ONE=`node token.js 9944:nl862637223B02`
export TWO=`node token.js 0208:0734825676`
export THREE=`node token.js 0208:0636984350`

echo $ACCESS_TOKEN_KEY
echo $ONE
echo $TWO
echo $THREE
```

All users will be serviced through Peppyrus unless you set the `BACKEND` env var to one of the other available backends, which are currently 'acube', 'scrada' and 'ion'.

### Check connectivity
```sh
curl $PROXY_HOST/v2
```

### Send a UBL document
Not all sender/receiver combinations work yet, but the following ones do.
Run this command from the proxy folder (note the relative file path pointing to [../docs/](../docs/)):
```sh
pnpm build
node ./build/src/genDoc.js invoice 9944:nl862637223B02 0208:0734825676 asdf > ./doc.xml
curl -X POST --data-binary "@./doc.xml" -H "Authorization: Bearer $ONE" $PROXY_HOST/v2/send
```

### Activate and de-activate SMP records
FIXME: currently not implemented for Peppyrus backend.
```sh
curl -X POST -H "Authorization: Bearer $ONE" -H 'Content-Type: application/json' $PROXY_HOST/v2/reg
curl -X POST -H "Authorization: Bearer $ONE" -H 'Content-Type: application/json' $PROXY_HOST/v2/unreg
```

### Read invoices
To list invoices and credit notes you have sent and received. There are 4 collections, each filtered for the authenticated legal entity:
* /v2/invoices/outgoing
* /v2/invoices/incoming
* /v2/credit-notes/outgoing
* /v2/credit-notes/incoming

Default page size is 20.
```sh
curl -H "Authorization: Bearer $TWO" "$PROXY_HOST/v2/invoices/outgoing" | json
curl -H "Authorization: Bearer $THREE" "$PROXY_HOST/v2/credit-notes/incoming?page=2&pageSize=2" | json
curl -H "Authorization: Bearer $ONE" "$PROXY_HOST/v2/invoices/incoming?page=1" | json
```

This will give an array of objects that look like this:
```json
  {
    "platformId": "peppyrus:dca93007-6f8c-4ef7-943e-29ceff2c2a57",
    "docType": "invoice",
    "direction": "outgoing",
    "senderId": "9944:nl862637223B02",
    "senderName": "SupplierTradingName Ltd.",
    "receiverId": "9925:be0123456789",
    "receiverName": "BuyerTradingName AS",
    "createdAt": "2025-09-12T17:18:04.000Z",
    "amount": "1656.25",
    "docId": "Snippet1"
  },
```

To fetch the XML of a specific one:
```sh
curl -H "Authorization: Bearer $ONE" $PROXY_HOST/v2/invoices/incoming/e37b5843-fc55-4b0b-8b8e-73435d9a0363
```
Note that this will only work for invoices whose platformId prefix matches the BACKEND env var, which by default is `peppyrus`.

## Deployment
### On the host system of your laptop
Ask @michielbdejong if you need to run this code in development, because the current instructions require Peppyrus API credentials.
You could also create your own API key [through Peppyrus directly](https://customer.peppyrus.be/) if you want to use this code in an instance independent from the one sponsored by Ponder Source.

Apart from a Peppyrus account you will need a postgres database somewhere (for instance through `docker compose up -d`) and that you have the [`json` CLI tool](https://github.com/trentm/json?tab=readme-ov-file#installation) installed.
Create a `proxy/.env` file that looks like this:
```sh
PORT=3000
ACCESS_TOKEN_KEY="some-other-secret"
PEPPYRUS_TOKEN_TEST="see https://customer.test.peppyrus.be/customer/organization/api to create"
DATABASE_URL="postgres://syncables:syncables@localhost:5432/syncables?sslmode=disable"
```

Then run:
```sh
cd proxy
# load the variables from your .env file:
export $(xargs < .env)
# run the proxy:
pnpm install
pnpm build
docker compose up -d # for postgresql
pnpm build
docker exec -it db psql postgresql://syncables:syncables@localhost:5432/syncables -c "create type direction as enum ('incoming', 'outgoing');"
docker exec -it db psql postgresql://syncables:syncables@localhost:5432/syncables -c "create type docType as enum ('invoice', 'credit-note');"
docker exec -it db psql postgresql://syncables:syncables@localhost:5432/syncables -c "create table FrontDocs (senderId text, senderName text, receiverId text, receiverName text, docType docType, direction direction, docId text, amount numeric, platformId text primary key, createdAt timestamp);"
pnpm start
export PROXY_HOST=http://localhost:3000
```
The db table will be filled up by the [syncables cron job](https://github.com/tubsproject/syncables/blob/main/src/cron.ts) which admittedly is not fully documented yet. Ask @michielbdejong for help.

### With Docker
The Docker image takes the same 'PORT', 'PEPPYRUS_TOKEN_TEST', 'ACCESS_TOKEN_KEY', and 'DATABASE_URL' environment variables, and optionally 'BACKEND'.
```sh
docker build -t proxy .
export ACUBE_TOKEN=`./auth.sh | json token`
docker run -d -e PEPPYRUS_TOKEN_TEST=$ACUBE_TOKEN -e BACKEND=peppyrus -e PORT=3000 -e ACCESS_TOKEN_KEY=something-secret -e DATABASE_URL="postgres://syncables:syncables@localhost:5432/syncables?sslmode=disable" -p 3000:3000 proxy
export PROXY_HOST=http://localhost:3000
```

### With Nix
A `devenv` environment is available in the `dev/proxy` directory to host the proxy locally and run a small test. Make sure you have [`devenv`](https://devenv.sh/getting-started/) installed, and optionally install [`direnv`](https://devenv.sh/automatic-shell-activation/) for automatic shell activation. If you don’t use `direnv`, you’ll need to run `devenv shell` manually in the `dev/proxy` directory. Next, create a `dev/.env` file with the following contents (without quotes):
```sh
PORT=3000
PEPPYRUS_TOKEN_TEST=see https://customer.test.peppyrus.be/customer/organization/api to create
ACCESS_TOKEN_KEY=some-other-secret
DATABASE_URL="postgres://syncables:syncables@localhost:5432/syncables?sslmode=disable"
BACKEND=peppyrus
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

### At a Platform-as-a-Service provider like Heroku
There is an old Heroku instance running at api.letspeppol.org. but it's not up to date.
You can also deploy staging instances elsewhere.

If you host it on a platform other than Heroku you might need to add your own TLS-offloading proxy, and then the environment variables and `pnpm install; pnpm build; pnpm start` commands will be similar to how it works in development.