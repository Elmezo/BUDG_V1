-- Per-user layout for main dashboard default widgets and custom dashboard widget order/visibility.
-- Synced by /api/dashboard/layout-prefs (DashboardLayoutPrefsServlet).

CREATE TABLE IF NOT EXISTS user_dashboard_layout_prefs (
  ID INT NOT NULL AUTO_INCREMENT,
  User_ID INT NOT NULL,
  Dashboard_Key VARCHAR(64) NOT NULL COMMENT 'main or numeric dashboard id as string',
  Layout_JSON LONGTEXT NOT NULL,
  Updated_At TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (ID),
  UNIQUE KEY uk_user_dashboard_layout (User_ID, Dashboard_Key),
  CONSTRAINT fk_udlp_user FOREIGN KEY (User_ID) REFERENCES people (ID) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
