ALTER TABLE vsum_meta_model
    ADD COLUMN name VARCHAR(255);

UPDATE vsum_meta_model
SET name = meta_model.name
FROM meta_model
WHERE vsum_meta_model.meta_model_id = meta_model.id;

ALTER TABLE vsum_meta_model
    ALTER COLUMN name SET NOT NULL;
