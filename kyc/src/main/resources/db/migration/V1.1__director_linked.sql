ALTER TABLE director ADD registered boolean NOT NULL DEFAULT FALSE;
UPDATE director SET registered = true WHERE 1=1;
