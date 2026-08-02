WITH converted AS (
    SELECT
        id,
        ubl::oid AS old_oid,
        convert_from(lo_get(ubl::oid), 'UTF8') AS ubl_text
    FROM document
    WHERE ubl ~ '^[0-9]+$'
    AND EXISTS (
        SELECT 1
        FROM pg_largeobject_metadata lom
        WHERE lom.oid = ubl::oid
    )
),
updated AS (
    UPDATE document d
    SET ubl = c.ubl_text
    FROM converted c
    WHERE d.id = c.id
    RETURNING c.old_oid
)
SELECT lo_unlink(old_oid)
FROM (SELECT DISTINCT old_oid FROM updated) x;
