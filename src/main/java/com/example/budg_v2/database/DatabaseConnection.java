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
            return value;
        }
        value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value;
        }
        return defaultValue;
    }

    private static final String DB_URL = resolveEnv("DB_URL", "jdbc:mysql://localhost:3306/project");
    private static final String DB_USER = resolveEnv("DB_USERNAME", "root");
    private static final String DB_PASS = resolveEnv("DB_PASSWORD", "");
    
    private static void initializePool() {
        if (dataSource == null) {
            synchronized (DatabaseConnection.class) {
                if (dataSource == null) {
                    HikariConfig config = new HikariConfig();
                    config.setJdbcUrl(DB_URL);
                    config.setUsername(DB_USER);
                    config.setPassword(DB_PASS);
                    config.setDriverClassName("com.mysql.cj.jdbc.Driver");
                    
                    // Pool sizing for 25-50+ concurrent users
                    config.setMaximumPoolSize(50);
                    config.setMinimumIdle(10);
                    
                    // Timeouts
                    config.setConnectionTimeout(30000);   // 30s to get connection from pool
                    // Must be < maxLifetime or Hikari disables idle timeout (warns at init)
                    config.setIdleTimeout(300000);         // 5min idle before eviction
                    // Keep lifetime below common MySQL/proxy idle-closing windows
                    // to avoid "No operations allowed after connection closed."
                    config.setMaxLifetime(540000);         // 9min max connection lifetime
                    config.setKeepaliveTime(240000);       // 4min keepalive ping on idle connections
                    config.setValidationTimeout(5000);     // 5s validation timeout
                    config.setLeakDetectionThreshold(); // 60s leak warning60000
                    
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
                               config.getMaximumPoolSize(), config.getMinimumIdle(), DB_URL);
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
