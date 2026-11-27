-- Marks the snapshots taken before a writeback of the collaboration server. A writeback that changes
-- more than the layout takes one unless the project's latest snapshot is such a snapshot and younger
-- than vsum.history.collaboration-interval, so the first such writeback after a user's change or a
-- revert still records the state right after it.
ALTER TABLE vsum_history
    ADD COLUMN collaboration_writeback BOOLEAN NOT NULL DEFAULT FALSE;

-- Every writeback that changes more than the layout reads the project's latest snapshot; the history
-- limit counts the snapshots and deletes the oldest.
CREATE INDEX idx_vsum_history_vsum_id_created_at ON vsum_history (vsum_id, created_at DESC);
