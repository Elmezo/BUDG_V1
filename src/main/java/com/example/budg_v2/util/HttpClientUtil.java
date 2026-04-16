package com.example.budg_v2.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for making HTTP requests to external services
 */
public class HttpClientUtil {

    private static final Logger logger = LoggerFactory.getLogger(HttpClientUtil.class);
    private static final Gson gson = new Gson();

    /** Connection timeout in milliseconds (e.g. 15 seconds). */
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    /** Socket timeout in milliseconds (e.g. 5 minutes for validation). */
    private static final int SOCKET_TIMEOUT_MS = 300_000;

    /** Default socket timeout for bulk validation calls (90 seconds) to avoid indefinite loading. */
    public static final int BULK_VALIDATION_SOCKET_TIMEOUT_MS = 90_000;

    /**
     * When {@code BULK_VALIDATION_API_KEY} is unset, Java and Python use this for local dev (matches {@code env.example}).
     */
    public static final String BULK_VALIDATION_API_KEY_DEV_DEFAULT = "BUDG_DEV_BULK_VALIDATION_LOCALHOST_ONLY";

    /**
     * Send POST request with JSON body
     * 
     * @param url The target URL
     * @param jsonBody The JSON body to send
     * @return Response body as String
     * @throws IOException if request fails
     */
    public static String postJson(String url, String jsonBody) throws IOException {
        return postJson(url, jsonBody, CONNECT_TIMEOUT_MS, SOCKET_TIMEOUT_MS);
    }

    /**
     * Send POST request with JSON body and custom timeouts.
     *
     * @param url The target URL
     * @param jsonBody The JSON body to send
     * @param connectTimeoutMs Connect timeout in milliseconds
     * @param socketTimeoutMs Socket (read) timeout in milliseconds
     * @return Response body as String
     * @throws IOException if request fails (e.g. timeout, connection refused)
     */
    private static String bulkValidationApiKey() {
        String k = System.getenv("BULK_VALIDATION_API_KEY");
        if (k != null && !k.trim().isEmpty()) {
            return k.trim();
        }
        k = System.getProperty("BULK_VALIDATION_API_KEY");
        if (k != null && !k.trim().isEmpty()) {
            return k.trim();
        }
        return BULK_VALIDATION_API_KEY_DEV_DEFAULT;
    }

    private static void addBulkValidationApiKeyHeader(HttpPost httpPost, String url) {
        if (url != null && url.contains("/api/validate")) {
            httpPost.setHeader("X-API-Key", bulkValidationApiKey());
        }
    }

    public static String postJson(String url, String jsonBody, int connectTimeoutMs, int socketTimeoutMs) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .build();
        try (CloseableHttpClient client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build()) {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setHeader("Accept", "application/json");
            addBulkValidationApiKeyHeader(httpPost, url);
            
            StringEntity entity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
            httpPost.setEntity(entity);
            
            logger.info("Sending POST request to: {}", url);
            logger.debug("Request body: {}", jsonBody);
            
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                logger.info("Response status: {}", statusCode);
                logger.debug("Response body: {}", responseBody);
                
                if (statusCode >= 200 && statusCode < 300) {
                    return responseBody;
                } else {
                    throw new IOException(String.format(
                        "HTTP request failed with status %d: %s", 
                        statusCode, 
                        responseBody
                    ));
                }
            }
        }
    }

    /**
     * Send POST request with JSON object
     * 
     * @param url The target URL
     * @param jsonObject The JSON object to send
     * @return Response body as String
     * @throws IOException if request fails
     */
    public static String postJson(String url, JsonObject jsonObject) throws IOException {
        String jsonBody = gson.toJson(jsonObject);
        return postJson(url, jsonBody);
    }

    /**
     * Send POST request and parse response as JsonObject
     * 
     * @param url The target URL
     * @param jsonBody The JSON body to send
     * @return Response as JsonObject
     * @throws IOException if request fails
     */
    public static JsonObject postJsonGetJson(String url, String jsonBody) throws IOException {
        String responseBody = postJson(url, jsonBody);
        return gson.fromJson(responseBody, JsonObject.class);
    }

    /**
     * Send POST request with JSON object and parse response as JsonObject
     * 
     * @param url The target URL
     * @param jsonObject The JSON object to send
     * @return Response as JsonObject
     * @throws IOException if request fails
     */
    public static JsonObject postJsonGetJson(String url, JsonObject jsonObject) throws IOException {
        String jsonBody = gson.toJson(jsonObject);
        return postJsonGetJson(url, jsonBody);
    }

    /**
     * Send POST request with JSON object and parse response as JsonObject, with custom socket timeout.
     * Use for bulk validation so the request fails in a reasonable time (e.g. 90s) instead of default 5 minutes.
     *
     * @param url The target URL
     * @param jsonObject The JSON object to send
     * @param socketTimeoutMs Socket (read) timeout in milliseconds
     * @return Response as JsonObject
     * @throws IOException if request fails (e.g. timeout, connection refused)
     */
    public static JsonObject postJsonGetJson(String url, JsonObject jsonObject, int socketTimeoutMs) throws IOException {
        String jsonBody = gson.toJson(jsonObject);
        String responseBody = postJson(url, jsonBody, CONNECT_TIMEOUT_MS, socketTimeoutMs);
        return gson.fromJson(responseBody, JsonObject.class);
    }

    /**
     * Send GET request with Basic Authentication
     * 
     * @param url The target URL
     * @param username Username for Basic Auth
     * @param password Password for Basic Auth
     * @param timeout Timeout in seconds (0 or negative for default)
     * @param proxyHost Proxy host (null if no proxy)
     * @param proxyPort Proxy port (null if no proxy)
     * @param sslInsecure If true, skip SSL certificate validation (dev/testing only)
     * @return Response body as String
     * @throws IOException if request fails
     */
    public static String getJsonWithBasicAuth(String url, String username, String password, 
                                               int timeout, String proxyHost, Integer proxyPort, 
                                               boolean sslInsecure) throws IOException {
        CloseableHttpClient client = null;
        try {
            // Build HTTP client with SSL and proxy configuration
            client = createHttpClient(sslInsecure, proxyHost, proxyPort);
            
            HttpGet httpGet = new HttpGet(url);
            httpGet.setHeader("Accept", "application/json");
            
            // Add Basic Auth header
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            httpGet.setHeader("Authorization", "Basic " + encodedAuth);
            
            // Set timeout
            RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
            if (timeout > 0) {
                requestConfigBuilder.setConnectTimeout(timeout * 1000)
                                   .setSocketTimeout(timeout * 1000)
                                   .setConnectionRequestTimeout(timeout * 1000);
            }
            httpGet.setConfig(requestConfigBuilder.build());
            
            logger.info("Sending GET request to: {}", url);
            logger.debug("Using Basic Auth for user: {}", username);
            if (sslInsecure) {
                logger.warn("SSL certificate validation is DISABLED (insecure mode) - for dev/testing only!");
            }
            
            try (CloseableHttpResponse response = client.execute(httpGet)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                logger.info("Response status: {}", statusCode);
                logger.debug("Response body: {}", responseBody);
                
                if (statusCode >= 200 && statusCode < 300) {
                    return responseBody;
                } else {
                    throw new IOException(String.format(
                        "HTTP request failed with status %d: %s", 
                        statusCode, 
                        responseBody
                    ));
                }
            }
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    logger.warn("Error closing HTTP client", e);
                }
            }
        }
    }

    /**
     * Send GET request with Basic Authentication and parse response as JsonObject
     * 
     * @param url The target URL
     * @param username Username for Basic Auth
     * @param password Password for Basic Auth
     * @param timeout Timeout in seconds (0 or negative for default)
     * @param proxyHost Proxy host (null if no proxy)
     * @param proxyPort Proxy port (null if no proxy)
     * @param sslInsecure If true, skip SSL certificate validation (dev/testing only)
     * @return Response as JsonObject
     * @throws IOException if request fails
     */
    public static JsonObject getJsonObjectWithBasicAuth(String url, String username, String password,
                                                        int timeout, String proxyHost, Integer proxyPort,
                                                        boolean sslInsecure) throws IOException {
        String responseBody = getJsonWithBasicAuth(url, username, password, timeout, proxyHost, proxyPort, sslInsecure);
        return gson.fromJson(responseBody, JsonObject.class);
    }

    /**
     * Send POST request with JSON body and Basic Authentication
     * 
     * @param url The target URL
     * @param jsonBody The JSON body to send (as String)
     * @param username Username for Basic Auth
     * @param password Password for Basic Auth
     * @param timeout Timeout in seconds (0 or negative for default)
     * @param proxyHost Proxy host (null if no proxy)
     * @param proxyPort Proxy port (null if no proxy)
     * @param sslInsecure If true, skip SSL certificate validation (dev/testing only)
     * @return Response body as String
     * @throws IOException if request fails
     */
    public static String postJsonWithBasicAuth(String url, String jsonBody, String username, String password,
                                               int timeout, String proxyHost, Integer proxyPort,
                                               boolean sslInsecure) throws IOException {
        CloseableHttpClient client = null;
        try {
            // Build HTTP client with SSL and proxy configuration
            client = createHttpClient(sslInsecure, proxyHost, proxyPort);
            
            HttpPost httpPost = new HttpPost(url);
            httpPost.setHeader("Accept", "application/json");
            httpPost.setHeader("Content-Type", "application/json");
            
            // Add Basic Auth header
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            httpPost.setHeader("Authorization", "Basic " + encodedAuth);
            
            // Set request body
            httpPost.setEntity(new StringEntity(jsonBody, StandardCharsets.UTF_8));
            
            // Set timeout
            RequestConfig.Builder requestConfigBuilder = RequestConfig.custom();
            if (timeout > 0) {
                requestConfigBuilder.setConnectTimeout(timeout * 1000)
                                   .setSocketTimeout(timeout * 1000)
                                   .setConnectionRequestTimeout(timeout * 1000);
            }
            httpPost.setConfig(requestConfigBuilder.build());
            
            logger.info("Sending POST request to: {}", url);
            logger.debug("Using Basic Auth for user: {}", username);
            if (sslInsecure) {
                logger.warn("SSL certificate validation is DISABLED (insecure mode) - for dev/testing only!");
            }
            
            try (CloseableHttpResponse response = client.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                
                logger.info("Response status: {}", statusCode);
                logger.debug("Response body: {}", responseBody);
                
                if (statusCode >= 200 && statusCode < 300) {
                    return responseBody;
                } else {
                    throw new IOException("HTTP " + statusCode + ": " + responseBody);
                }
            }
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException e) {
                    logger.warn("Failed to close HTTP client", e);
                }
            }
        }
    }
    
    /**
     * Send POST request with JSON object and Basic Authentication, return as JsonObject
     * 
     * @param url The target URL
     * @param jsonObject The JSON object to send
     * @param username Username for Basic Auth
     * @param password Password for Basic Auth
     * @param timeout Timeout in seconds (0 or negative for default)
     * @param proxyHost Proxy host (null if no proxy)
     * @param proxyPort Proxy port (null if no proxy)
     * @param sslInsecure If true, skip SSL certificate validation (dev/testing only)
     * @return Response as JsonObject
     * @throws IOException if request fails
     */
    public static JsonObject postJsonGetJson(String url, JsonObject jsonObject, String username, String password,
                                            int timeout, String proxyHost, Integer proxyPort,
                                            boolean sslInsecure) throws IOException {
        String jsonBody = gson.toJson(jsonObject);
        String responseBody = postJsonWithBasicAuth(url, jsonBody, username, password, timeout, proxyHost, proxyPort, sslInsecure);
        return gson.fromJson(responseBody, JsonObject.class);
    }
    
    /**
     * Send POST request with JSON string and Basic Authentication, return as JsonObject
     * 
     * @param url The target URL
     * @param jsonBody The JSON body as String
     * @param username Username for Basic Auth
     * @param password Password for Basic Auth
     * @param timeout Timeout in seconds (0 or negative for default)
     * @param proxyHost Proxy host (null if no proxy)
     * @param proxyPort Proxy port (null if no proxy)
     * @param sslInsecure If true, skip SSL certificate validation (dev/testing only)
     * @return Response as JsonObject
     * @throws IOException if request fails
     */
    public static JsonObject postJsonGetJson(String url, String jsonBody, String username, String password,
                                            int timeout, String proxyHost, Integer proxyPort,
                                            boolean sslInsecure) throws IOException {
        String responseBody = postJsonWithBasicAuth(url, jsonBody, username, password, timeout, proxyHost, proxyPort, sslInsecure);
        return gson.fromJson(responseBody, JsonObject.class);
    }
    
    /**
     * Create HTTP client with SSL and proxy configuration
     * 
     * @param sslInsecure If true, skip SSL certificate validation
     * @param proxyHost Proxy host (null if no proxy)
     * @param proxyPort Proxy port (null if no proxy)
     * @return Configured HTTP client
     * @throws IOException if SSL context creation fails
     */
    private static CloseableHttpClient createHttpClient(boolean sslInsecure, String proxyHost, Integer proxyPort) throws IOException {
        try {
            org.apache.http.impl.client.HttpClientBuilder clientBuilder = HttpClients.custom();
            
            // Configure SSL
            if (sslInsecure) {
                // Insecure mode: trust all certificates (dev/testing only)
                SSLContext sslContext = SSLContextBuilder.create()
                    .loadTrustMaterial(new TrustAllStrategy())
                    .build();
                
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE
                );
                
                clientBuilder.setSSLSocketFactory(sslSocketFactory);
            }
            // Otherwise, use default SSL (truststore-based)
            
            // Configure proxy
            if (proxyHost != null && !proxyHost.trim().isEmpty() && proxyPort != null && proxyPort > 0) {
                HttpHost proxy = new HttpHost(proxyHost, proxyPort);
                clientBuilder.setProxy(proxy);
                logger.debug("Using proxy: {}:{}", proxyHost, proxyPort);
            }
            
            return clientBuilder.build();
            
        } catch (Exception e) {
            logger.error("Failed to create HTTP client", e);
            throw new IOException("Failed to create HTTP client: " + e.getMessage(), e);
        }
    }
}


