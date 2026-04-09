package com.example.budg_v2.tools;

import com.example.budg_v2.bulk.relationships.RelationshipConfigRegistry;
import com.example.budg_v2.bulk.relationships.config.EntityConfig;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import com.example.budg_v2.util.RelationshipExcelGenerator;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

/**
 * One-shot: writes {@code python/processors/relationships/relationship_metadata.json} for Python import.
 */
public final class RelationshipMetadataExporter {

    private RelationshipMetadataExporter() {
    }

    public static void main(String[] args) throws Exception {
        RelationshipExcelGenerator gen = new RelationshipExcelGenerator();
        JsonArray out = new JsonArray();
        for (String key : new TreeSet<>(RelationshipConfigRegistry.getSupportedKeys())) {
            RelationshipConfig c = RelationshipConfigRegistry.getConfig(key);
            if (c == null) {
                continue;
            }
            try {
                JsonObject o = new JsonObject();
                o.addProperty("key", key);
                o.addProperty("relationship_table", c.getTableName());
                o.add("entity_a", entityJson(c.getEntityA(), c.getEntityAIdColumn()));
                o.add("entity_b", entityJson(c.getEntityB(), c.getEntityBIdColumn()));
                if (c.getRelationTypeTable() != null && !c.getRelationTypeTable().isEmpty()) {
                    o.addProperty("relation_type_table", c.getRelationTypeTable());
                }
                if (c.getRelationTypeColumn() != null && !c.getRelationTypeColumn().isEmpty()) {
                    o.addProperty("relation_type_field", c.getRelationTypeColumn());
                }
                o.addProperty("requires_relation_type", c.isRequiresRelationType());
                JsonArray h = new JsonArray();
                for (String x : gen.getHeadersForRelationshipPublic(c)) {
                    h.add(x);
                }
                o.add("headers", h);
                out.add(o);
            } catch (Exception e) {
                System.err.println("Skip " + key + ": " + e.getMessage());
            }
        }
        Path p = Path.of("python/processors/relationships/relationship_metadata.json");
        Files.createDirectories(p.getParent());
        Files.writeString(p, new GsonBuilder().setPrettyPrinting().create().toJson(out));
        System.out.println("Wrote " + out.size() + " entries to " + p.toAbsolutePath());
    }

    private static JsonObject entityJson(EntityConfig e, String dbCol) {
        JsonObject o = new JsonObject();
        o.addProperty("name", e.getName());
        o.addProperty("table", e.getTableName());
        o.addProperty("name_field", e.getNameColumn());
        if (e.getRefColumn() != null && !e.getRefColumn().isEmpty()) {
            o.addProperty("ref_field", e.getRefColumn());
        }
        if (e.getParentColumn() != null && !e.getParentColumn().isEmpty()) {
            o.addProperty("parent_field", e.getParentColumn());
        }
        o.addProperty("db_column", dbCol);
        return o;
    }
}
