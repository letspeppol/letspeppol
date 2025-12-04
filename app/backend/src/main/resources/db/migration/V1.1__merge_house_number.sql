UPDATE app.address
SET street =
        CASE
            WHEN street IS NULL AND house_number IS NULL THEN NULL
            WHEN street IS NULL THEN house_number
            WHEN house_number IS NULL OR house_number = '' THEN street
            ELSE street || ' ' || house_number
            END;

ALTER TABLE app.address
DROP COLUMN house_number;
