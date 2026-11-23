-- What the canvas showed when a version was published: the project as GET /vsums/{id}/details
-- returns it, plus its rule sets, with every file id pointing at a copy of the version. The build
-- copies cannot serve this, because a build merges the reaction files of a pair into one. NULL for
-- versions published before the content was stored.
ALTER TABLE vsum_version
    ADD COLUMN content JSONB;

-- The copies the content refers to. A copy of a file that is also a build input is shared with the
-- build's copy tables.
CREATE TABLE vsum_version_content_file
(
    version_id      BIGINT  NOT NULL,
    file_storage_id BIGINT  NOT NULL,
    file_order      INTEGER NOT NULL,
    CONSTRAINT pk_vsum_version_content_file PRIMARY KEY (version_id, file_order),
    CONSTRAINT FK_VSUMVERSIONCONTENTFILE_ON_VERSION FOREIGN KEY (version_id) REFERENCES vsum_version (id),
    CONSTRAINT FK_VSUMVERSIONCONTENTFILE_ON_FILE FOREIGN KEY (file_storage_id) REFERENCES file_storage (id)
);
