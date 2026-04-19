ALTER TABLE document ADD COLUMN created_externally boolean DEFAULT true NOT NULL;

UPDATE document SET created_externally = false WHERE direction = 'OUTGOING';
