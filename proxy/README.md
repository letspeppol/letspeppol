# LetsPeppol Proxy

## Usage
Note that there is a [`docker-compose.yml`](../docker-compose.yml) file in the root of this repository which includes the database server, this proxy, the web interface app and the KYC component.

**If you want to run just this proxy, then keep reading...**

Apart from a Scrada account you will need to have Docker installed, and optionally (for pretty-printing) the [`json` CLI tool](https://github.com/trentm/json?tab=readme-ov-file#installation).

Set some environment variables. You should get the SCRADA_ credentials from Scrada, and pick a strong ACCESS_TOKEN_KEY yourself, you will need that in a next step:
```sh
export PORT=3000
export SCRADA_API_KEY="from-scrada"
export SCRADA_API_PWD="from-scrada"
export SCRADA_COMPANY_ID="from-scrada"
export SCRADA_COMPANY_KEY="from-scrada"
export DATABASE_URL="postgres://letspeppol:letspeppol@localhost:5432/letspeppol?sslmode=disable"
export ACCESS_TOKEN_KEY="something-secret"
```
For developing the proxy, you don't need the `kyc` and `app` components, but you do need the `db` component, and you can use `pnpm` to run this proxy as a Node process on the host system, so:
```sh
docker compose -f compose-db-only.yml up -d
pnpm install
pnpm build
pnpm start
```
In both cases you will need to create the database table before first use (FIXME: make this automatic):
```sh
docker exec -it db psql postgresql://letspeppol:letspeppol@localhost:5432/letspeppol -c "create type direction as enum ('incoming', 'outgoing');"
docker exec -it db psql postgresql://letspeppol:letspeppol@localhost:5432/letspeppol -c "create type docType as enum ('invoice', 'credit-note');"
docker exec -it db psql postgresql://letspeppol:letspeppol@localhost:5432/letspeppol -c "create table FrontDocs (userId text, platformId text primary key, createdAt timestamp, docType docType, direction direction, counterPartyId text, counterPartyName text, docId text, amount numeric, dueDate timestamp, paymentTerms text, paid text, ubl text, status text);"
```

In a separate terminal window, do the following to register, send a document, list documents, fetch a single document, and unregister:
```sh
export PROXY_HOST=http://localhost:3000
export ACCESS_TOKEN_KEY="something-secret" # same as what you put in the compose.yml!
export ONE=`node token.js 0208:0734825676`
curl $PROXY_HOST/v2
curl -X POST -d'{"name":"BARGE vzw"}' -H "Authorization: Bearer $ONE" -H 'Content-Type: application/json' $PROXY_HOST/v2/reg
node ./build/src/genDoc.js invoice 0208:0734825676 0208:1029545627 asdf > ./doc.xml
curl -X POST --data-binary "@./doc.xml" -H "Authorization: Bearer $ONE" $PROXY_HOST/v2/send
docker exec -it db psql postgresql://letspeppol:letspeppol@localhost:5432/letspeppol -c "select userId, platformId, createdAt, docType, direction, counterPartyId, counterPartyName, docId, amount, dueDate, paymentTerms, paid, status from frontdocs"
curl -X POST -d '{"paid":"yes"}' -H "Authorization: Bearer $ONE" -H 'Content-Type: application/json' $PROXY_HOST/v2/documents/scrada_9e8912d1-5d42-4cc1-a2c4-176f7d7738d7
curl -H "Authorization: Bearer $ONE" "$PROXY_HOST/v2/documents" | json
curl -H "Authorization: Bearer $ONE" $PROXY_HOST/v2/documents/scrada_e37b5843-fc55-4b0b-8b8e-73435d9a0363
curl -H "Authorization: Bearer $ONE" "$PROXY_HOST/v2/totals" | json
curl -X POST -H "Authorization: Bearer $ONE" -H 'Content-Type: application/json' $PROXY_HOST/v2/unreg
```

The proxy keeps a local database of sent and received documents in which you can search.
This will give an array of objects that look like this:
```json
[
  {
    "platformId": "scrada_9e8912d1-5d42-4cc1-a2c4-176f7d7738d7",
    "createdAt": "2025-11-05T12:19:47.028Z",
    "docType": "invoice",
    "direction": "outgoing",
    "counterPartyId": "0208:1029545627",
    "counterPartyName": "Ponder Source Three",
    "docId": "asdf",
    "amount": "6125",
    "dueDate": "2024-01-17T23:00:00.000Z",
    "paymentTerms": "Payment within 10 days, 2% discount",
    "paid": "yes",
    "status": "Created"
  }
]
```
There is a `page` and a `pageSize` parameter. Default page size is 20.
Outgoing docs are filtered by senderId, incoming ones by receiverId, based on the authenticated PeppolId.
Apart from that you can filter on `counterPartyId`, `counterPartyNameLike`, `docType=invoice|credit-note`, `direction=incoming|outgoing`, `docId`, and `sortBy=amountAsc|amountDesc|createdAtAsc(default)|createdAtDesc`.

Examples:
```sh
curl -H "Authorization: Bearer $TWO" "$PROXY_HOST/v2/documents?direction=outgoing" | json
curl -H "Authorization: Bearer $THREE" "$PROXY_HOST/v2/documents?docType=credit-notes&page=2&pageSize=2" | json
curl -H "Authorization: Bearer $ONE" "$PROXY_HOST/v2/documents?direction=outgoing&receiverName=Business Appl&sortBy=amountDesc" | json
```

The output of the `/v2/totals` call looks like this:
```json
{
  "totalPayable": "0",
  "totalReceivable": "18375"
}
```

### Webhooks
To let the webhooks from Scrada arrive at your laptop locally, you can do the following:
```sh
npm install -g localtunnel
lt --port 3000
curl -X POST -d'{"test":true}' -H 'Content-Type: application/json' -H 'X-Scrada-HMAC-SHA256: 2abe5bb975969de69687e1a8fd7b5dbee7217e07a8b3161acb96614b5f94df8c' https://fuzzy-suns-march.loca.lt/v2/webhook/outgoing/scrada
```
This will give you a domain name like https://yummy-rings-cut.loca.lt and then you can go to [the integration settings in your Scrada dashboard](https://mytest.scrada.be/nl/company/f932b7c4-b4fe-40d1-a981-a338b4478f78/settings/integrations/webhook) and configure:
* `peppolInboundDocument/new` to go to https://yummy-rings-cut.loca.lt/v2/webhook/incoming
* `peppolOutboundDocument/statusUpdate` to go to https://yummy-rings-cut.loca.lt/v2/webhook/outgoing

### With Nix
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
