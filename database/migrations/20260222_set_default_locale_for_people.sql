-- Migration script: Add default locale for users where Locale is NULL
-- Default language: English (en)
-- Date: 2026-02-22

-- Set default locale to 'en' for all users with NULL locale
UPDATE people
SET Locale = 'en',
    Last_Updated = NOW()
WHERE Locale IS NULL
  AND Deleted_date IS NULL;

-- Verify the update
SELECT COUNT(*) AS users_with_default_locale
FROM people
WHERE Locale = 'en' AND Deleted_date IS NULL;

-- Show distribution of locales
SELECT Locale, COUNT(*) AS count
FROM people
WHERE Deleted_date IS NULL
GROUP BY Locale
ORDER BY count DESC;
