-- Fix changerequest_x_document foreign key: reference changerequest(ID) instead of change_request(id)
-- The table change_request does not exist; the actual table is changerequest with primary key ID.

ALTER TABLE changerequest_x_document
  DROP FOREIGN KEY fk_cr_doc_changerequest;

ALTER TABLE changerequest_x_document
  ADD CONSTRAINT fk_cr_doc_changerequest
  FOREIGN KEY (ChangeRequest_ID) REFERENCES changerequest (ID) ON DELETE CASCADE;
