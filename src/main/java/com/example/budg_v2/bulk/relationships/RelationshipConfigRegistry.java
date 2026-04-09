package com.example.budg_v2.bulk.relationships;

import com.example.budg_v2.bulk.relationships.config.EntityConfig;
import com.example.budg_v2.bulk.relationships.config.RelationshipConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for relationship configurations
 * Config-driven approach for easy addition of new relationship types
 */
public class RelationshipConfigRegistry {
    
    private static final Logger logger = LoggerFactory.getLogger(RelationshipConfigRegistry.class);
    private static final Map<String, RelationshipConfig> REGISTRY = new HashMap<>();
    
    static {
        initializeConfigurations();
    }
    
    /**
     * Get relationship configuration by key
     * Normalizes the key by converting to lowercase, removing spaces, and removing underscores
     * so that "Legal_X_Geography" and "legal_x_geography" resolve to the same config as "legalentityxgeography".
     */
    public static RelationshipConfig getConfig(String key) {
        if (key == null) {
            return null;
        }
        String normalizedKey = normalizeKey(key);
        return REGISTRY.get(normalizedKey);
    }

    /**
     * Normalize key: lowercase, trim, remove spaces, remove underscores.
     */
    private static String normalizeKey(String key) {
        if (key == null) {
            return "";
        }
        return key.toLowerCase().trim().replaceAll("\\s+", "").replace("_", "");
    }

    /**
     * Check if relationship key is supported
     * Uses same normalization as getConfig (lowercase, no spaces, no underscores).
     */
    public static boolean isSupported(String key) {
        if (key == null) {
            return false;
        }
        String normalizedKey = normalizeKey(key);
        return REGISTRY.containsKey(normalizedKey);
    }
    
    /**
     * Get all supported relationship keys
     */
    public static Set<String> getSupportedKeys() {
        return REGISTRY.keySet();
    }

    /**
     * All registered relationship configurations (for environment replace / truncate).
     */
    public static List<RelationshipConfig> getAllConfigurations() {
        return Collections.unmodifiableList(new ArrayList<>(REGISTRY.values()));
    }
    
    /**
     * Initialize all relationship configurations
     * Based on BulkRelationshipsTemplateGeneratorServlet RELATIONSHIP_CONFIG
     */
    private static void initializeConfigurations() {
        logger.info("Initializing relationship configurations...");
        
        // Capability X Client
        REGISTRY.put("capabilityxclient", new RelationshipConfig(
            "capabilityxclient",
            "capability_x_client",
            "Capability X Client",
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", true),
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            "Capability_ID",
            "Client_ID",
            "capability_x_client_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Capability X Product
        REGISTRY.put("capabilityxproduct", new RelationshipConfig(
            "capabilityxproduct",
            "capability_x_product",
            "Capability X Product",
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", true),
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            "Capability_ID",
            "Product_ID",
            "capability_x_product_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Capability X System
        REGISTRY.put("capabilityxsystem", new RelationshipConfig(
            "capabilityxsystem",
            "capability_x_system",
            "Capability X System",
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "Capability_ID",
            "System_ID",
            "capability_x_system_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X System
        REGISTRY.put("policyxsystem", new RelationshipConfig(
            "policyxsystem",
            "policy_x_system",
            "Policy X System",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "Policy_ID",
            "System_ID",
            "policy_x_system_relationtype",
            "Relation_Type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Business Area X System
        REGISTRY.put("businessareaxsystem", new RelationshipConfig(
            "businessareaxsystem",
            "businessarea_x_system",
            "Business Area X System",
            new EntityConfig("businessarea", "business_area", "ID", null, "PrimaryName", "Parent_ID", "deletedatetime", true),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "BusinessArea_ID",
            "System_ID",
            "businessarea_x_system_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X System
        REGISTRY.put("processxsystem", new RelationshipConfig(
            "processxsystem",
            "process_x_system",
            "Process X System",
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "Process_ID",
            "System_ID",
            "process_x_system_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Product X Client
        REGISTRY.put("productxclient", new RelationshipConfig(
            "productxclient",
            "product_x_client",
            "Product X Client",
            new EntityConfig("product", "product", "id", "refnumber", "primaryname", "parent_id", "deleteddatetime", false),
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            "Product_ID",
            "Client_ID",
            "product_x_client_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X Client
        REGISTRY.put("policyxclient", new RelationshipConfig(
            "policyxclient",
            "client_x_policy",
            "Policy X Client",
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", false),
            "Client_ID",
            "Policy_ID",
            "client_x_policy_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // System X Client
        REGISTRY.put("systemxclient", new RelationshipConfig(
            "systemxclient",
            "client_x_system",
            "System X Client",
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "Client_ID",
            "System_ID",
            "client_x_system_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Attribute X Attribute (source/target roles so "Source Attribute Name/Ref." and "Target Attribute Name/Ref." match correctly)
        REGISTRY.put("attributexattribute", new RelationshipConfig(
            "attributexattribute",
            "attribute_x_attribute",
            "Attribute X Attribute",
            new EntityConfig("attribute", "attribute", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            new EntityConfig("attribute", "attribute", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            "Source_AttributeID",
            "Target_AttributeID",
            "attribute_x_attribute_relationtype",
            "Relation_Type",
            true,
            null,
            new String[]{"Relation_Scope", "Relation_Method"},
            true, true, false,
            "source", "target"
        ));
        
        // Business Area X Glossary
        REGISTRY.put("businessareaxglossary", new RelationshipConfig(
            "businessareaxglossary",
            "businessarea_x_glossary",
            "Business Area X Glossary",
            new EntityConfig("businessarea", "business_area", "ID", null, "PrimaryName", "Parent_ID", "deletedatetime", true),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", false),
            "BusinessArea_ID",
            "Glossary_ID",
            "businessarea_x_glossary_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Business Area X Process
        REGISTRY.put("businessareaxprocess", new RelationshipConfig(
            "businessareaxprocess",
            "businessarea_x_process",
            "Business Area X Process",
            new EntityConfig("businessarea", "business_area", "ID", null, "PrimaryName", "Parent_ID", "deletedatetime", true),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            "BusinessArea_ID",
            "Process_ID",
            "businessarea_x_process_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Capability X Business Area
        REGISTRY.put("capabilityxbusinessarea", new RelationshipConfig(
            "capabilityxbusinessarea",
            "capability_x_businessarea",
            "Capability X Business Area",
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            new EntityConfig("businessarea", "business_area", "ID", null, "PrimaryName", "Parent_ID", "deletedatetime", true),
            "Capability_ID",
            "BusinessArea_ID",
            "capability_x_businessarea_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Capability X Glossary
        REGISTRY.put("capabilityxglossary", new RelationshipConfig(
            "capabilityxglossary",
            "capability_x_glossary",
            "Capability X Glossary",
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", true),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            "Capability_ID",
            "Glossary_ID",
            "capability_x_glossary_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Capability X Legal Entity
        REGISTRY.put("capabilityxlegalentity", new RelationshipConfig(
            "capabilityxlegalentity",
            "capability_x_legal",
            "Capability X Legal Entity",
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            new EntityConfig("legal", "legal", "ID", null, "ShortName", "Parent_ID", "DeleteDatetime", true),
            "Capability_ID",
            "Legal_ID",
            "capability_x_legal_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Capability X Process
        REGISTRY.put("capabilityxprocess", new RelationshipConfig(
            "capabilityxprocess",
            "capability_x_process",
            "Capability X Process",
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            "Capability_ID",
            "Process_ID",
            "capability_x_process_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Committee X Capability
        REGISTRY.put("committeexcapability", new RelationshipConfig(
            "committeexcapability",
            "committee_x_capability",
            "Committee X Capability",
            new EntityConfig("committee", "committee", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeleteDatetime", true),
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", true),
            "Committee_ID",
            "Capability_ID",
            "committee_x_capability_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Committee X Committee
        REGISTRY.put("committeexcommittee", new RelationshipConfig(
            "committeexcommittee",
            "committee_x_committee",
            "Committee X Committee",
            new EntityConfig("committee", "committee", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeleteDatetime", false),
            new EntityConfig("committee", "committee", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeleteDatetime", false),
            "Source_ID",
            "Target_ID",
            "committee_x_committee_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false,
            "source", "target"
        ));
        
        // Data Set X Client
        REGISTRY.put("datasetxclient", new RelationshipConfig(
            "datasetxclient",
            "client_x_dataset",
            "Data Set X Client",
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            new EntityConfig("dataset", "dataset", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", true),
            "Client_ID",
            "Dataset_ID",  // Matches DB column name in client_x_dataset (see DatasetImpactDAO)
            "client_x_dataset_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Data Set X Legal Entity
        REGISTRY.put("datasetxlegalentity", new RelationshipConfig(
            "datasetxlegalentity",
            "dataset_x_legal",
            "Data Set X Legal Entity",
            new EntityConfig("dataset", "dataset", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            new EntityConfig("legal", "legal", "ID", null, "ShortName", "Parent_ID", "DeleteDatetime", true),
            "DataSet_ID",
            "Legal_ID",
            "dataset_x_legal_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Data Set X Product
        REGISTRY.put("datasetxproduct", new RelationshipConfig(
            "datasetxproduct",
            "product_x_dataset",
            "Data Set X Product",
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            new EntityConfig("dataset", "dataset", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", true),
            "Product_ID",
            "Dataset_ID",  // Fixed: Changed from "DataSet_ID" to "Dataset_ID" to match database column name
            "product_x_dataset_relationtype",
            "Product_Dataset_Relation_Type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Glossary X Client
        REGISTRY.put("glossaryxclient", new RelationshipConfig(
            "glossaryxclient",
            "client_x_glossary",
            "Glossary X Client",
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            "Client_ID",
            "Glossary_ID",
            "client_x_glossary_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Glossary X Glossary (both source and target required so resolution and self-relationship validation work correctly)
        REGISTRY.put("glossaryxglossary", new RelationshipConfig(
            "glossaryxglossary",
            "glossary_x_glossary",
            "Glossary X Glossary",
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            "SourceGlossaryID",
            "TargetGlossaryID",
            "glossary_x_glossary_reltype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false,
            "source", "target"
        ));
        
        // Glossary X Product (product_x_glossary table uses lowercase column "relationtype")
        REGISTRY.put("glossaryxproduct", new RelationshipConfig(
            "glossaryxproduct",
            "product_x_glossary",
            "Glossary X Product",
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            "productid",
            "glossaryid",
            "product_x_glossary_relationtype",
            "relationtype",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Glossary X System
        REGISTRY.put("glossaryxsystem", new RelationshipConfig(
            "glossaryxsystem",
            "glossary_x_system",
            "Glossary X System",
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "GlossaryID",
            "SystemID",
            "glossary_x_system_relationtype",
            "Relation_TypeID",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Interface X Glossary
        REGISTRY.put("interfacexglossary", new RelationshipConfig(
            "interfacexglossary",
            "interface_x_glossary",
            "Interface X Glossary",
            new EntityConfig("interface", "interface", "id", "Ref_number", "Name", null, "deleted_datetime", false),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", false),
            "Interface",
            "Glossary",
            "interface_x_glossary_relationtype",
            "Glossary_RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Legal Entity X Geography
        REGISTRY.put("legalentityxgeography", new RelationshipConfig(
            "legalentityxgeography",
            "legal_x_geography",
            "Legal Entity X Geography",
            new EntityConfig("legal", "legal", "ID", null, "ShortName", "Parent_ID", "DeleteDatetime", true),
            new EntityConfig("geography", "geography", "ID", null, "PrimaryName", "ParentID", "DeletedDatetime", true),
            "Legal_ID",
            "Geography_ID",
            "legal_x_geo_relationtype",
            "Relation_Type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        // Alias so "Legal_X_Geography" and "legal_x_geography" resolve to Legal Entity X Geography
        REGISTRY.put("legalxgeography", REGISTRY.get("legalentityxgeography"));

        // Policy X Attribute
        REGISTRY.put("policyxattribute", new RelationshipConfig(
            "policyxattribute",
            "policy_x_attribute",
            "Policy X Attribute",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            new EntityConfig("attribute", "attribute", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", true),
            "policyid",
            "attributeid",
            "policy_x_attribute_relationtype",
            "relation_type",
            true,
            "description",
            new String[]{},
            true, true, false
        ));
        
        // Policy X Business Area
        REGISTRY.put("policyxbusinessarea", new RelationshipConfig(
            "policyxbusinessarea",
            "policy_x_businessarea",
            "Policy X Business Area",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            new EntityConfig("businessarea", "business_area", "ID", null, "PrimaryName", "Parent_ID", "deletedatetime", true),
            "Policy_ID",
            "BusinessArea_ID",
            "policy_x_businessarea_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X Data Set
        REGISTRY.put("policyxdataset", new RelationshipConfig(
            "policyxdataset",
            "policy_x_dataset",
            "Policy X Data Set",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", false),
            new EntityConfig("dataset", "dataset", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            "PolicyID",
            "DatasetID",
            "policy_x_dataset_relationtype",
            "Relation_Type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X Glossary
        REGISTRY.put("policyxglossary", new RelationshipConfig(
            "policyxglossary",
            "policy_x_glossary",
            "Policy X Glossary",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            "PolicyID",
            "GlossaryID",
            "policy_x_glossary_relationtype",
            "Relation_Type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X Legal Entity
        REGISTRY.put("policyxlegalentity", new RelationshipConfig(
            "policyxlegalentity",
            "policy_x_legal",
            "Policy X Legal Entity",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", false),
            new EntityConfig("legal", "legal", "ID", null, "ShortName", "Parent_ID", "DeleteDatetime", true),
            "Policy_ID",
            "Legal_ID",
            "policy_x_legal_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X Policy
        REGISTRY.put("policyxpolicy", new RelationshipConfig(
            "policyxpolicy",
            "policy_x_policy",
            "Policy X Policy",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", false),
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", false),
            "sourceid",
            "targetid",
            "policy_x_policy_relation_type",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false,
            "source", "target"
        ));
        
        // Policy X Process
        REGISTRY.put("policyxprocess", new RelationshipConfig(
            "policyxprocess",
            "policy_x_process",
            "Policy X Process",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", true),
            "policy_id",
            "process_id",
            "policy_x_process_relationtype",
            "relation_type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X Product
        REGISTRY.put("policyxproduct", new RelationshipConfig(
            "policyxproduct",
            "product_x_policy",
            "Policy X Product",
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            "productid",
            "policyid",
            "product_x_policy_relationtype",
            "relationtype",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Policy X Project
        REGISTRY.put("policyxproject", new RelationshipConfig(
            "policyxproject",
            "policy_x_project",
            "Policy X Project",
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", true),
            "policy_id",
            "project_id",
            "policy_x_project_relationtype",
            "relation_type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X Attribute
        REGISTRY.put("processxattribute", new RelationshipConfig(
            "processxattribute",
            "process_x_attribute",
            "Process X Attribute",
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            new EntityConfig("attribute", "attribute", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            "processid",
            "attributeid",
            "process_x_attribute_relationtype",
            "relation_type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X Client
        REGISTRY.put("processxclient", new RelationshipConfig(
            "processxclient",
            "client_x_process",
            "Process X Client",
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            "Client_ID",
            "Process_ID",
            "client_x_process_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X Data Set
        REGISTRY.put("processxdataset", new RelationshipConfig(
            "processxdataset",
            "process_x_dataset",
            "Process X Data Set",
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            new EntityConfig("dataset", "dataset", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            "processid",
            "datasetid",
            "process_x_dataset_relationtype",
            "relation_type",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X Glossary
        REGISTRY.put("processxglossary", new RelationshipConfig(
            "processxglossary",
            "glossary_x_process",
            "Process X Glossary",
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", false),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            "Glossary_ID",
            "Process_ID",
            "glossary_x_process_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X Legal Entity
        REGISTRY.put("processxlegalentity", new RelationshipConfig(
            "processxlegalentity",
            "process_x_legal",
            "Process X Legal Entity",
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", true),
            new EntityConfig("legal", "legal", "ID", null, "ShortName", "Parent_ID", "DeleteDatetime", true),
            "Process_ID",
            "Legal_ID",
            "process_x_legal_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X Process (entityARole/entityBRole so "Process Ref." maps to entity A, "Predecessor Process Ref." to entity B)
        REGISTRY.put("processxprocess", new RelationshipConfig(
            "processxprocess",
            "process_x_process",
            "Process X Process",
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            "sourceprocess_id",
            "targetprocess_id",
            "process_x_process_relationtype",
            "relationtype",
            false,
            "annotations",
            new String[]{},
            true, true, false,
            "Process", "Predecessor"
        ));
        
        // Process X Product
        REGISTRY.put("processxproduct", new RelationshipConfig(
            "processxproduct",
            "product_x_process",
            "Process X Product",
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", true),
            "productid",
            "processid",
            "product_x_process_relationtype",
            "relationtype",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Process X System Interface
        REGISTRY.put("processxsysteminterface", new RelationshipConfig(
            "processxsysteminterface",
            "process_x_interface",
            "Process X System Interface",
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", false),
            new EntityConfig("interface", "interface", "id", "Ref_number", "Name", null, "deleted_datetime", false),
            "Process_ID",
            "Interface_ID",
            "process_x_interface_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Product X Business Area
        REGISTRY.put("productxbusinessarea", new RelationshipConfig(
            "productxbusinessarea",
            "product_x_businessarea",
            "Product X Business Area",
            new EntityConfig("product", "product", "id", "refnumber", "primaryname", "parent_id", "deleteddatetime", true),
            new EntityConfig("businessarea", "business_area", "ID", null, "PrimaryName", "Parent_ID", "deletedatetime", true),
            "Product_ID",
            "BusinessArea_ID",
            "product_x_businessarea_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Product X Legal Entity
        REGISTRY.put("productxlegalentity", new RelationshipConfig(
            "productxlegalentity",
            "product_x_legal",
            "Product X Legal Entity",
            new EntityConfig("product", "product", "id", "refnumber", "primaryname", "parent_id", "deleteddatetime", false),
            new EntityConfig("legal", "legal", "ID", null, "ShortName", "Parent_ID", "DeleteDatetime", true),
            "Product_ID",
            "Legal_ID",
            "product_x_legalentity_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Product X System
        REGISTRY.put("productxsystem", new RelationshipConfig(
            "productxsystem",
            "product_x_system",
            "Product X System",
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "Product_ID",
            "System_ID",
            "product_x_system_relationtype",
            "Product_System_Relation_Type",
            true,
            null,
            new String[]{"Legal_Relation_Type", "Legal_ID"},
            true, true, false
        ));
        
        // System X Product (reverse perspective of Product X System)
        REGISTRY.put("systemxproduct", new RelationshipConfig(
            "systemxproduct",
            "product_x_system",
            "System X Product",
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            "System_ID",
            "Product_ID",
            "product_x_system_relationtype",
            "Product_System_Relation_Type",
            true,
            null,
            new String[]{"Legal_Relation_Type", "Legal_ID"},
            true, true, false
        ));
        
        // Project X Attribute
        REGISTRY.put("projectxattribute", new RelationshipConfig(
            "projectxattribute",
            "project_x_attribute",
            "Project X Attribute",
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", false),
            new EntityConfig("attribute", "attribute", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            "projectid",
            "attribute_id",
            "project_x_attribute_relationtype",
            "relationtype",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Project X Business Area
        REGISTRY.put("projectxbusinessarea", new RelationshipConfig(
            "projectxbusinessarea",
            "project_x_businessarea",
            "Project X Business Area",
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", true),
            new EntityConfig("businessarea", "business_area", "ID", null, "PrimaryName", "Parent_ID", "deletedatetime", true),
            "Project_ID",
            "BusinessArea_ID",
            "project_x_businessarea_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Project X Capability
        REGISTRY.put("projectxcapability", new RelationshipConfig(
            "projectxcapability",
            "project_x_capability",
            "Project X Capability",
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", true),
            new EntityConfig("capability", "capability", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", true),
            "Project_ID",
            "Capability_ID",
            "project_x_capability_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Project X Client
        REGISTRY.put("projectxclient", new RelationshipConfig(
            "projectxclient",
            "client_x_project",
            "Project X Client",
            new EntityConfig("client", "client", "ID", null, "PrimaryName", "Parent_ID", null, true),
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", false),
            "Client_ID",
            "Project_ID",
            "client_x_project_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Project X Data Set
        REGISTRY.put("projectxdataset", new RelationshipConfig(
            "projectxdataset",
            "project_x_dataset",
            "Project X Data Set",
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", false),
            new EntityConfig("dataset", "dataset", "ID", "RefNumber", "PrimaryName", null, "DeletedDatetime", false),
            "projectid",
            "dataset_id",
            "project_x_dataset_relationtype",
            "relationtype",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Project X Glossary
        REGISTRY.put("projectxglossary", new RelationshipConfig(
            "projectxglossary",
            "glossary_x_project",
            "Project X Glossary",
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", true),
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", true),
            "Glossary_ID",
            "Project_ID",
            "glossary_x_project_relationtype",
            "RelationType",
            true,
            "Description",
            new String[]{},
            true, true, false
        ));
        
        // Project X Process
        REGISTRY.put("projectxprocess", new RelationshipConfig(
            "projectxprocess",
            "project_x_process",
            "Project X Process",
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", true),
            new EntityConfig("process", "process", "id", "refnumber", "primaryname", "parentid", "deleteddatetime", true),
            "projectid",
            "process_id",
            "project_x_process_relationtype",
            "relationtype",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Project X Product - both product and project must resolve; project is required
        REGISTRY.put("projectxproduct", new RelationshipConfig(
            "projectxproduct",
            "product_x_project",
            "Project X Product",
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", true),
            "productid",
            "projectid",
            "product_x_project_relationtype",
            "relationtype",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Project X Project
        REGISTRY.put("projectxproject", new RelationshipConfig(
            "projectxproject",
            "project_x_project",
            "Project X Project",
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", false),
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", false),
            "sourceprojectid",
            "targetprojectid",
            "project_x_project_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false,
            "source", "target"
        ));
        
        // Project X System
        REGISTRY.put("projectxsystem", new RelationshipConfig(
            "projectxsystem",
            "project_x_system",
            "Project X System",
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", false),
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            "projectid",
            "systemid",
            "project_x_system_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Regulation X Policy
        REGISTRY.put("regulationxpolicy", new RelationshipConfig(
            "regulationxpolicy",
            "regulation_x_policy",
            "Regulation X Policy",
            new EntityConfig("regulation", "regulation", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", true),
            new EntityConfig("policy", "policy", "ID", "RefNumber", "PrimaryName", "ParentID", "DeletedDatetime", true),
            "RegulationID",
            "PolicyID",
            "regulation_x_policy_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Regulation X Product
        REGISTRY.put("regulationxproduct", new RelationshipConfig(
            "regulationxproduct",
            "regulation_x_product",
            "Regulation X Product",
            new EntityConfig("regulation", "regulation", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", true),
            new EntityConfig("product", "product", "id", null, "primaryname", "parent_id", "deleteddatetime", true),
            "RegulationID",
            "ProductID",
            "regulation_x_product_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Regulation X Project
        REGISTRY.put("regulationxproject", new RelationshipConfig(
            "regulationxproject",
            "regulation_x_project",
            "Regulation X Project",
            new EntityConfig("regulation", "regulation", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            new EntityConfig("project", "project", "id", "refnumber", "primaryname", "parentid", "deletedatetime", false),
            "RegulationID",
            "ProjectID",
            "regulation_x_project_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Regulation X Regulator
        REGISTRY.put("regulationxregulator", new RelationshipConfig(
            "regulationxregulator",
            "regulation_x_regulator",
            "Regulation X Regulator",
            new EntityConfig("regulation", "regulation", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            new EntityConfig("regulator", "regulator", "ID", null, "PrimaryName", null, "DeletedDatetime", true),
            "RegulationID",
            "RegulatorID",
            "regulation_x_regulator_relationtype",
            "RelationType",
            true,
            "Regulator_Reg_Ref",
            new String[]{},
            true, true, false
        ));
        
        // Regulation X Regulatory Theme
        REGISTRY.put("regulationxregulatorytheme", new RelationshipConfig(
            "regulationxregulatorytheme",
            "regulation_x_regulatorytheme",
            "Regulation X Regulatory Theme",
            new EntityConfig("regulation", "regulation", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            new EntityConfig("regulatorytheme", "regulatorytheme", "ID", "RefNumber", "PrimaryName", "Parent_ID", "DeletedDatetime", false),
            "Regulation_ID",
            "RegulatoryTheme_ID",
            "regulation_x_regulatorytheme_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // Regulator X Geography
        REGISTRY.put("regulatorxgeography", new RelationshipConfig(
            "regulatorxgeography",
            "regulator_x_geography",
            "Regulator X Geography",
            new EntityConfig("regulator", "regulator", "ID", null, "PrimaryName", null, "DeletedDatetime", true),
            new EntityConfig("geography", "geography", "ID", null, "PrimaryName", "ParentID", "DeletedDatetime", true),
            "Regulator_ID",
            "Geography_ID",
            null,
            null,
            false,
            "Description",
            new String[]{},
            true, true, false
        ));
        
        // System X Legal Entity
        REGISTRY.put("systemxlegalentity", new RelationshipConfig(
            "systemxlegalentity",
            "system_x_legal",
            "System X Legal Entity",
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            new EntityConfig("legal", "legal", "ID", null, "ShortName", "Parent_ID", "DeleteDatetime", true),
            "System_ID",
            "Legal_ID",
            "system_x_legal_relationtype",
            "RelationType",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // System X Glossary
        REGISTRY.put("systemxglossary", new RelationshipConfig(
            "systemxglossary",
            "glossary_x_system",
            "System X Glossary",
            new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
            new EntityConfig("glossary", "glossary", "ID", "Ref_Number", "Name", "Parent_ID", "Deleted_datetime", false),
            "SystemID",
            "GlossaryID",
            "glossary_x_system_relationtype",
            "Relation_TypeID",
            true,
            null,
            new String[]{},
            true, true, false
        ));
        
        // People X People (entityARole/entityBRole so "Manager Email" maps to entity A, "Employee Email" to entity B)
        REGISTRY.put("peoplexpeople", new RelationshipConfig(
            "peoplexpeople",
            "people_x_people",
            "People X People",
            new EntityConfig("people", "people", "ID", null, "Email", null, "Deleted_date", true),
            new EntityConfig("people", "people", "ID", null, "Email", null, "Deleted_date", true),
            "Manager",
            "Employee",
            "people_x_people_relationtype",
            "ipXip_RelationType",
            true,
            null,
            new String[]{},
            true, true, false,
            "Manager", "Employee"
        ));
        
        // System X Resource - DISABLED: Table 'system_x_resource' does not exist in database
        // REGISTRY.put("systemxresource", new RelationshipConfig(
        //     "systemxresource",
        //     "system_x_resource",
        //     "System X Resource",
        //     new EntityConfig("system", "system", "id", null, "Name", "parent_id", "Deleted_datetime", true),
        //     new EntityConfig("objectreference", "object_reference", "ID", null, "Resource_ID", null, null, true),
        //     "System_ID",
        //     "Object_Reference_ID",
        //     null,
        //     null,
        //     false,
        //     null,
        //     new String[]{"Resource_ID"},
        //     true, true, false
        // ));
        
        // Segment X Object (for SegmentXObject_BulkUpload_Template)
        REGISTRY.put("segmentxobject", new RelationshipConfig(
            "segmentxobject",
            "segment_x_identity",
            "Segment X Object",
            new EntityConfig("segment", "segment", "ID", null, "PrimaryName", null, "Deleted_At", true),
            new EntityConfig("identity", "identity", "ID", null, "Object_ID", null, null, true),
            "Segment_ID",
            "Identity_ID",
            null,
            null,
            false,
            "Object_Type",
            new String[]{"Object_ID"},
            true, true, true
        ));
        
        logger.info("Loaded {} relationship configurations", REGISTRY.size());
    }
    
    /**
     * Add or update a configuration (for dynamic configuration)
     */
    public static void registerConfig(RelationshipConfig config) {
        if (config != null && config.getKey() != null) {
            REGISTRY.put(config.getKey().toLowerCase(), config);
            logger.info("Registered configuration for: {}", config.getKey());
        }
    }
}

