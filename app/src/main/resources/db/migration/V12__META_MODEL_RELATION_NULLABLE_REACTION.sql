ALTER TABLE meta_model_relation
    ALTER COLUMN reaction_file_id DROP NOT NULL;

ALTER TABLE meta_model_relation
    DROP CONSTRAINT IF EXISTS uk_vsum_source_target_file;

ALTER TABLE meta_model_relation
    ADD CONSTRAINT uk_vsum_source_target UNIQUE (vsum_id, source_id, target_id);
