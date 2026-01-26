CREATE TABLE app_link (
  peppol_id     text NOT NULL,
  linked_uid    uuid NOT NULL,
  PRIMARY KEY (peppol_id, linked_uid)
);

CREATE INDEX idx_peppol_id ON app_link(peppol_id);
CREATE INDEX idx_linked_uid ON app_link(linked_uid);
CREATE INDEX idx_ubl_document_all_new ON ubl_document(owner_peppol_id, download_count, direction, created_on DESC);
