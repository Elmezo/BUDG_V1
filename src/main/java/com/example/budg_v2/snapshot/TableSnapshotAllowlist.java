package com.example.budg_v2.snapshot;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses numbered table names from {@code migration/snapshot_table_allowlist.txt}.
 */
public final class TableSnapshotAllowlist {

    private static final Pattern LINE = Pattern.compile("^\\s*\\d+\\.\\s+([a-zA-Z0-9_]+)\\s*$");
    private static final String RESOURCE = "migration/snapshot_table_allowlist.txt";

    private TableSnapshotAllowlist() {
    }

    /**
     * Ordered unique names (lowercase) from the classpath resource.
     */
    public static List<String> load() {
        InputStream in = TableSnapshotAllowlist.class.getClassLoader().getResourceAsStream(RESOURCE);
        if (in == null) {
            throw new IllegalStateException("Missing classpath resource: " + RESOURCE);
        }
        Set<String> seen = new LinkedHashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = LINE.matcher(line);
                if (m.matches()) {
                    seen.add(m.group(1).toLowerCase());
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read " + RESOURCE, e);
        }
        return new ArrayList<>(seen);
    }
}
