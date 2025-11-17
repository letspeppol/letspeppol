#!/bin/bash

# This line just tells the shell to stop if any error.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
	CREATE DATABASE $POSTGRES_APP_DB;
	CREATE USER $POSTGRES_APP_USER WITH PASSWORD '$POSTGRES_APP_PASSWORD';
  GRANT ALL PRIVILEGES ON DATABASE $POSTGRES_APP_DB TO $POSTGRES_APP_USER;
EOSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_APP_DB" <<-EOSQL
	GRANT ALL ON SCHEMA public TO $POSTGRES_APP_USER;
EOSQL
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_APP_USER" --dbname "$POSTGRES_APP_DB" <<-EOSQL
	CREATE TYPE direction AS enum ('incoming', 'outgoing');
	CREATE TYPE docType AS enum ('invoice', 'credit-note');
	CREATE TABLE FrontDocs (userId text, platformId text primary key, createdAt timestamp, docType docType, direction direction, counterPartyId text, counterPartyName text, docId text, amount numeric, dueDate timestamp, paymentTerms text, paid text, ubl text, status text);
EOSQL