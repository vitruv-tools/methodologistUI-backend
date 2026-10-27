-- Store file contents in the row itself instead of as PostgreSQL large objects. Large objects are
-- not freed when their row is deleted or its content is overwritten, so they piled up as orphans.
ALTER TABLE file_storage
    ALTER COLUMN data TYPE BYTEA USING lo_get(data);

-- Free all large objects: the ones just copied into file_storage and the ones already orphaned.
-- file_storage.data was the only column storing large objects.
SELECT lo_unlink(oid)
FROM pg_largeobject_metadata;
