CREATE TYPE access_point AS ENUM ('NONE', 'E_INVOICE', 'SCRADA');
CREATE TYPE direction AS ENUM ('INCOMING', 'OUTGOING');

CREATE TABLE ubl_document (
    id                  uuid PRIMARY KEY,
    direction           direction NOT NULL,
    owner_peppol_id     text NOT NULL,
    partner_peppol_id   text NOT NULL,
    created_on          timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    scheduled_on        timestamp with time zone,
    processed_on        timestamp with time zone,
    processed_status    text,
    ubl                 text,
    hash                text NOT NULL,
    download_count      bigint NOT NULL DEFAULT 0,
    updated_on          timestamp with time zone,
    access_point        access_point,
    access_point_id     text
);

CREATE TABLE registry (
    peppol_id           text PRIMARY KEY,
    access_point        access_point,
    variables           jsonb
);
