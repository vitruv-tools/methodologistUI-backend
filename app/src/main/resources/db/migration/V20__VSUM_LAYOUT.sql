-- Canvas layout of a project, stored and returned as-is (positions keyed by meta-model id).
ALTER TABLE vsum
    ADD COLUMN layout JSONB;
