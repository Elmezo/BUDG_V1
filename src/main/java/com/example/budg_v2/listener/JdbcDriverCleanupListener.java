package com.example.budg_v2.listener;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

/**
 * ServletContextListener to properly cleanup JDBC drivers during application shutdown.
 * This prevents memory leaks when Tomcat reloads the application.
 */
@WebListener
public class JdbcDriverCleanupListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(JdbcDriverCleanupListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        logger.info("JDBC Driver Cleanup Listener initialized");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        logger.info("Starting JDBC driver cleanup...");
        
        try {
            // Deregister all JDBC drivers
            Enumeration<Driver> drivers = DriverManager.getDrivers();
            while (drivers.hasMoreElements()) {
                Driver driver = drivers.nextElement();
                try {
                    DriverManager.deregisterDriver(driver);
                    logger.info("Deregistered JDBC driver: {}", driver.getClass().getName());
                } catch (SQLException e) {
                    logger.warn("Failed to deregister JDBC driver: {}", driver.getClass().getName(), e);
                }
            }
            
            // Force cleanup of MySQL abandoned connection cleanup thread
            try {
                Class.forName("com.mysql.cj.jdbc.AbandonedConnectionCleanupThread");
                com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
                logger.info("MySQL abandoned connection cleanup thread shutdown completed");
            } catch (ClassNotFoundException e) {
                logger.debug("MySQL abandoned connection cleanup thread not found (not using MySQL driver)");
            } catch (Exception e) {
                logger.warn("Failed to shutdown MySQL abandoned connection cleanup thread", e);
            }
            
        } catch (Exception e) {
            logger.error("Error during JDBC driver cleanup", e);
        }
        
        logger.info("JDBC driver cleanup completed");
    }
}
