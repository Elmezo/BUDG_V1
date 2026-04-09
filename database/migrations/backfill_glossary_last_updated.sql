-- ============================================
-- Backfill Last_updated_userID and Last_Updated_Datetime for glossary
-- Rows that were never updated (or created when these were NULL) get
-- creator and created date so "Last Updated By/Date" display correctly.
-- ============================================

UPDATE glossary
SET Last_updated_userID = CreatedBy_ID,
    Last_Updated_Datetime = COALESCE(Last_Updated_Datetime, Created_Datetime)
WHERE (Last_updated_userID IS NULL OR Last_Updated_Datetime IS NULL)
  AND Deleted_datetime IS NULL;
