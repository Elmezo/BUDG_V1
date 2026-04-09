package com.example.budg_v2.database;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ElasticsearchConfig {

    private static final Logger logger = LoggerFactory.getLogger(ElasticsearchConfig.class);

    private static RestClient restClient;
    private static ElasticsearchTransport transport;
    private static ElasticsearchClient client;

    private static final String HOST = "localhost";
    private static final int PORT = 9200;
    private static final String SCHEME = "http";

    private ElasticsearchConfig() {}

    public static synchronized ElasticsearchClient getClient() {
        if (client == null) {
            try {
                logger.info("Initializing Elasticsearch client...");

                restClient = RestClient.builder(new HttpHost(HOST, PORT, SCHEME))
                        .setRequestConfigCallback(requestConfigBuilder ->
                                requestConfigBuilder
                                        .setConnectTimeout(10000)   // 10 seconds
                                        .setSocketTimeout(60000)    // 60 seconds
                        )
                        .build();

                transport = new RestClientTransport(restClient, new JacksonJsonpMapper(new JsonMapper()));
                client = new ElasticsearchClient(transport);

                logger.info("Elasticsearch client initialized successfully. Host: {} Port: {}", HOST, PORT);
            } catch (Exception e) {
                logger.error("Failed to initialize Elasticsearch client", e);
                throw new RuntimeException("Elasticsearch client initialization failed", e);
            }
        }
        return client;
    }

    public static synchronized void closeConnections() {
        try {
            logger.info("Closing Elasticsearch connections...");

            if (transport != null) {
                transport.close();
                transport = null;
                logger.debug("Transport closed successfully.");
            }

            if (restClient != null) {
                restClient.close();
                restClient = null;
                logger.debug("RestClient closed successfully.");
            }

            client = null;

            logger.info("Elasticsearch connections closed successfully.");
        } catch (IOException e) {
            logger.error("Error while closing Elasticsearch connections", e);
        }
    }
}
