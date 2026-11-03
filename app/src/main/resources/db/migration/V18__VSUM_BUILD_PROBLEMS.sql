-- Structured outcome of failed builds: the error code of the setup-service and the problems it
-- found in the uploaded files, as a JSON array (see BuildProblem).
ALTER TABLE vsum_build
    ADD COLUMN error_code VARCHAR(64);

ALTER TABLE vsum_build
    ADD COLUMN problems JSONB;
