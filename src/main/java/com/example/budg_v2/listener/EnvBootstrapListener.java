package com.example.budg_v2.listener;

import com.example.budg_v2.util.EnvFileLoader;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads filesystem {@code .env} into system properties before any other listener touches JDBC.
 * Must be declared first in {@code web.xml}.
 */
public class EnvBootstrapListener implements ServletContextListener {

    private static final Logger logger = LoggerFactory.getLogger(EnvBootstrapListener.class);

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        EnvFileLoader.loadEnvFile();
        logger.info("Environment bootstrap: .env loaded (if present) before other listeners");
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // no-op
    }
}
