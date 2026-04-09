package com.example.budg_v2.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.RefreshRequest;
import com.example.budg_v2.database.DatabaseConnection;
import com.example.budg_v2.database.ElasticsearchConfig;

import java.io.IOException;
import java.io.StringReader;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

public class SearchService {

    private volatile boolean synced = false;

    public static final String INDEX_ORG_UNIT = "org_unit";
    public static final String INDEX_PEOPLE = "people";
    public static final String INDEX_STATUS = "status";
    public static final String INDEX_SYSTEM = "system";
    public static final String INDEX_DATASET = "dataset";
    public static final String INDEX_INTERFACE = "interface";
    public static final String INDEX_GLOSSARY = "glossary";
    public static final String INDEX_PROCESS = "process";
    public static final String INDEX_PROJECT = "project";
    public static final String INDEX_PRODUCT = "product";
    public static final String INDEX_POLICY = "policy";
    public static final String INDEX_BUSINESS_AREA = "business_area";
    public static final String INDEX_CAPABILITY = "capability";
    public static final String INDEX_legal = "legal";
    public static final String INDEX_CLIENT = "client";
    public static final String INDEX_COMMITTEE = "committee";
    public static final String INDEX_ATTRIBUTE = "attribute";
    public static final String INDEX_GEOGRAPHY = "geography";
    public static final String INDEX_REGULATION = "regulation";
    public static final String INDEX_REGULATOR = "regulator";
    public static final String INDEX_REGULATORY_THEME = "regulatory_theme";
    public static final String INDEX_DATAQUALITY = "dataquality";

    // Updated from EDITOR - changed from record to class for compatibility
    private static final class ModuleConfig {
        final String index;
        final String sql;
        final List<String> fieldsForSearch;
        final List<String> fieldsForReturn;
        final SQLMapper mapper;
        ModuleConfig(String index, String sql, List<String> fieldsForSearch, List<String> fieldsForReturn, SQLMapper mapper) {
            this.index = index; this.sql = sql; this.fieldsForSearch = fieldsForSearch; this.fieldsForReturn = fieldsForReturn; this.mapper = mapper;
        }
    }

    private static final Map<String, ModuleConfig> REGISTRY;
    static {
        Map<String, ModuleConfig> r = new LinkedHashMap<>();
        r.put("Org Unit", new ModuleConfig(
                INDEX_ORG_UNIT,
                "SELECT ID, Name, Reference FROM org_unit WHERE deleted_Date IS NULL",
                Arrays.asList("Name", "Reference"), Collections.singletonList("Name"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("Name", rs.getString("Name"));
                    map.put("Reference", rs.getString("Reference"));
                    return map;
                }));
        r.put("People", new ModuleConfig(
                INDEX_PEOPLE,
                "SELECT ID, first_name, last_name FROM people WHERE Deleted_date IS NULL",
                Arrays.asList("First_Name", "Last_Name"), Arrays.asList("First_Name", "Last_Name"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("First_Name", rs.getString("first_name"));
                    map.put("Last_Name", rs.getString("last_name"));
                    return map;
                }));
        r.put("System", new ModuleConfig(
                INDEX_SYSTEM,
                "SELECT id, Name, Long_Name, Description, AssetID FROM system WHERE Deleted_datetime IS NULL",
                Arrays.asList("Name", "Long_Name", "Description", "AssetID"), Arrays.asList("Name", "Long_Name", "Description", "AssetID"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("id"));
                    map.put("Name", rs.getString("Name"));
                    map.put("Long_Name", rs.getString("Long_Name"));
                    map.put("Description", rs.getString("Description"));
                    map.put("AssetID", rs.getString("AssetID"));
                    return map;
                }));
        r.put("Data Sets", new ModuleConfig(
                INDEX_DATASET,
                "SELECT ID, PrimaryName, RefNumber FROM dataset WHERE DeletedDatetime IS NULL",
                Arrays.asList("PrimaryName", "RefNumber"), Arrays.asList("PrimaryName", "RefNumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("RefNumber", rs.getString("RefNumber"));
                    return map;
                }));
        r.put("Interface", new ModuleConfig(
                INDEX_INTERFACE,
                "SELECT id, Name, Ref_number, Asset_ID FROM interface WHERE deleted_datetime IS NULL",
                Arrays.asList("Name", "Ref_number", "Asset_ID"), Arrays.asList("Name", "Ref_number", "Asset_ID"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("id"));
                    map.put("Name", rs.getString("Name"));
                    map.put("Ref_number", rs.getString("Ref_number"));
                    map.put("Asset_ID", rs.getString("Asset_ID"));
                    return map;
                }));
        r.put("Glossary", new ModuleConfig(
                INDEX_GLOSSARY,
                "SELECT g.ID, g.Name, g.Description, g.Ref_Number, IFNULL(GROUP_CONCAT(gan.Name SEPARATOR ' '), '') AS Aliases\n" +
                        "FROM glossary g\n" +
                        "LEFT JOIN glossary_alias_names gan ON gan.Glossary_id = g.ID\n" +
                        "WHERE g.Deleted_datetime IS NULL\n" +
                        "GROUP BY g.ID, g.Name, g.Description, g.Ref_Number",
                Arrays.asList("Name", "Description", "Aliases", "Ref_Number"), Arrays.asList("Name", "Description", "Aliases", "Ref_Number"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("Name", rs.getString("Name"));
                    map.put("Description", rs.getString("Description"));
                    map.put("Aliases", rs.getString("Aliases"));
                    map.put("Ref_Number", rs.getString("Ref_Number"));
                    return map;
                }));
        r.put("Process", new ModuleConfig(
                INDEX_PROCESS,
                "SELECT id, primaryname, refnumber FROM process WHERE deleteddatetime IS NULL",
                Arrays.asList("primaryname", "refnumber"), Arrays.asList("primaryname", "refnumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("id"));
                    map.put("primaryname", rs.getString("primaryname"));
                    map.put("refnumber", rs.getString("refnumber"));
                    return map;
                }));
        r.put("Project", new ModuleConfig(
                INDEX_PROJECT,
                "SELECT id, primaryname, refnumber FROM project WHERE deletedatetime IS NULL",
                Arrays.asList("primaryname", "refnumber"), Arrays.asList("primaryname", "refnumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("id"));
                    map.put("primaryname", rs.getString("primaryname"));
                    map.put("refnumber", rs.getString("refnumber"));
                    return map;
                }));
        r.put("Product", new ModuleConfig(
                INDEX_PRODUCT,
                "SELECT id, primaryname, longname, refnumber FROM product WHERE deleteddatetime IS NULL",
                Arrays.asList("primaryname", "longname", "refnumber"), Arrays.asList("primaryname", "longname", "refnumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("id"));
                    map.put("primaryname", rs.getString("primaryname"));
                    map.put("longname", rs.getString("longname"));
                    map.put("refnumber", rs.getString("refnumber"));
                    return map;
                }));
        r.put("Policy", new ModuleConfig(
                INDEX_POLICY,
                "SELECT ID, PrimaryName, refNumber FROM policy WHERE DeletedDatetime IS NULL",
                Arrays.asList("PrimaryName", "refNumber"), Arrays.asList("PrimaryName", "refNumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("refNumber", rs.getString("refNumber"));
                    return map;
                }));
        // Added from EDITOR - Business Area search configuration
        r.put("Business Area", new ModuleConfig(
                INDEX_BUSINESS_AREA,
                "SELECT ID, PrimaryName, Description FROM business_area WHERE deletedatetime IS NULL",
                Arrays.asList("PrimaryName", "Description"), Arrays.asList("PrimaryName", "Description"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("Description", rs.getString("Description"));
                    return map;
                }));
        // Added from EDITOR - Capability search configuration
        r.put("Capability", new ModuleConfig(
                INDEX_CAPABILITY,
                "SELECT ID, PrimaryName, Description FROM capability WHERE DeletedDatetime IS NULL",
                Arrays.asList("PrimaryName", "Description"), Arrays.asList("PrimaryName", "Description"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("Description", rs.getString("Description"));
                    return map;
                }));
        r.put("Legal Entity", new ModuleConfig(
                INDEX_legal,
                "SELECT ID, ShortName, LongName FROM legal WHERE DeleteDatetime IS NULL",
                Arrays.asList("ShortName", "LongName"),Arrays.asList("ShortName", "LongName"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("ShortName", rs.getString("ShortName"));
                    map.put("LongName", rs.getString("LongName"));
                    return map;
                }));
        r.put("Client", new ModuleConfig(
                INDEX_CLIENT,
                "SELECT ID, PrimaryName, LongName FROM client WHERE DeleteDatetime IS NULL",
                Arrays.asList("PrimaryName", "LongName"), Arrays.asList("PrimaryName", "LongName"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("LongName", rs.getString("LongName"));
                    return map;
                }));
        r.put("Committee", new ModuleConfig(
                INDEX_COMMITTEE,
                "SELECT ID, PrimaryName, RefNumber FROM committee WHERE DeleteDatetime IS NULL",
                Arrays.asList("PrimaryName", "RefNumber"),Arrays.asList("PrimaryName", "RefNumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("RefNumber", rs.getString("RefNumber"));

                    return map;
                }));
        r.put("Attribute", new ModuleConfig(
                INDEX_ATTRIBUTE,
                "SELECT ID, PrimaryName, RefNumber FROM attribute WHERE DeletedDatetime IS NULL",
                Arrays.asList("PrimaryName", "RefNumber"), Arrays.asList("PrimaryName", "RefNumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("RefNumber", rs.getString("RefNumber"));
                    return map;
                }));
        r.put("Geography", new ModuleConfig(
                INDEX_GEOGRAPHY,
                "SELECT ID, PrimaryName FROM geography WHERE DeletedDatetime IS NULL",
                Arrays.asList("PrimaryName"), Arrays.asList("PrimaryName"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    return map;
                }));
        r.put("Regulation", new ModuleConfig(
                INDEX_REGULATION,
                "SELECT ID, primaryName AS Name, RefNumber AS Ref FROM regulation WHERE DeletedDatetime IS NULL",
                Arrays.asList("Name", "Ref"), Arrays.asList("Name", "Ref"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("Name", rs.getString("Name"));
                    map.put("Ref", rs.getString("Ref"));
                    return map;
                }));
        r.put("Regulator", new ModuleConfig(
                INDEX_REGULATOR,
                "SELECT ID, PrimaryName, ShortName FROM regulator WHERE DeletedDatetime IS NULL",
                Arrays.asList("PrimaryName", "ShortName"), Arrays.asList("PrimaryName", "ShortName"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("ShortName", rs.getString("ShortName"));
                    return map;
                }));
        r.put("Regulatory Theme", new ModuleConfig(
                INDEX_REGULATORY_THEME,
                "SELECT ID, PrimaryName, RefNumber FROM regulatorytheme WHERE DeletedDatetime IS NULL",
                Arrays.asList("PrimaryName", "RefNumber"), Arrays.asList("PrimaryName", "RefNumber"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("PrimaryName", rs.getString("PrimaryName"));
                    map.put("RefNumber", rs.getString("RefNumber"));
                    return map;
                }));
        r.put("Data Quality", new ModuleConfig(
                INDEX_DATAQUALITY,
                "SELECT ID, primaryname, ref FROM data_quality WHERE DeletedDatetime IS NULL",
                Arrays.asList("primaryname", "ref"), Arrays.asList("primaryname", "ref"),
                rs -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("ID", rs.getInt("ID"));
                    map.put("primaryname", rs.getString("primaryname"));
                    map.put("ref", rs.getString("ref"));
                    return map;
                }));
        REGISTRY = Collections.unmodifiableMap(r);
    }

    private List<ModuleConfig> loadEnabledModuleConfigs(Connection conn) throws SQLException {
        if (tableExists(conn, "module_search_config")) {
            return loadConfigsFromConfigTable(conn);
        }

        Set<String> enabled = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT primaryname FROM module"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) enabled.add(rs.getString(1));
        }
        List<ModuleConfig> list = new ArrayList<>();
        for (String name : enabled) {
            ModuleConfig mc = findRegistryByNormalizedName(name);
            if (mc != null) list.add(mc);
        }
        return list;
    }

    private boolean tableExists(Connection conn, String tableName) {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }

    private ModuleConfig findRegistryByNormalizedName(String name) {
        if (name == null) return null;
        String norm = normalizeName(name);
        for (Map.Entry<String, ModuleConfig> e : REGISTRY.entrySet()) {
            if (normalizeName(e.getKey()).equals(norm)) return e.getValue();
        }
        for (Map.Entry<String, ModuleConfig> e : REGISTRY.entrySet()) {
            String k = normalizeName(e.getKey());
            if (k.endsWith("s") && k.substring(0, k.length()-1).equals(norm)) return e.getValue();
            if (norm.endsWith("s") && norm.substring(0, norm.length()-1).equals(k)) return e.getValue();
        }
        return null;
    }

    private String normalizeName(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replaceAll("\\s+", ""); // remove spaces
        return t;
    }

    private List<ModuleConfig> loadConfigsFromConfigTable(Connection conn) throws SQLException {
        List<ModuleConfig> list = new ArrayList<>();
        String sql = "SELECT module_name, index_name, sql_query, fields_search, fields_return FROM module_search_config";
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String index = rs.getString("index_name");
                String query = rs.getString("sql_query");
                String fieldsSearch = rs.getString("fields_search");
                String fieldsReturn = rs.getString("fields_return");

                List<String> searchFields = parseCsv(fieldsSearch);
                List<String> returnFields = parseCsv(fieldsReturn);

                SQLMapper mapper = buildGenericMapper(returnFields);
                list.add(new ModuleConfig(index, query, searchFields, returnFields, mapper));
            }
        }
        Set<String> enabled = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement("SELECT primaryname FROM module"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) enabled.add(normalizeName(rs.getString(1)));
        }
        List<ModuleConfig> filtered = new ArrayList<>();
        for (ModuleConfig mc : list) {
            if (enabled.contains(normalizeName(mc.index)) || enabled.contains(normalizeNameFromIndex(mc.index))) {
                filtered.add(mc);
            }
        }
        return filtered.isEmpty() ? list : filtered;
    }

    private List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) return Collections.emptyList();
        String[] parts = csv.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private SQLMapper buildGenericMapper(List<String> fields) {
        return rs -> {
            Map<String, Object> m = new LinkedHashMap<>();
            try { 
                Object id = rs.getObject("ID");
                if (id != null) {
                    m.put("ID", id);
                }
            } catch (SQLException ignore) {}
            for (String f : fields) {
                try { 
                    Object value = rs.getObject(f);
                    if (value != null) {
                        m.put(f, value);
                    }
                } catch (SQLException ignore) {}
            }
            return m;
        };
    }

    private String normalizeNameFromIndex(String index) {
        // map index like "system-interface" or "org_unit" to normalized name without separators
        return index == null ? "" : index.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final int BULK_BATCH_SIZE = 500;

    private void createIndicesIfNotExist(ElasticsearchClient client) throws IOException {
        String[] indices = {
                INDEX_ORG_UNIT, INDEX_PEOPLE, INDEX_STATUS,
                INDEX_SYSTEM, INDEX_DATASET, INDEX_INTERFACE, INDEX_GLOSSARY,
                INDEX_PROCESS, INDEX_PROJECT, INDEX_PRODUCT, INDEX_POLICY, INDEX_BUSINESS_AREA, INDEX_CAPABILITY,
                INDEX_legal, INDEX_CLIENT, INDEX_COMMITTEE, INDEX_ATTRIBUTE, INDEX_GEOGRAPHY,
                INDEX_REGULATION, INDEX_REGULATOR, INDEX_REGULATORY_THEME, INDEX_DATAQUALITY
        };

        for (String index : indices) {
            try {
                // Check if index exists
                boolean exists = client.indices().exists(e -> e.index(index)).value();
                if (!exists) {
                    // Create index with basic mapping
                    client.indices().create(c -> c.index(index));
                    //system.out.println("Created Elasticsearch index: " + index);
                }
            } catch (Exception e) {
                System.err.println("Error creating index " + index + ": " + e.getMessage());
                // Continue with other indices even if one fails
            }
        }
    }


    public synchronized void ensureSynced(boolean force) throws IOException {
        if (!force && synced) {
            //system.out.println("Elasticsearch already synced, skipping sync");
            return;
        }

        //system.out.println("Starting Elasticsearch sync (force=" + force + ")");
        
        ElasticsearchClient client = ElasticsearchConfig.getClient();
        if (client == null) {
            System.err.println("Elasticsearch client is null, cannot sync");
            return;
        }

        // Create indices if they don't exist
        createIndicesIfNotExist(client);

        List<Callable<Void>> tasks = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection()) {
            List<ModuleConfig> configs = loadEnabledModuleConfigs(conn);
            //system.out.println("Loaded " + configs.size() + " module configurations");
            
            for (ModuleConfig mc : configs) {
                tasks.add(() -> {
                    try {
                        //system.out.println("Syncing index: " + mc.index);
                        bulkSync(client, mc.index, mc.sql, mc.mapper);
                    } catch (SQLException | IOException e) {
                        System.err.println("Error in bulkSync for index " + mc.index + ": " + e.getMessage());
                        throw new RuntimeException("Error in bulkSync for index: " + mc.index, e);
                    }
                    return null;
                });
            }
        } catch (SQLException e) {
            // If database connection fails, just log the error and continue
            System.err.println("Database connection failed: " + e.getMessage());
            // No tasks to add - indices will be created empty
        }

        try {
            if (!tasks.isEmpty()) {
                List<Future<Void>> futures = executor.invokeAll(tasks);
                for (Future<Void> f : futures) {
                    try {
                        f.get();
                    } catch (ExecutionException e) {
                        System.err.println("Task execution failed: " + e.getCause().getMessage());
                        // Continue with other tasks
                    }
                }
            }

            // refresh all registered indices (safe even if some missing)
            try {
                client.indices().refresh(RefreshRequest.of(r -> r.index(new ArrayList<>(new LinkedHashSet<>(Arrays.asList(
                        INDEX_ORG_UNIT, INDEX_PEOPLE,
                        INDEX_SYSTEM, INDEX_DATASET, INDEX_INTERFACE, INDEX_GLOSSARY,
                        INDEX_PROCESS, INDEX_PROJECT, INDEX_PRODUCT, INDEX_POLICY, INDEX_BUSINESS_AREA, INDEX_CAPABILITY,
                        INDEX_legal, INDEX_CLIENT, INDEX_COMMITTEE, INDEX_ATTRIBUTE, INDEX_GEOGRAPHY,
                        INDEX_REGULATION, INDEX_REGULATOR, INDEX_REGULATORY_THEME, INDEX_DATAQUALITY
                ))))));
            } catch (Exception e) {
                System.err.println("Error refreshing indices: " + e.getMessage());
                // Continue anyway
            }

            synced = true;
            //system.out.println("Elasticsearch sync completed");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Sync interrupted: " + e.getMessage());
            // Mark as synced to prevent repeated attempts that could overwhelm the server
            synced = true;
        } catch (Exception e) {
            System.err.println("Error during sync process: " + e.getMessage());
            e.printStackTrace();
            // Don't throw, just log and mark as synced to prevent repeated attempts
            synced = true;
        }
    }

    private void bulkSync(ElasticsearchClient client, String index, String query, SQLMapper mapper) throws SQLException, IOException {
        if (client == null) {
            System.err.println("Elasticsearch client is null, skipping bulkSync for index: " + index);
            return;
        }

        if (query == null || query.trim().isEmpty()) {
            System.err.println("Query is null or empty, skipping bulkSync for index: " + index);
            return;
        }

        if (mapper == null) {
            System.err.println("Mapper is null, skipping bulkSync for index: " + index);
            return;
        }

        Set<String> mysqlIds = new HashSet<>();
        List<BulkOperation> batch = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            if (conn == null) {
                System.err.println("Database connection is null, skipping bulkSync for index: " + index);
                return;
            }

            try (PreparedStatement ps = conn.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {

                if (rs == null) {
                    System.err.println("ResultSet is null, skipping bulkSync for index: " + index);
                    return;
                }

                int rowCount = 0;
                while (rs.next()) {
                    try {
                        rowCount++;
                        Object idObj = rs.getObject("ID");
                        if (idObj == null) {
                            System.err.println("Warning: Row " + rowCount + " in index " + index + " has null ID, skipping");
                            continue;
                        }
                        
                        String id = String.valueOf(idObj);
                        Map<String, Object> doc = mapper.map(rs);

                        if (doc != null && !doc.isEmpty()) {
                            // Ensure ID is in the document
                            if (!doc.containsKey("ID")) {
                                doc.put("ID", idObj);
                            }
                            
                            mysqlIds.add(id);
                            batch.add(BulkOperation.of(b -> b.index(idx -> idx.index(index).id(id).document(doc))));

                            if (batch.size() >= BULK_BATCH_SIZE) {
                                BulkResponse bulkResponse = client.bulk(b -> b.operations(batch));
                                // Check for errors in bulk response
                                if (bulkResponse.errors()) {
                                    System.err.println("Warning: Some documents failed to index in batch for " + index);
                                }
                                batch.clear();
                            }
                        }
                    } catch (SQLException e) {
                        System.err.println("SQL error processing row " + rowCount + " for index " + index + ": " + e.getMessage());
                        // Continue with next row
                    } catch (Exception e) {
                        System.err.println("Error processing row " + rowCount + " for index " + index + ": " + e.getMessage());
                        if (e.getCause() != null) {
                            System.err.println("  Caused by: " + e.getCause().getMessage());
                        }
                        // Continue with next row
                    }
                }
                
                //system.out.println("Processed " + rowCount + " rows for index " + index);

                if (!batch.isEmpty()) {
                    BulkResponse bulkResponse = client.bulk(b -> b.operations(batch));
                    if (bulkResponse.errors()) {
                        System.err.println("Warning: Some documents failed to index in final batch for " + index);
                    }
                    //system.out.println("Indexed " + mysqlIds.size() + " documents for index " + index);
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error during bulkSync for index " + index + ": " + e.getMessage());
            // Don't rethrow, just log and continue
            return;
        }

        // Only delete missing if we have some data
        if (!mysqlIds.isEmpty()) {
            try {
                deleteMissing(client, index, mysqlIds);
            } catch (Exception e) {
                System.err.println("Error deleting missing documents for index " + index + ": " + e.getMessage());
            }
        }
    }

    @FunctionalInterface
    interface SQLMapper {
        Map<String, Object> map(ResultSet rs) throws SQLException;
    }

    @SuppressWarnings("rawtypes")
    private void deleteMissing(ElasticsearchClient client, String index, Set<String> mysqlIds) throws IOException {
        // Attempt to remove read-only-allow-delete block if present to avoid 429 errors
        tryUnblockIndex(client, index);

        int size = 500;
        Set<String> esIds = new HashSet<>();
        int from = 0;

        while (true) {
            final int fromFinal = from;

            SearchResponse<Map> response = client.search(
                    s -> s.index(index)
                            .from(fromFinal)
                            .size(size)
                            .source(sc -> sc.filter(f -> f.excludes("*"))),
                    Map.class
            );

            List<Hit<Map>> hits = response.hits().hits();
            if (hits.isEmpty()) break;

            for (Hit<Map> hit : hits) esIds.add(hit.id());
            from += size;
        }

        esIds.removeAll(mysqlIds);

        if (!esIds.isEmpty()) {
            //system.out.println("Deleting " + esIds.size() + " orphaned documents from index " + index);
            // Use bulk delete for better performance
            List<BulkOperation> deleteBatch = new ArrayList<>();
            for (String id : esIds) {
                deleteBatch.add(BulkOperation.of(b -> b.delete(d -> d.index(index).id(id))));
                if (deleteBatch.size() >= BULK_BATCH_SIZE) {
                    client.bulk(b -> b.operations(deleteBatch));
                    deleteBatch.clear();
                }
            }
            if (!deleteBatch.isEmpty()) {
                client.bulk(b -> b.operations(deleteBatch));
            }
            //system.out.println("Deleted " + esIds.size() + " orphaned documents from index " + index);
        }
    }

    /**
     * Best-effort: remove read-only-allow-delete block that Elasticsearch sets at flood-stage watermark.
     * This will only succeed if the disk condition has been alleviated and the client has permissions.
     */
    private void tryUnblockIndex(ElasticsearchClient client, String index) {
        try {
            client.indices().putSettings(ps -> ps
                    .index(index)
                    .settings(s -> s.withJson(new StringReader("{\"index.blocks.read_only_allow_delete\": false}"))));
        } catch (Exception e) {
            System.err.println("Could not update settings to unblock index '" + index + "': " + e.getMessage());
        }
    }


    public Map<String, Object> searchAll(String keyword, int size) throws IOException {
        ElasticsearchClient client = ElasticsearchConfig.getClient();
        Map<String, Object> result = new LinkedHashMap<>();

        try (Connection conn = DatabaseConnection.getConnection()) {
            for (ModuleConfig mc : loadEnabledModuleConfigs(conn)) {
                searchIndex(client, result, mc.index, keyword, size, mc.fieldsForSearch, mc.fieldsForReturn);
            }
        } catch (SQLException e) {
            // If database connection fails, use default module configurations
            System.err.println("Database connection failed, using default module configurations: " + e.getMessage());
            for (ModuleConfig mc : REGISTRY.values()) {
                try {
                    searchIndex(client, result, mc.index, keyword, size, mc.fieldsForSearch, mc.fieldsForReturn);
                } catch (IOException ioException) {
                    System.err.println("Failed to search index " + mc.index + ": " + ioException.getMessage());
                }
            }
        }

        return result;
    }

    /**
     * Get fuzzy search configuration from app_config table
     * @return true if fuzzy search is enabled, false otherwise
     */
    private boolean getFuzzySearchConfig() {
        String sql = "SELECT definition FROM app_config WHERE config_key = 'UNISON_FUZZY_DEFAULT'";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                String value = rs.getString("definition");
                return "true".equalsIgnoreCase(value);
            }
        } catch (SQLException e) {
            System.err.println("Error reading fuzzy search config, defaulting to true: " + e.getMessage());
        }
        // Default to true if config not found
        return true;
    }

    @SuppressWarnings({"null", "rawtypes"})
    private void searchIndex(ElasticsearchClient client, Map<String, Object> result, String index,
                             String keyword, int size, List<String> fieldsForSearch, List<String> fieldsForReturn)
            throws IOException {

        try {
            // Check if index exists before searching
            boolean exists = client.indices().exists(e -> e.index(index)).value();
            if (!exists) {
                //system.out.println("Index " + index + " does not exist, skipping search");
                return;
            }

            // Get fuzzy search configuration from database
            boolean useFuzzy = getFuzzySearchConfig();

            SearchResponse<Map> response = client.search(
                    s -> s.index(index)
                            .size(size)
                            .sort(sort -> sort.score(o -> o.order(SortOrder.Desc)))
                            .query(q -> q.bool(b -> {
                                for (String f : fieldsForSearch) {
                                    b.should(sh -> sh.matchPhrasePrefix(m -> m.field(f).query(keyword)));
                                    if (useFuzzy) {
                                        // Use AUTO fuzziness to allow 1-2 character errors based on query length
                                        // AUTO: 0 errors for 1-2 chars, 1 error for 3-5 chars, 2 errors for 6+ chars
                                        b.should(sh -> sh.match(m -> m.field(f).query(keyword).fuzziness("AUTO")));
                                    } else {
                                        b.should(sh -> sh.match(m -> m.field(f).query(keyword)));
                                    }
                                    // Add wildcard query for substring matching (e.g., "test" matches "glossary_test")
                                    // This enables finding search terms anywhere in the field value (beginning, middle, or end)
                                    b.should(sh -> sh.wildcard(w -> w.field(f).value("*" + keyword + "*").caseInsensitive(true)));
                                }
                                return b;
                            })), Map.class
            );

            List<Map<String, Object>> data = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.source();
                if (source == null) continue;

                Map<String, Object> item = new LinkedHashMap<>();
                // Always include ID (as id) if available for navigation
                Object idVal = source.get("ID");
                if (idVal != null) item.put("id", idVal);
                for (String field : fieldsForReturn) item.put(field, source.get(field));
                data.add(item);
            }

            int total = (int) (response.hits().total() != null ? response.hits().total().value() : data.size());
            if (total > 0) {
                result.put(index, Map.of("total", total, "data", data));
            }
        } catch (Exception e) {
            System.err.println("Error searching index " + index + ": " + e.getMessage());
            // Continue with other indices even if one fails
        }
    }
}
