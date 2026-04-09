package com.example.budg_v2.bulk.relationships;

import com.example.budg_v2.util.CorsUtil;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Servlet for generating dynamic Excel templates for Relationship bulk upload
 * Endpoint: /api/bulk/templates/generate-relationship/{relationshipKey}/{operation}
 * operation: INSERT, DELETE, or UPDATE (for specific relationships like Segment X Object)
 * relationshipKey: normalized key like "capabilityxproduct", "policyxsystem", etc.
 */
@WebServlet(urlPatterns = {"/api/bulk/templates/generate-relationship/*"})
public class BulkRelationshipsTemplateGeneratorServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(BulkRelationshipsTemplateGeneratorServlet.class);
    private static final Gson gson = new Gson();
    
    // Relationship entity configuration
    private static final Map<String, RelationshipTemplateConfig> RELATIONSHIP_CONFIG = new HashMap<>();
    
    // Notes loaded from notes.json: Map<fileName, Map<headerName, noteText>>
    private static final Map<String, Map<String, String>> NOTES_BY_FILE_AND_HEADER = loadNotesFromJson();
    
    static {
        // ===== ATTRIBUTE RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("attributexattribute", new RelationshipTemplateConfig(
            "Attribute X Attribute",
            "attribute-x-attribute.xlsx",
            new String[]{
                "Sourcing Logic",
                "Sourcing Type",
                "Target Attribute Ref.",
                "Target Attribute Name",
                "Target Data Set Name",
                "Target System Short Name",
                "Source Attribute Ref.",
                "Source Attribute Name",
                "Source Data Set Name",
                "Source System Short Name",
                "Scope of Data",
                "Interface Name",
                "Interface Source System Short Name",
                "Interface Target System Short Name"
            },
            new boolean[]{false, true, false, false, false, false, false, false, false, false, false, false, false, false},
            new boolean[]{false, true, false, false, false, false, false, false, false, false, true, false, false, false},
            new String[]{
                "Sourcing Logic description",
                "Sourcing Type (required, dropdown list from attribute_x_attribute_relationtype)",
                "Target Attribute reference number (optional)",
                "Target Attribute name (optional, used to identify the target attribute)",
                "Target Data Set name (optional, for disambiguation)",
                "Target System short name (optional, for disambiguation)",
                "Source Attribute reference number (optional)",
                "Source Attribute name (optional, used to identify the source attribute)",
                "Source Data Set name (optional, for disambiguation)",
                "Source System short name (optional, for disambiguation)",
                "Scope of Data (optional dropdown from attribute_x_attribute_relationscope)",
                "Interface name (optional)",
                "Interface source system short name (optional)",
                "Interface target system short name (optional)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(1, "attribute_x_attribute_relationtype", "PrimaryName"),
                new ListColumnConfig(10, "attribute_x_attribute_relationscope", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("attributexphysicalfield", new RelationshipTemplateConfig(
            "Attribute X Physical Field",
            "attribute-x-physical-field.xlsx",
            new String[]{
                "Attribute Ref.",
                "Attribute Name",
                "Attribute Data Set Name",
                "Attribute System Short Name",
                "Name",
                "Field ID",
                "Field Type"
            },
            new boolean[]{false, false, false, false, true, true, true},
            new boolean[]{false, false, false, false, false, false, false},
            new String[]{
                "Attribute reference number (optional)",
                "Attribute name (optional)",
                "Attribute Data Set name (optional, for disambiguation)",
                "Attribute System short name (optional, for disambiguation)",
                "Physical field name (required)",
                "Physical field ID (required)",
                "Physical field type (required)"
            },
            new ListColumnConfig[]{},
            new String[]{"INSERT"}
        ));
        
        // ===== BUSINESS AREA RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("businessareaxglossary", new RelationshipTemplateConfig(
            "Business Area X Glossary",
            "business-area-x-glossary.xlsx",
            new String[]{
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Business Area Name",
                "Parent Business Area Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Business Area name (required)",
                "Parent Business Area name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from businessarea_x_glossary_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "businessarea_x_glossary_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("businessareaxprocess", new RelationshipTemplateConfig(
            "Business Area X Process",
            "business-area-x-process.xlsx",
            new String[]{
                "Description",
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Business Area Name",
                "Business Area Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Business Area name (required)",
                "Business Area parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from businessarea_x_process_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "businessarea_x_process_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("businessareaxsystem", new RelationshipTemplateConfig(
            "Business Area X System",
            "business-area-x-system.xlsx",
            new String[]{
                "System Short Name",
                "Parent System Short Name",
                "Business Area Name",
                "Business Area Parent Name",
                "Relationship Type"
            },
            new boolean[]{true, false, true, false, true},
            new boolean[]{false, false, false, false, true},
            new String[]{
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Business Area name (required)",
                "Business Area parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from businessarea_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(4, "businessarea_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== CAPABILITY RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("capabilityxbusinessarea", new RelationshipTemplateConfig(
            "Capability X Business Area",
            "capability-x-business-area.xlsx",
            new String[]{
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Business Area Name",
                "Business Area Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Business Area name (required)",
                "Business Area parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from capability_x_businessarea_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "capability_x_businessarea_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("capabilityxclient", new RelationshipTemplateConfig(
            "Capability X Client",
            "capability-x-client.xlsx",
            new String[]{
                "Client Name",
                "Client Parent Name",
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Client name (required)",
                "Client parent name (optional, for disambiguation)",
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from capability_x_client_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "capability_x_client_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("capabilityxglossary", new RelationshipTemplateConfig(
            "Capability X Glossary",
            "capability-x-glossary.xlsx",
            new String[]{
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Capability Ref.",
                "Capability Name",
                "Parent Capability Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Parent Capability name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from capability_x_glossary_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "capability_x_glossary_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("capabilityxlegalentity", new RelationshipTemplateConfig(
            "Capability X Legal Entity",
            "capability-x-legal-entity.xlsx",
            new String[]{
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Legal Short Name",
                "Parent Legal Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Legal Entity short name (required)",
                "Parent Legal Entity short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from capability_x_legal_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "capability_x_legal_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("capabilityxprocess", new RelationshipTemplateConfig(
            "Capability X Process",
            "capability-x-process.xlsx",
            new String[]{
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from capability_x_process_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "capability_x_process_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("capabilityxproduct", new RelationshipTemplateConfig(
            "Capability X Product",
            "capability-x-product.xlsx",
            new String[]{
                "Product Name",
                "Product Parent Name",
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Product name (required)",
                "Product parent name (optional, for disambiguation)",
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from capability_x_product_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "capability_x_product_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("capabilityxsystem", new RelationshipTemplateConfig(
            "Capability X System",
            "capability-x-system.xlsx",
            new String[]{
                "System Short Name",
                "Parent System Short Name",
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from capability_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "capability_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== COMMITTEE RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("committeexcapability", new RelationshipTemplateConfig(
            "Committee X Capability",
            "committee-x-capability.xlsx",
            new String[]{
                "Committee Ref.",
                "Committee Name",
                "Committee Parent Name",
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Committee reference number (optional)",
                "Committee name (optional)",
                "Committee parent name (optional, for disambiguation)",
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from committee_x_capability_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "committee_x_capability_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("committeexcommittee", new RelationshipTemplateConfig(
            "Committee X Committee",
            "committee-x-committee.xlsx",
            new String[]{
                "Source Committee Ref.",
                "Source Committee Name",
                "Source Committee Parent Name",
                "Target Committee Ref.",
                "Target Committee Name",
                "Target Parent Committee Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Source Committee reference number (optional)",
                "Source Committee name (optional)",
                "Source Committee parent name (optional, for disambiguation)",
                "Target Committee reference number (optional)",
                "Target Committee name (optional)",
                "Target Committee parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from committee_x_committee_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "committee_x_committee_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== DATA SET RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("datasetxclient", new RelationshipTemplateConfig(
            "Data Set X Client",
            "data-set-x-client.xlsx",
            new String[]{
                "Client Name",
                "Client Parent Name",
                "Data Set Ref.",
                "Data Set Name",
                "Data Set System Short Name",
                "Data Set Parent System Short Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Client name (required)",
                "Client parent name (optional, for disambiguation)",
                "Data Set reference number (optional)",
                "Data Set name (optional)",
                "Data Set System short name (optional, for disambiguation)",
                "Data Set Parent System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from client_x_dataset_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "client_x_dataset_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("datasetxlegalentity", new RelationshipTemplateConfig(
            "Data Set X Legal Entity",
            "data-set-x-legal-entity.xlsx",
            new String[]{
                "Legal Entity Short Name",
                "Parent Legal Entity Short Name",
                "Data Set Ref.",
                "Data Set Name",
                "Data Set System Short Name",
                "Data Set Parent System Short Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Legal Entity short name (required)",
                "Parent Legal Entity short name (optional, for disambiguation)",
                "Data Set reference number (optional)",
                "Data Set name (optional)",
                "Data Set System short name (optional, for disambiguation)",
                "Data Set Parent System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from dataset_x_legal_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "dataset_x_legal_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("datasetxproduct", new RelationshipTemplateConfig(
            "Data Set X Product",
            "data-set-x-product.xlsx",
            new String[]{
                "Product Name",
                "Product Parent Name",
                "Data Set Ref.",
                "Data Set Name",
                "Data Set System Short Name",
                "Data Set Parent System Short Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Product name (required)",
                "Product parent name (optional, for disambiguation)",
                "Data Set reference number (optional)",
                "Data Set name (optional)",
                "Data Set System short name (optional, for disambiguation)",
                "Data Set Parent System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_dataset_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "product_x_dataset_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== GLOSSARY RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("glossaryxclient", new RelationshipTemplateConfig(
            "Glossary X Client",
            "glossary-x-client.xlsx",
            new String[]{
                "Client Name",
                "Parent Client Name",
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Client name (required)",
                "Parent Client name (optional, for disambiguation)",
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from client_x_glossary_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "client_x_glossary_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("glossaryxglossary", new RelationshipTemplateConfig(
            "Glossary X Glossary",
            "glossary-x-glossary.xlsx",
            new String[]{
                "Source Glossary Ref.",
                "Source Glossary Name",
                "Source Parent Glossary Name",
                "Target Glossary Ref.",
                "Target Glossary Name",
                "Target Parent Glossary Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Source Glossary reference number (optional)",
                "Source Glossary name (optional)",
                "Source Parent Glossary name (optional, for disambiguation)",
                "Target Glossary reference number (optional)",
                "Target Glossary name (optional)",
                "Target Parent Glossary name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from glossary_x_glossary_reltype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "glossary_x_glossary_reltype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("glossaryxproduct", new RelationshipTemplateConfig(
            "Glossary X Product",
            "glossary-x-product.xlsx",
            new String[]{
                "Product Name",
                "Product Parent Name",
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Product name (required)",
                "Product parent name (optional, for disambiguation)",
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_glossary_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "product_x_glossary_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("glossaryxsystem", new RelationshipTemplateConfig(
            "Glossary X System",
            "glossary-x-system.xlsx",
            new String[]{
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "System Short Name",
                "Strategic Data Set Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "System short name (required)",
                "Strategic Data Set name (optional)",
                "Relationship Type (required, dropdown from glossary_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "glossary_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== INTERFACE RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("interfacexglossary", new RelationshipTemplateConfig(
            "Interface X Glossary",
            "interface-x-glossary.xlsx",
            new String[]{
                "Relationship Type",
                "Interface Ref.",
                "Interface Name",
                "Interface Source System Short Name",
                "Interface Target System Short Name",
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name"
            },
            new boolean[]{true, false, false, false, false, false, false, false},
            new boolean[]{true, false, false, false, false, false, false, false},
            new String[]{
                "Relationship Type (required, dropdown from interface_x_glossary_relationtype)",
                "Interface reference number (optional)",
                "Interface name (optional)",
                "Interface source system short name (optional)",
                "Interface target system short name (optional)",
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(0, "interface_x_glossary_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxsysteminterface", new RelationshipTemplateConfig(
            "Process X System Interface",
            "process-x-system-interface.xlsx",
            new String[]{
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Interface Ref.",
                "Interface Name",
                "Interface Source System Short Name",
                "Interface Target System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Interface reference number (optional)",
                "Interface name (optional)",
                "Interface source system short name (optional)",
                "Interface target system short name (optional)",
                "Relationship Type (required, dropdown from process_x_interface_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "process_x_interface_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== LEGAL/GEOGRAPHY RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("legalentityxgeography", new RelationshipTemplateConfig(
            "Legal Entity X Geography",
            "legal-entity-x-geography.xlsx",
            new String[]{
                "Geography",
                "Parent Geography",
                "Legal Short Name",
                "Parent Legal Short Name",
                "Relationship Type"
            },
            new boolean[]{true, false, true, false, true},
            new boolean[]{false, false, false, false, true},
            new String[]{
                "Geography name (required)",
                "Parent Geography name (optional, for disambiguation)",
                "Legal Entity short name (required)",
                "Parent Legal Entity short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from legal_x_geo_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(4, "legal_x_geo_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("regulatorxgeography", new RelationshipTemplateConfig(
            "Regulator X Geography",
            "regulator-x-geography.xlsx",
            new String[]{
                "Description",
                "Regulator Name",
                "Geography Name",
                "Parent Geography Name"
            },
            new boolean[]{false, true, true, false},
            new boolean[]{false, false, false, false},
            new String[]{
                "Description (optional)",
                "Regulator name (required)",
                "Geography name (required)",
                "Parent Geography name (optional, for disambiguation)"
            },
            new ListColumnConfig[]{},
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== DATA QUALITY RULE RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("localdataqualityrulextechnicalreference", new RelationshipTemplateConfig(
            "Local Data Quality Rule X Technical Reference",
            "local-data-quality-rule-x-technical-reference.xlsx",
            new String[]{
                "Ref.",
                "Rule Name",
                "Technical Rule Reference"
            },
            new boolean[]{false, false, true},
            new boolean[]{false, false, false},
            new String[]{
                "Local Data Quality Rule reference number (optional)",
                "Local Data Quality Rule name (optional)",
                "Technical Rule Reference (required)"
            },
            new ListColumnConfig[]{},
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("standarddataqualityrulextechnicalreference", new RelationshipTemplateConfig(
            "Standard Data Quality Rule X Technical Reference",
            "standard-data-quality-rule-x-technical-reference.xlsx",
            new String[]{
                "Automation",
                "Ref.",
                "Rule Name",
                "Glossary Reference list",
                "Technical Rule Reference"
            },
            new boolean[]{false, false, false, false, true},
            new boolean[]{true, false, false, false, false},
            new String[]{
                "Automation level (optional dropdown)",
                "Standard Data Quality Rule reference number (optional)",
                "Standard Data Quality Rule name (optional)",
                "Glossary reference list (optional)",
                "Technical Rule Reference (required)"
            },
            new ListColumnConfig[]{},
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== PEOPLE RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("peoplexpeople", new RelationshipTemplateConfig(
            "People X People",
            "people-x-people.xlsx",
            new String[]{
                "Manager Email",
                "Manager First Name",
                "Manager Last Name",
                "Manager Lan ID",
                "Employee Email",
                "Employee First Name",
                "Employee Last Name",
                "Employee Lan ID",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, false, true},
            new String[]{
                "Manager email (optional)",
                "Manager first name (optional)",
                "Manager last name (optional)",
                "Manager LAN ID (optional)",
                "Employee email (optional)",
                "Employee first name (optional)",
                "Employee last name (optional)",
                "Employee LAN ID (optional)",
                "Relationship Type (required, dropdown from people_x_people_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(8, "people_x_people_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== POLICY RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("policyxattribute", new RelationshipTemplateConfig(
            "Policy X Attribute",
            "policy-x-attribute.xlsx",
            new String[]{
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Attribute Ref.",
                "Attribute Name",
                "Attribute Data Set Name",
                "Attribute System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Attribute reference number (optional)",
                "Attribute name (optional)",
                "Attribute Data Set name (optional, for disambiguation)",
                "Attribute System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_attribute_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "policy_x_attribute_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxbusinessarea", new RelationshipTemplateConfig(
            "Policy X Business Area",
            "policy-x-business-area.xlsx",
            new String[]{
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Business Area Name",
                "Parent Business Area Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Business Area name (required)",
                "Parent Business Area name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_businessarea_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "policy_x_businessarea_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxclient", new RelationshipTemplateConfig(
            "Policy X Client",
            "policy-x-client.xlsx",
            new String[]{
                "Client Name",
                "Client Parent Name",
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Client name (required)",
                "Client parent name (optional, for disambiguation)",
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from client_x_policy_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "client_x_policy_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxdataset", new RelationshipTemplateConfig(
            "Policy X Data Set",
            "policy-x-data-set.xlsx",
            new String[]{
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Data Set Ref.",
                "Data Set Name",
                "Data Set System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Data Set reference number (optional)",
                "Data Set name (optional)",
                "Data Set System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_dataset_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "policy_x_dataset_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxglossary", new RelationshipTemplateConfig(
            "Policy X Glossary",
            "policy-x-glossary.xlsx",
            new String[]{
                "Description",
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_glossary_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "policy_x_glossary_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxlegalentity", new RelationshipTemplateConfig(
            "Policy X Legal Entity",
            "policy-x-legal-entity.xlsx",
            new String[]{
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Legal Short Name",
                "Parent Legal Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Legal Entity short name (required)",
                "Parent Legal Entity short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_legal_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "policy_x_legal_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxpolicy", new RelationshipTemplateConfig(
            "Policy X Policy",
            "policy-x-policy.xlsx",
            new String[]{
                "Description",
                "Source Policy Ref.",
                "Source Policy Name",
                "Source Parent Policy Name",
                "Target Policy Ref.",
                "Target Policy Name",
                "Target Parent Policy Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Source Policy reference number (optional)",
                "Source Policy name (optional)",
                "Source Parent Policy name (optional, for disambiguation)",
                "Target Policy reference number (optional)",
                "Target Policy name (optional)",
                "Target Parent Policy name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_policy_relation_type)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "policy_x_policy_relation_type", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxprocess", new RelationshipTemplateConfig(
            "Policy X Process",
            "policy-x-process.xlsx",
            new String[]{
                "Description",
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_process_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "policy_x_process_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxproduct", new RelationshipTemplateConfig(
            "Policy X Product",
            "policy-x-product.xlsx",
            new String[]{
                "Product Name",
                "Product Parent Name",
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Product name (required)",
                "Product parent name (optional, for disambiguation)",
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_policy_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "product_x_policy_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxproject", new RelationshipTemplateConfig(
            "Policy X Project",
            "policy-x-project.xlsx",
            new String[]{
                "Description",
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Project Ref.",
                "Project Name",
                "Parent Project Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Project reference number (optional)",
                "Project name (optional)",
                "Parent Project name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_project_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "policy_x_project_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("policyxsystem", new RelationshipTemplateConfig(
            "Policy X System",
            "policy-x-system.xlsx",
            new String[]{
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "System Short Name",
                "Parent System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from policy_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "policy_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== PROCESS RELATIONSHIPS ===== (continuing in next part)
        RELATIONSHIP_CONFIG.put("processxattribute", new RelationshipTemplateConfig(
            "Process X Attribute",
            "process-x-attribute.xlsx",
            new String[]{
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Attribute Ref.",
                "Attribute Name",
                "Attribute Data Set Name",
                "Attribute System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Attribute reference number (optional)",
                "Attribute name (optional)",
                "Attribute Data Set name (optional, for disambiguation)",
                "Attribute System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from process_x_attribute_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "process_x_attribute_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxclient", new RelationshipTemplateConfig(
            "Process X Client",
            "process-x-client.xlsx",
            new String[]{
                "Client Name",
                "Client Parent Name",
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Client name (required)",
                "Client parent name (optional, for disambiguation)",
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from client_x_process_relationtype). Rows with no value here will appear in the error report as failed."
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "client_x_process_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxdataset", new RelationshipTemplateConfig(
            "Process X Data Set",
            "process-x-data-set.xlsx",
            new String[]{
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Data Set Ref.",
                "Data Set Name",
                "Data Set System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Data Set reference number (optional)",
                "Data Set name (optional)",
                "Data Set System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from process_x_dataset_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "process_x_dataset_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxglossary", new RelationshipTemplateConfig(
            "Process X Glossary",
            "process-x-glossary.xlsx",
            new String[]{
                "Description",
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from glossary_x_process_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "glossary_x_process_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxlegalentity", new RelationshipTemplateConfig(
            "Process X Legal Entity",
            "process-x-legal-entity.xlsx",
            new String[]{
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Legal Short Name",
                "Parent Legal Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Legal Entity short name (required)",
                "Parent Legal Entity short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from process_x_legal_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "process_x_legal_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxprocess", new RelationshipTemplateConfig(
            "Process X Process",
            "process-x-process.xlsx",
            new String[]{
                "Condition",
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Predecessor Process Ref.",
                "Predecessor Process Name",
                "Predecessor Parent Process Name"
            },
            new boolean[]{false, false, false, false, false, false, false},
            new boolean[]{false, false, false, false, false, false, false},
            new String[]{
                "Condition (optional)",
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Predecessor Process reference number (optional)",
                "Predecessor Process name (optional)",
                "Predecessor Parent Process name (optional, for disambiguation)"
            },
            new ListColumnConfig[]{},
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxproduct", new RelationshipTemplateConfig(
            "Process X Product",
            "process-x-product.xlsx",
            new String[]{
                "Product Name",
                "Product Parent Name",
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Product name (required)",
                "Product parent name (optional, for disambiguation)",
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_process_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "product_x_process_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("processxsystem", new RelationshipTemplateConfig(
            "Process X System",
            "process-x-system.xlsx",
            new String[]{
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "System Short Name",
                "Parent System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from process_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "process_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== PRODUCT RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("productxbusinessarea", new RelationshipTemplateConfig(
            "Product X Business Area",
            "product-x-business-area.xlsx",
            new String[]{
                "Product Ref.",
                "Product Name",
                "Product Parent Name",
                "Business Area Name",
                "Business Area Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Product reference number (optional)",
                "Product name (optional)",
                "Product parent name (optional, for disambiguation)",
                "Business Area name (optional)",
                "Business Area parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_businessarea_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "product_x_businessarea_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("productxclient", new RelationshipTemplateConfig(
            "Product X Client",
            "product-x-client.xlsx",
            new String[]{
                "Product Ref.",
                "Product Name",
                "Product Parent Name",
                "Client Name",
                "Parent Client Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Product reference number (optional)",
                "Product name (optional)",
                "Product parent name (optional, for disambiguation)",
                "Client name (required)",
                "Parent Client name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_client_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "product_x_client_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("productxlegalentity", new RelationshipTemplateConfig(
            "Product X Legal Entity",
            "product-x-legal-entity.xlsx",
            new String[]{
                "Product Ref.",
                "Product Name",
                "Product Parent Name",
                "Legal Short Name",
                "Parent Legal Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Product reference number (optional)",
                "Product name (optional)",
                "Product parent name (optional, for disambiguation)",
                "Legal Entity short name (required)",
                "Parent Legal Entity short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_legalentity_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "product_x_legalentity_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== PROJECT RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("projectxattribute", new RelationshipTemplateConfig(
            "Project X Attribute",
            "project-x-attribute.xlsx",
            new String[]{
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Attribute Ref.",
                "Attribute Name",
                "Attribute Data Set Name",
                "Attribute System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Attribute reference number (optional)",
                "Attribute name (optional)",
                "Attribute Data Set name (optional, for disambiguation)",
                "Attribute System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from project_x_attribute_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "project_x_attribute_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxbusinessarea", new RelationshipTemplateConfig(
            "Project X Business Area",
            "project-x-business-area.xlsx",
            new String[]{
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Business Area Name",
                "Business Area Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Business Area name (optional)",
                "Business Area parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from project_x_businessarea_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "project_x_businessarea_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxcapability", new RelationshipTemplateConfig(
            "Project X Capability",
            "project-x-capability.xlsx",
            new String[]{
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Capability Ref.",
                "Capability Name",
                "Capability Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Capability reference number (optional)",
                "Capability name (optional)",
                "Capability parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from project_x_capability_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "project_x_capability_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxclient", new RelationshipTemplateConfig(
            "Project X Client",
            "project-x-client.xlsx",
            new String[]{
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Client Name",
                "Client Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, true, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Client name (required)",
                "Client parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from client_x_project_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "client_x_project_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxdataset", new RelationshipTemplateConfig(
            "Project X Data Set",
            "project-x-data-set.xlsx",
            new String[]{
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Data Set Ref.",
                "Data Set Name",
                "Data Set System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Data Set reference number (optional)",
                "Data Set name (optional)",
                "Data Set System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from project_x_dataset_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "project_x_dataset_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxglossary", new RelationshipTemplateConfig(
            "Project X Glossary",
            "project-x-glossary.xlsx",
            new String[]{
                "Description",
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from glossary_x_project_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "glossary_x_project_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxprocess", new RelationshipTemplateConfig(
            "Project X Process",
            "project-x-process.xlsx",
            new String[]{
                "Description",
                "Process Ref.",
                "Process Name",
                "Parent Process Name",
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Process reference number (optional)",
                "Process name (optional)",
                "Parent Process name (optional, for disambiguation)",
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from project_x_process_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "project_x_process_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxproduct", new RelationshipTemplateConfig(
            "Project X Product",
            "project-x-product.xlsx",
            new String[]{
                "Description",
                "Product Name",
                "Product Parent Name",
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Product name (required)",
                "Product parent name (optional, for disambiguation)",
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from product_x_project_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "product_x_project_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxproject", new RelationshipTemplateConfig(
            "Project X Project",
            "project-x-project.xlsx",
            new String[]{
                "Description",
                "Source Project Ref.",
                "Source Project Name",
                "Source Project Parent Name",
                "Target Project Ref.",
                "Target Project Name",
                "Target Project Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Source Project reference number (optional)",
                "Source Project name (optional)",
                "Source Project parent name (optional, for disambiguation)",
                "Target Project reference number (optional)",
                "Target Project name (optional)",
                "Target Project parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from project_x_project_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(7, "project_x_project_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("projectxsystem", new RelationshipTemplateConfig(
            "Project X System",
            "project-x-system.xlsx",
            new String[]{
                "Description",
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "System Short Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, true, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Description (optional)",
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "System short name (required)",
                "Relationship Type (required, dropdown from project_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "project_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== REGULATION RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("regulationxpolicy", new RelationshipTemplateConfig(
            "Regulation X Policy",
            "regulation-x-policy.xlsx",
            new String[]{
                "Regulation Ref.",
                "Regulation Name",
                "Parent Regulation Name",
                "Policy Ref.",
                "Policy Name",
                "Parent Policy Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Regulation reference number (optional)",
                "Regulation name (optional)",
                "Parent Regulation name (optional, for disambiguation)",
                "Policy reference number (optional)",
                "Policy name (optional)",
                "Parent Policy name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from regulation_x_policy_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "regulation_x_policy_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("regulationxproduct", new RelationshipTemplateConfig(
            "Regulation X Product",
            "regulation-x-product.xlsx",
            new String[]{
                "Regulation Ref.",
                "Regulation Name",
                "Parent Regulation Name",
                "Product Name",
                "Product Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "Regulation reference number (optional)",
                "Regulation name (optional)",
                "Parent Regulation name (optional, for disambiguation)",
                "Product name (optional)",
                "Product parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from regulation_x_product_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "regulation_x_product_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("regulationxproject", new RelationshipTemplateConfig(
            "Regulation X Project",
            "regulation-x-project.xlsx",
            new String[]{
                "Regulation Ref.",
                "Regulation Name",
                "Parent Regulation Name",
                "Project Ref.",
                "Project Name",
                "Project Parent Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Regulation reference number (optional)",
                "Regulation name (optional)",
                "Parent Regulation name (optional, for disambiguation)",
                "Project reference number (optional)",
                "Project name (optional)",
                "Project parent name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from regulation_x_project_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "regulation_x_project_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("regulationxregulator", new RelationshipTemplateConfig(
            "Regulation X Regulator",
            "regulation-x-regulator.xlsx",
            new String[]{
                "Regulator Reg Reference",
                "Regulation Ref.",
                "Regulation Name",
                "Parent Regulation Name",
                "Regulator Name"
            },
            new boolean[]{true, false, false, false, true},
            new boolean[]{false, false, false, false, false},
            new String[]{
                "Regulator Reg Reference (required)",
                "Regulation reference number (optional)",
                "Regulation name (optional)",
                "Parent Regulation name (optional, for disambiguation)",
                "Regulator name (required)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(0, "regulation_x_regulator_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("regulationxregulatorytheme", new RelationshipTemplateConfig(
            "Regulation X Regulatory Theme",
            "regulation-x-regulatory-theme.xlsx",
            new String[]{
                "Regulation Ref.",
                "Regulation Name",
                "Parent Regulation Name",
                "Regulatory Theme Ref.",
                "Regulatory Theme Name",
                "Parent Regulatory Theme Name",
                "Relationship Type"
            },
            new boolean[]{false, false, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, false, true},
            new String[]{
                "Regulation reference number (optional)",
                "Regulation name (optional)",
                "Parent Regulation name (optional, for disambiguation)",
                "Regulatory Theme reference number (optional)",
                "Regulatory Theme name (optional)",
                "Parent Regulatory Theme name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from regulation_x_regulatorytheme_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(6, "regulation_x_regulatorytheme_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        // ===== SEGMENT RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("segmentxobject", new RelationshipTemplateConfig(
            "Segment X Object",
            "segment-x-object.xlsx",
            new String[]{
                "Object ID",
                "Object Type",
                "Target Segment"
            },
            new boolean[]{true, true, true},
            new boolean[]{false, true, true},
            new String[]{
                "Object ID (required - use the object's numeric ID, not the object Ref)",
                "Object Type (required, dropdown from facet names)",
                "Target Segment (required, dropdown from segments)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(1, "module", "primaryname"),
                new ListColumnConfig(2, "segment", "Name")
            },
            new String[]{"UPDATE"}
        ));
        
        // ===== SYSTEM RELATIONSHIPS =====
        RELATIONSHIP_CONFIG.put("systemxclient", new RelationshipTemplateConfig(
            "System X Client",
            "system-x-client.xlsx",
            new String[]{
                "Client Name",
                "Client Parent Name",
                "System Short Name",
                "Parent System Short Name",
                "Relationship Type"
            },
            new boolean[]{true, false, true, false, true},
            new boolean[]{false, false, false, false, true},
            new String[]{
                "Client name (required)",
                "Client parent name (optional, for disambiguation)",
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from client_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(4, "client_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("systemxdataonboardingrule", new RelationshipTemplateConfig(
            "System X Data Onboarding Rule",
            "system-x-data-onboarding-rule.xlsx",
            new String[]{
                "System Short Name",
                "Parent System Short Name",
                "Data Onboarding Rule Name",
                "Relationship Type"
            },
            new boolean[]{true, false, true, true},
            new boolean[]{false, false, false, true},
            new String[]{
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Data Onboarding Rule name (required)",
                "Relationship Type (required, dropdown)"
            },
            new ListColumnConfig[]{},
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("systemxglossary", new RelationshipTemplateConfig(
            "System X Glossary",
            "system-x-glossary.xlsx",
            new String[]{
                "System Short Name",
                "Parent System Short Name",
                "Glossary Ref.",
                "Glossary Name",
                "Parent Glossary Name",
                "Relationship Type"
            },
            new boolean[]{true, false, false, false, false, true},
            new boolean[]{false, false, false, false, false, true},
            new String[]{
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Glossary reference number (optional)",
                "Glossary name (optional)",
                "Parent Glossary name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from glossary_x_system_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(5, "glossary_x_system_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("systemxlegalentity", new RelationshipTemplateConfig(
            "System X Legal Entity",
            "system-x-legal-entity.xlsx",
            new String[]{
                "System Short Name",
                "Parent System Short Name",
                "Legal Entity Short Name",
                "Parent Legal Entity Short Name",
                "Relationship Type"
            },
            new boolean[]{true, false, true, false, true},
            new boolean[]{false, false, false, false, true},
            new String[]{
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Legal Entity short name (required)",
                "Parent Legal Entity short name (optional, for disambiguation)",
                "Relationship Type (required, dropdown from system_x_legal_relationtype)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(4, "system_x_legal_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("systemxproduct", new RelationshipTemplateConfig(
            "System X Product",
            "system-x-product.xlsx",
            new String[]{
                "Product Name",
                "Product Parent Name",
                "System Short Name",
                "Relationship Type",
                "Product x Legal Entity Rel Type",
                "Legal Entity Short Name",
                "Legal Entity Parent Name"
            },
            new boolean[]{true, false, true, true, false, false, false},
            new boolean[]{false, false, false, true, true, false, false},
            new String[]{
                "Product name (required)",
                "Product parent name (optional, for disambiguation)",
                "System short name (required)",
                "Relationship Type (required, dropdown from product_x_system_relationtype)",
                "Product x Legal Entity Rel Type (optional dropdown from product_x_legal_relationtype)",
                "Legal Entity short name (optional)",
                "Legal Entity parent name (optional, for disambiguation)"
            },
            new ListColumnConfig[]{
                new ListColumnConfig(3, "product_x_system_relationtype", "PrimaryName"),
                new ListColumnConfig(4, "product_x_legal_relationtype", "PrimaryName")
            },
            new String[]{"INSERT", "DELETE"}
        ));
        
        RELATIONSHIP_CONFIG.put("systemxresource", new RelationshipTemplateConfig(
            "System X Resource",
            "system-x-resource.xlsx",
            new String[]{
                "System Short Name",
                "Parent System Short Name",
                "Name",
                "Resource ID"
            },
            new boolean[]{true, false, true, true},
            new boolean[]{false, false, false, false},
            new String[]{
                "System short name (required)",
                "Parent System short name (optional, for disambiguation)",
                "Resource name (required)",
                "Resource ID (required)"
            },
            new ListColumnConfig[]{},
            new String[]{"INSERT"}
        ));
    }
    
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        CorsUtil.handlePreflight(resp);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        CorsUtil.setCorsHeaders(response);
        
        try {
            // Parse path: /api/bulk/templates/generate-relationship/{relationshipKey}/{operation}
            String pathInfo = request.getPathInfo();
            
            logger.info("Relationship template request - PathInfo: {}", pathInfo);
            
            if (pathInfo == null || pathInfo.equals("/")) {
                sendErrorResponse(response, "Missing relationship key and operation in path", 400);
                return;
            }
            
            String[] pathParts = pathInfo.substring(1).split("/");
            
            if (pathParts.length != 2) {
                sendErrorResponse(response, "Invalid path format. Expected: /api/bulk/templates/generate-relationship/{relationshipKey}/{operation}", 400);
                return;
            }
            
            String relationshipKey = pathParts[0].toLowerCase().replaceAll("\\s+", "").replaceAll("\\.", "");
            String operation = pathParts[1].toUpperCase();
            
            // Validate operation
            if (!operation.equals("INSERT") && !operation.equals("DELETE") && !operation.equals("UPDATE")) {
                sendErrorResponse(response, "Invalid operation. Must be INSERT, DELETE, or UPDATE", 400);
                return;
            }
            
            // Validate relationship key
            if (!RELATIONSHIP_CONFIG.containsKey(relationshipKey)) {
                sendErrorResponse(response, "Unsupported relationship: " + relationshipKey + ". Supported: " + RELATIONSHIP_CONFIG.keySet(), 400);
                return;
            }
            
            RelationshipTemplateConfig config = RELATIONSHIP_CONFIG.get(relationshipKey);
            
            // Validate operation is supported for this relationship
            boolean operationSupported = false;
            for (String supportedOp : config.supportedOperations) {
                if (supportedOp.equals(operation)) {
                    operationSupported = true;
                    break;
                }
            }
            
            if (!operationSupported) {
                sendErrorResponse(response, "Operation " + operation + " not supported for " + config.displayName + 
                    ". Supported operations: " + String.join(", ", config.supportedOperations), 400);
                return;
            }
            
            logger.info("Generating relationship template - Entity: {}, Operation: {}", relationshipKey, operation);
            
            // Generate Excel template
            XSSFWorkbook workbook = null;
            try {
                workbook = generateRelationshipTemplate(relationshipKey, operation, config);
                
                // Set response headers for file download
                String fileName = "TEMPLATE_" + operation + ".xlsx";
                
                response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
                response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");
                
                // Write workbook to response
                try (OutputStream outputStream = response.getOutputStream()) {
                    workbook.write(outputStream);
                    outputStream.flush();
                    logger.info("Relationship template generated successfully: {} for {}", fileName, relationshipKey);
                }
            } finally {
                if (workbook != null) {
                    try {
                        workbook.close();
                    } catch (Exception e) {
                        logger.warn("Error closing workbook: {}", e.getMessage());
                    }
                }
            }
            
        } catch (IllegalArgumentException e) {
            logger.error("Invalid argument: {}", e.getMessage(), e);
            sendErrorResponse(response, "Invalid request: " + e.getMessage(), 400);
        } catch (SQLException e) {
            logger.error("Database error generating relationship template: {}", e.getMessage(), e);
            sendErrorResponse(response, "Database error: " + e.getMessage(), 500);
        } catch (Exception e) {
            logger.error("Error generating relationship template", e);
            sendErrorResponse(response, "Internal server error: " + e.getMessage(), 500);
        }
    }
    
    /**
     * Generate Excel template for relationship
     */
    private XSSFWorkbook generateRelationshipTemplate(String relationshipKey, String operation, 
                                                      RelationshipTemplateConfig config) throws SQLException {
        XSSFWorkbook workbook = new XSSFWorkbook();
        
        // Determine sheet name based on operation
        String sheetPrefix;
        if (operation.equals("INSERT")) {
            sheetPrefix = "Create";
        } else if (operation.equals("DELETE")) {
            sheetPrefix = "Delete";
        } else {
            sheetPrefix = "Update";
        }
        String sheetName = sheetPrefix + " " + config.displayName;
        
        XSSFSheet sheet = workbook.createSheet(sheetName);
        
        // Create styles
        CellStyle headerStyle = createHeaderStyle(workbook);
        CellStyle requiredHeaderStyle = createRequiredHeaderStyle(workbook);
        
        // Create header row
        Row headerRow = sheet.createRow(0);

        // Prepare for comments (notes) on header cells
        CreationHelper creationHelper = workbook.getCreationHelper();
        XSSFDrawing drawing = sheet.createDrawingPatriarch();

        for (int i = 0; i < config.columns.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(config.columns[i]);
            cell.setCellStyle(config.required[i] ? requiredHeaderStyle : headerStyle);

            // Resolve description for this header:
            // 1) Try to get from notes.json (per file & header)
            // 2) Fallback to static description from config (if any)
            String description = null;
            if (config.fileName != null) {
                Map<String, String> fileNotes = NOTES_BY_FILE_AND_HEADER.get(config.fileName);
                if (fileNotes != null) {
                    description = fileNotes.get(config.columns[i]);
                }
            }
            if ((description == null || description.isEmpty()) &&
                config.descriptions != null && i < config.descriptions.length) {
                description = config.descriptions[i];
            }

            if (description != null && !description.isEmpty()) {
                ClientAnchor anchor = creationHelper.createClientAnchor();
                anchor.setCol1(i);
                anchor.setCol2(i + 5);

                int approxCharsPerLine = 45;
                int lines = (description.length() / approxCharsPerLine) + 1;
                int rowsHigh = Math.min(20, 3 + lines);

                anchor.setRow1(0);
                anchor.setRow2(rowsHigh);

                XSSFComment comment = drawing.createCellComment(anchor);
                comment.setString(creationHelper.createRichTextString(description));
                cell.setCellComment(comment);
            }
        }
        
        // Process list columns if any
        if (config.listColumns != null && config.listColumns.length > 0) {
            for (ListColumnConfig listCol : config.listColumns) {
                try {
                    RelationshipListFillTemplate.addListValidation(
                        workbook, 
                        sheet, 
                        listCol.columnIndex, 
                        listCol.tableName, 
                        listCol.displayColumn
                    );
                } catch (SQLException e) {
                    logger.warn("Failed to populate list for column {} from table {}: {}", 
                        listCol.columnIndex, listCol.tableName, e.getMessage());
                }
            }
        }
        
        // Auto-size columns
        for (int i = 0; i < config.columns.length; i++) {
            sheet.autoSizeColumn(i);
            // Add extra width for better readability
            sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
        }
        
        return workbook;
    }
    
    /**
     * Create header cell style
     */
    private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Create required header cell style (red font)
     */
    private CellStyle createRequiredHeaderStyle(XSSFWorkbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.RED.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Load notes definitions from notes.json into a map: Map<fileName, Map<headerName, noteText>>
     */
    private static Map<String, Map<String, String>> loadNotesFromJson() {
        Map<String, Map<String, String>> result = new HashMap<>();
        try {
            // Try to load from classpath resources
            java.io.InputStream is = BulkRelationshipsTemplateGeneratorServlet.class
                    .getClassLoader()
                    .getResourceAsStream("relationships-notes.json");

            if (is == null) {
                // Fallback: try relative file path from working directory
                Path path = Paths.get("src/main/resources/relationships-notes.json");
                if (Files.exists(path)) {
                    is = Files.newInputStream(path);
                }
            }

            if (is == null) {
                logger.warn("relationships-notes.json not found on classpath or expected path. Relationship header notes will fall back to static descriptions.");
                return result;
            }

            try (java.io.Reader reader = new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8)) {
                NotesEntry[] entries = gson.fromJson(reader, NotesEntry[].class);
                if (entries != null) {
                    for (NotesEntry entry : entries) {
                        if (entry == null || entry.fileName == null || entry.headerName == null) continue;
                        String fileName = entry.fileName.trim();
                        String headerName = entry.headerName.trim();
                        String noteText = entry.noteText != null ? entry.noteText.trim() : "";
                        if (fileName.isEmpty() || headerName.isEmpty() || noteText.isEmpty()) continue;

                        result
                            .computeIfAbsent(fileName, k -> new HashMap<>())
                            .put(headerName, noteText);
                    }
                }
                logger.info("Loaded {} files of header notes from relationships-notes.json", result.size());
            }
        } catch (Exception e) {
            logger.error("Failed to load relationships-notes.json for relationship templates: {}", e.getMessage(), e);
        }
        return result;
    }
    
    /**
     * Send JSON error response
     */
    private void sendErrorResponse(HttpServletResponse response, String message, int statusCode) 
            throws IOException {
        response.setStatus(statusCode);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        JsonObject errorResponse = new JsonObject();
        errorResponse.addProperty("status", "error");
        errorResponse.addProperty("message", message);
        
        response.getWriter().write(gson.toJson(errorResponse));
    }
    
    /**
     * Configuration class for relationship templates
     */
    private static class RelationshipTemplateConfig {
        String displayName;
        String fileName;
        String[] columns;
        boolean[] required;
        @SuppressWarnings("unused")
        boolean[] isList;
        String[] descriptions;
        ListColumnConfig[] listColumns;
        String[] supportedOperations;
        
        RelationshipTemplateConfig(String displayName, String fileName, String[] columns, 
                                 boolean[] required, boolean[] isList, String[] descriptions, 
                                 ListColumnConfig[] listColumns, String[] supportedOperations) {
            this.displayName = displayName;
            this.fileName = fileName;
            this.columns = columns;
            this.required = required;
            this.isList = isList;
            this.descriptions = descriptions;
            this.listColumns = listColumns;
            this.supportedOperations = supportedOperations;
        }
    }
    
    /**
     * Configuration for list columns
     */
    static class ListColumnConfig {
        int columnIndex;
        String tableName;
        String displayColumn;
        
        ListColumnConfig(int columnIndex, String tableName, String displayColumn) {
            this.columnIndex = columnIndex;
            this.tableName = tableName;
            this.displayColumn = displayColumn;
        }
    }
    
    /**
     * Helper DTO to map entries from notes.json
     */
    private static class NotesEntry {
        @SerializedName("File Name")
        String fileName;

        @SerializedName("Header Name")
        String headerName;

        @SerializedName("Note Text")
        String noteText;
    }
}

