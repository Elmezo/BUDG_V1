-- ============================================
-- Add Created_By column to committee and committee_audit
-- Enables "Created By" display in Committee facet (create/view/list)
-- ============================================

-- committee table: add Created_By (FK to people)
ALTER TABLE committee
  ADD COLUMN Created_By INT(11) DEFAULT NULL AFTER LastUpdate_UserID,
  ADD KEY fk_Committee_CreatedBy (Created_By),
  ADD CONSTRAINT fk_Committee_CreatedBy FOREIGN KEY (Created_By) REFERENCES people (ID);

-- committee_audit table: add Created_By for snapshots
ALTER TABLE committee_audit
  ADD COLUMN Created_By INT(11) DEFAULT NULL AFTER LastUpdate_UserID;
