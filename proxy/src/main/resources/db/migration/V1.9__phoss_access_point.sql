ALTER TYPE proxy.access_point ADD VALUE IF NOT EXISTS 'PHOSS';

-- No FK: the durable identity is committed independently of the scheduler transaction.
-- Retain this tombstone even if an outgoing document is removed, to prevent ID reuse.
-- No invoice payload is duplicated here, including for no-archive documents.
CREATE TABLE proxy.phoss_submission (
    document_id uuid PRIMARY KEY,
    instance_id text NOT NULL UNIQUE,
    fingerprint text NOT NULL,
    created_on timestamptz NOT NULL DEFAULT now(),
    attempted boolean NOT NULL DEFAULT false,
    failure text,
    report text
);
