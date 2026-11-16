ALTER TABLE vsum_view
    ADD COLUMN name VARCHAR(255);

ALTER TABLE vsum_view
    ADD COLUMN description TEXT;

-- Existing views are named after their NeoJoin file without the extension, followed by their id,
-- which keeps the names unique within a VSUM.
UPDATE vsum_view
SET name = (SELECT LEFT(CASE
                            WHEN file_storage.filename LIKE '%.nj'
                                THEN LEFT(file_storage.filename, LENGTH(file_storage.filename) - 3)
                            ELSE file_storage.filename
                            END, 200)
            FROM file_storage
            WHERE file_storage.id = vsum_view.file_storage_id) || ' (' || id || ')';

ALTER TABLE vsum_view
    ALTER COLUMN name SET NOT NULL;

-- Deferred to the end of the transaction, so that one save can rename views in any order, e.g.
-- swap two names or give a view the name of a view it deletes.
ALTER TABLE vsum_view
    ADD CONSTRAINT uk_vsum_view_vsum_name UNIQUE (vsum_id, name) DEFERRABLE INITIALLY DEFERRED;
