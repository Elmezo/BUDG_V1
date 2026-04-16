package com.example.budg_v2.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatabaseConnection {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnection.class);
    
    private static volatile HikariDataSource dataSource;

    private static String resolveEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        return defaultValue;
    }

    /**
     * Required non-blank string from env or system property (set by OS env or {@link com.example.budg_v2.util.EnvFileLoader}).
     */
    private static String requireNonBlank(String key) {
        String value = System.getenv(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        throw new IllegalStateException(
                "Required configuration missing: \"" + key + "\". "
                        + "Set it as an environment variable or system property (e.g. from a .env file outside the WAR). "
                        + "See env.example in src/main/resources.");
    }

    /**
     * JDBC password: may be empty (e.g. local MySQL {@code root} with no password). Prefer a strong password in production.
     */
    private static String resolveJdbcPassword() {
        if (System.getenv("DB_PASSWORD") != null) {
            return System.getenv("DB_PASSWORD");
        }
        if (System.getProperty("DB_PASSWORD") != null) {
            return System.getProperty("DB_PASSWORD");
        }
        return "";
    }

    /**
     * Fail fast at startup before any pool is created (called from {@link com.example.budg_v2.listener.ApplicationInitializer}).
     */
    public static void verifyJdbcCredentialsConfigured() {
        requireNonBlank("DB_USERNAME");
    }

    private static void initializePool() {
        if (dataSource == null) {
            synchronized (DatabaseConnection.class) {
                if (dataSource == null) {
                    String dbUrl = resolveEnv("DB_URL", "jdbc:mysql://localhost:3306/project");
                    String dbUser = requireNonBlank("DB_USERNAME");
                    String dbPass = resolveJdbcPassword();

                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(dbUrl);
                    config.setUsername(dbUser);
                    config.setPassword(dbPass);
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    
                    // Pool sizing for 25-50+ concurrent users
                    config.setMaximumPoolSize(50);
                    config.setMinimumIdle(10);
                    
                    // Timeouts
                    config.setConnectionTimeout(30000);   // 30s to get connection from pool
                    config.setIdleTimeout(300000);         // 5min idle before eviction
                    config.setMaxLifetime(540000);         // 9min max connection lifetime
                    config.setKeepaliveTime(240000);       // 4min keepalive ping on idle connections
                    config.setValidationTimeout(5000);     // 5s validation timeout
                    config.setLeakDetectionThreshold(60000); // 60s leak warning
                    
                    // Validation
                    config.setConnectionTestQuery("SELECT 1");
                    
                    // MySQL performance tuning
                    config.addDataSourceProperty("cachePrepStmts", "true");
                    config.addDataSourceProperty("prepStmtCacheSize", "250");
                    config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
                    config.addDataSourceProperty("useServerPrepStmts", "true");
                    config.addDataSourceProperty("useLocalSessionState", "true");
                    config.addDataSourceProperty("rewriteBatchedStatements", "true");
                    config.addDataSourceProperty("cacheResultSetMetadata", "true");
                    config.addDataSourceProperty("cacheServerConfiguration", "true");
                    config.addDataSourceProperty("maintainTimeStats", "false");
                    
                    config.setPoolName("BudgHikariPool");
                    
                    dataSource = new HikariDataSource(config);
                    logger.info("HikariCP pool initialized: maxSize={}, minIdle={}, url={}", 
                               config.getMaximumPoolSize(), config.getMinimumIdle(), dbUrl);
                }
            }
        }
    }

	public static Connection getConnection() throws SQLException {
        initializePool();
        return dataSource.getConnection();
	}

	public static void closeConnection(Connection connection) {
		if (connection != null) {
			try {
                connection.close(); // Returns connection to pool (not actually closed)
			} catch (SQLException e) {
				logger.error("Error closing database connection", e);
			}
		}
	}
    
    /**
     * Shutdown the connection pool. Call on application shutdown.
     */
    public static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
            logger.info("HikariCP pool shut down");
        }
    }
}
