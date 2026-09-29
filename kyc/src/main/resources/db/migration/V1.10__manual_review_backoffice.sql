SET search_path = kyc;

CREATE TYPE permission AS ENUM ('REVIEW_REGISTRATIONS');

CREATE TABLE account_permission (
    account_id bigint     NOT NULL,
    permission permission NOT NULL,
    granted_on timestamp with time zone NOT NULL DEFAULT now(),
    PRIMARY KEY (account_id, permission),
    CONSTRAINT fk_account_permission_account
        FOREIGN KEY (account_id) REFERENCES account(id) ON DELETE CASCADE
);

CREATE TYPE review_status AS ENUM ('NOT_REQUIRED', 'PENDING', 'APPROVED', 'REJECTED');

ALTER TABLE director_identity_verification
    ADD COLUMN requested_type account_type,
    ADD COLUMN review_status  review_status NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN reviewed_by    bigint,
    ADD COLUMN reviewed_on    timestamp with time zone,
    ADD CONSTRAINT fk_director_identity_verification_reviewed_by
        FOREIGN KEY (reviewed_by) REFERENCES account(id);

CREATE INDEX idx_director_identity_verification_review_status ON director_identity_verification(review_status);

UPDATE director_identity_verification div
SET review_status = 'PENDING'
FROM director d
WHERE d.id = div.director_id
  AND NOT EXISTS (
      SELECT 1 FROM ownership o
      WHERE o.account_id = div.account_id
        AND o.company_id = d.company_id
  );

UPDATE director_identity_verification div
SET requested_type = ev.type
FROM account a, director d, company c,
     LATERAL (
         SELECT e.type FROM email_verification e
         WHERE lower(e.email) = a.email
           AND e.peppol_id = c.peppol_id
           AND e.type IS NOT NULL
         ORDER BY e.created_on DESC
         LIMIT 1
     ) ev
WHERE div.review_status = 'PENDING'
  AND a.id = div.account_id
  AND d.id = div.director_id
  AND c.id = d.company_id;
