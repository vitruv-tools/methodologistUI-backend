ALTER TABLE vsum_view_meta_model
    ADD CONSTRAINT uk_vsum_view_meta_model UNIQUE (vsum_view_id, meta_model_id);

CREATE INDEX idx_vsum_view_meta_model_vsum_view_id ON vsum_view_meta_model (vsum_view_id);

CREATE INDEX idx_vsum_view_meta_model_meta_model_id ON vsum_view_meta_model (meta_model_id);
