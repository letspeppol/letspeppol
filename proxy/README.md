# LetsPeppol Proxy

## Usage
Apart from a Scrada account you will need a postgres database somewhere (for instance through `docker compose up -d`) and that you have the [`json` CLI tool](https://github.com/trentm/json?tab=readme-ov-file#installation) installed.

```sh
export PORT=3000
export SCRADA_API_KEY="from-scrada"
export SCRADA_API_PWD="from-scrada"
export SCRADA_COMPANY_ID="from-scrada"
export DATABASE_URL="postgres://syncables:syncables@localhost:5432/syncables?sslmode=disable"
export ACCESS_TOKEN_KEY="something-secret"
docker compose up -d
docker exec -it db psql postgresql://syncables:syncables@localhost:5432/syncables -c "create type direction as enum ('incoming', 'outgoing');"
docker exec -it db psql postgresql://syncables:syncables@localhost:5432/syncables -c "create type docType as enum ('invoice', 'credit-note');"
docker exec -it db psql postgresql://syncables:syncables@localhost:5432/syncables -c "create table FrontDocs (userId text, counterPartyId text, counterPartyName text, docType docType, direction direction, docId text, amount numeric, platformId text primary key, createdAt timestamp, ubl text);"

pnpm install
pnpm build
pnpm start
```
In a separate terminal window, do the following to register, send a document, list documents, fetch a single document, and unregister:
```sh
export PROXY_HOST=http://localhost:3000
export ACCESS_TOKEN_KEY="something-secret"
export ONE=`node token.js 0208:0734825676`
curl $PROXY_HOST/v2
curl -X POST -d'{"name":"BARGE vzw"}' -H "Authorization: Bearer $ONE" -H 'Content-Type: application/json' $PROXY_HOST/v2/reg
node ./build/src/genDoc.js invoice 0208:0734825676 9944:nl862637223B02 asdf > ./doc.xml
curl -X POST --data-binary "@./doc.xml" -H "Authorization: Bearer $ONE" $PROXY_HOST/v2/send
docker exec -it db psql postgresql://syncables:syncables@localhost:5432/syncables -c "select platformId, userId, counterPartyId, docId, amount from frontdocs"
curl -H "Authorization: Bearer $TWO" "$PROXY_HOST/v2/documents" | json
curl -H "Authorization: Bearer $ONE" $PROXY_HOST/v2/documents/scrada_e37b5843-fc55-4b0b-8b8e-73435d9a0363
curl -X POST -H "Authorization: Bearer $ONE" -H 'Content-Type: application/json' $PROXY_HOST/v2/unreg
```

The proxy keeps a local database of sent and received documents in which you can search.
This will give an array of objects that look like this:
```json
[
  {
    "platformId": "scrada_81f0b375-3242-4a1c-b472-4c90caacd3a8",
    "docType": "invoice",
    "direction": "outgoing",
    "counterPartyId": "9944:nl862637223B02",
    "counterPartyName": "Ponder Source Three",
    "createdAt": null,
    "amount": "6125",
    "docId": "asdf"
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

### With Nix
A `devenv` environment is available in the `dev/proxy` directory to host the proxy locally and run a small test. Make sure you have [`devenv`](https://devenv.sh/getting-started/) installed, and optionally install [`direnv`](https://devenv.sh/automatic-shell-activation/) for automatic shell activation. If you don’t use `direnv`, you’ll need to run `devenv shell` manually in the `dev/proxy` directory. Next, create a `dev/.env` file with the following contents (without quotes):
```sh
PORT=3000
SCRADA_API_KEY="from-scrada"
SCRADA_API_PWD="from-scrada"
SCRADA_COMPANY_ID="from-scrada"
ACCESS_TOKEN_KEY=some-other-secret
DATABASE_URL="postgres://syncables:syncables@localhost:5432/syncables?sslmode=disable"
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
