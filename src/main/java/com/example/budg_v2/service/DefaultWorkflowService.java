package com.example.budg_v2.service;

import com.example.budg_v2.dao.BpmnContentDAO;
import com.example.budg_v2.dao.ModuleDAO;
import com.example.budg_v2.dao.ProcessDefinitionDAO;
import com.example.budg_v2.model.ProcessDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.budg_v2.model.Module;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing default workflows
 */
public class DefaultWorkflowService {
    
    private static final Logger logger = LoggerFactory.getLogger(DefaultWorkflowService.class);
    private static final String DEFAULT_WORKFLOWS_DIR = "data/default-workflows/";
    
    /**
     * Get the base directory (Tomcat bin directory or current working directory)
     */
    private static Path getBaseDirectory() {
        // Try catalina.base first (Tomcat installation directory)
        String catalinaBase = System.getProperty("catalina.base");
        if (catalinaBase != null && !catalinaBase.isEmpty()) {
            return Paths.get(catalinaBase);
        }
        
        // Try catalina.home as fallback
        String catalinaHome = System.getProperty("catalina.home");
        if (catalinaHome != null && !catalinaHome.isEmpty()) {
            return Paths.get(catalinaHome);
        }
        
        // Fallback to user.dir (current working directory)
        return Paths.get(System.getProperty("user.dir", "."));
    }
    
    /**
     * Get the absolute path to default workflows directory
     */
    private static Path getDefaultWorkflowsPath() {
        return getBaseDirectory().resolve(DEFAULT_WORKFLOWS_DIR);
    }
    private static final String TEMPLATE_WORKFLOW = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<bpmn2:definitions xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns:bpmn2=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:bpmndi=\"http://www.omg.org/spec/BPMN/20100524/DI\" xmlns:dc=\"http://www.omg.org/spec/DD/20100524/DC\" xmlns:di=\"http://www.omg.org/spec/DD/20100524/DI\" xmlns:camunda=\"http://activiti.org/bpmn\" id=\"ChangeRequest-CatItemCategory-default-1554893219\" targetNamespace=\"ChangeRequest-CatItemCategory\" exporter=\"budg\" exporterVersion=\"1.0.0\" xsi:schemaLocation=\"http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd\">\n" +
            "  <bpmn2:collaboration id=\"ChangeRequest-CatItemCategory-default1554893219\" name=\"Approval for Object Definition\">\n" +
            "    <bpmn2:participant id=\"ChangeRequest-CatItemCategory-default-pool-participant\" name=\"Roles\" processRef=\"changerequest-Wf-Client-7824-1742123186297-13-0\" />\n" +
            "  </bpmn2:collaboration>\n" +
            "  <bpmn2:process id=\"changerequest-Wf-Client-7824-1742123186297-13-0\" name=\"DefaultWorkflow-1-Client\" isExecutable=\"true\">\n" +
            "    <bpmn2:laneSet id=\"ChangeRequest-CatItemCategory-default-pool-laneset-1554893219\" name=\"ChangeRequest-CatItemCategory-default-pool-laneset-1554893219\">\n" +
            "      <bpmn2:lane id=\"ChangeRequest-CatItemCategory-default-pool-laneset-1554893219-lane-9\" name=\"3:Client Segmentation Owner\">\n" +
            "        <bpmn2:flowNodeRef>StartEvent_00htd7i</bpmn2:flowNodeRef>\n" +
            "        <bpmn2:flowNodeRef>EndEvent_1ge7wxq</bpmn2:flowNodeRef>\n" +
            "        <bpmn2:flowNodeRef>UserTask_09wiquf</bpmn2:flowNodeRef>\n" +
            "        <bpmn2:flowNodeRef>UserTask_1fytr6i</bpmn2:flowNodeRef>\n" +
            "        <bpmn2:flowNodeRef>UserTask_1r85tib</bpmn2:flowNodeRef>\n" +
            "        <bpmn2:flowNodeRef>ExclusiveGateway_1m7r66b</bpmn2:flowNodeRef>\n" +
            "        <bpmn2:flowNodeRef>UserTask_0un1rwo</bpmn2:flowNodeRef>\n" +
            "        <bpmn2:flowNodeRef>EndEvent_02vx5jj</bpmn2:flowNodeRef>\n" +
            "      </bpmn2:lane>\n" +
            "      <bpmn2:lane id=\"Lane_1alu7bq\" name=\"R01:Requestor\" />\n" +
            "    </bpmn2:laneSet>\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_0v3fa35\" sourceRef=\"UserTask_0un1rwo\" targetRef=\"EndEvent_1ge7wxq\" />\n" +
            "    <bpmn2:startEvent id=\"StartEvent_00htd7i\" name=\"Start\">\n" +
            "      <bpmn2:outgoing>SequenceFlow_0tsxvfo</bpmn2:outgoing>\n" +
            "    </bpmn2:startEvent>\n" +
            "    <bpmn2:endEvent id=\"EndEvent_1ge7wxq\" name=\"End-Comms\">\n" +
            "      <bpmn2:incoming>SequenceFlow_0v3fa35</bpmn2:incoming>\n" +
            "    </bpmn2:endEvent>\n" +
            "    <bpmn2:userTask id=\"UserTask_09wiquf\" name=\"Implement\" camunda:dueDate=\"5\">\n" +
            "      <bpmn2:documentation>The approved request is implemented. All necessary communications are made. The change request can then be closed.</bpmn2:documentation>\n" +
            "      <bpmn2:incoming>SequenceFlow_17adjgx</bpmn2:incoming>\n" +
            "      <bpmn2:outgoing>SequenceFlow_1s6hi6r</bpmn2:outgoing>\n" +
            "    </bpmn2:userTask>\n" +
            "    <bpmn2:userTask id=\"UserTask_1fytr6i\" name=\"Assessment\" camunda:dueDate=\"5\">\n" +
            "      <bpmn2:documentation>Perform all necessary work to review and assess the request and understand the impact, or rework proposal. Prepare recommendations.</bpmn2:documentation>\n" +
            "      <bpmn2:incoming>SequenceFlow_1o7zucb</bpmn2:incoming>\n" +
            "      <bpmn2:incoming>SequenceFlow_0tsxvfo</bpmn2:incoming>\n" +
            "      <bpmn2:outgoing>SequenceFlow_07gbhm3</bpmn2:outgoing>\n" +
            "    </bpmn2:userTask>\n" +
            "    <bpmn2:userTask id=\"UserTask_1r85tib\" name=\"Approval\" camunda:dueDate=\"5\">\n" +
            "      <bpmn2:documentation>Recommendations are assessed for completeness and approval. Should this request be implemented?</bpmn2:documentation>\n" +
            "      <bpmn2:incoming>SequenceFlow_07gbhm3</bpmn2:incoming>\n" +
            "      <bpmn2:outgoing>SequenceFlow_115x6b0</bpmn2:outgoing>\n" +
            "    </bpmn2:userTask>\n" +
            "    <bpmn2:exclusiveGateway id=\"ExclusiveGateway_1m7r66b\" name=\"Decision\">\n" +
            "      <bpmn2:incoming>SequenceFlow_115x6b0</bpmn2:incoming>\n" +
            "      <bpmn2:outgoing>SequenceFlow_1o7zucb</bpmn2:outgoing>\n" +
            "      <bpmn2:outgoing>SequenceFlow_17adjgx</bpmn2:outgoing>\n" +
            "      <bpmn2:outgoing>SequenceFlow_0ejcxta</bpmn2:outgoing>\n" +
            "    </bpmn2:exclusiveGateway>\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_0tsxvfo\" sourceRef=\"StartEvent_00htd7i\" targetRef=\"UserTask_1fytr6i\" />\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_17adjgx\" name=\"Approved\" sourceRef=\"ExclusiveGateway_1m7r66b\" targetRef=\"UserTask_09wiquf\">\n" +
            "      <bpmn2:conditionExpression xsi:type=\"bpmn2:tFormalExpression\"><![CDATA[${last_approval == 'Approved'}]]></bpmn2:conditionExpression>\n" +
            "    </bpmn2:sequenceFlow>\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_1o7zucb\" name=\"Rework\" sourceRef=\"ExclusiveGateway_1m7r66b\" targetRef=\"UserTask_1fytr6i\">\n" +
            "      <bpmn2:documentation>A decision cannot be made on the information available. The request is returned for additional actions. Ensure that reasons for rework are stated in the comments, or the analysis field on the summary tab.</bpmn2:documentation>\n" +
            "      <bpmn2:conditionExpression xsi:type=\"bpmn2:tFormalExpression\"><![CDATA[${last_approval == 'Rework'}]]></bpmn2:conditionExpression>\n" +
            "    </bpmn2:sequenceFlow>\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_07gbhm3\" sourceRef=\"UserTask_1fytr6i\" targetRef=\"UserTask_1r85tib\" />\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_115x6b0\" sourceRef=\"UserTask_1r85tib\" targetRef=\"ExclusiveGateway_1m7r66b\" />\n" +
            "    <bpmn2:userTask id=\"UserTask_0un1rwo\" name=\"Communicate\" camunda:dueDate=\"5\">\n" +
            "      <bpmn2:documentation>Change Request object should clearly outline reasons for rejection (e.g. in Resolution), requester and other interested parties should be informed. Ensure that these reasons are either recorded in comments or appended to the request, so we have a record. Request can then be closed.</bpmn2:documentation>\n" +
            "      <bpmn2:incoming>SequenceFlow_0ejcxta</bpmn2:incoming>\n" +
            "      <bpmn2:outgoing>SequenceFlow_0v3fa35</bpmn2:outgoing>\n" +
            "    </bpmn2:userTask>\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_0ejcxta\" name=\"Rejected\" sourceRef=\"ExclusiveGateway_1m7r66b\" targetRef=\"UserTask_0un1rwo\">\n" +
            "      <bpmn2:conditionExpression xsi:type=\"bpmn2:tFormalExpression\"><![CDATA[${last_approval == 'Rejected'}]]></bpmn2:conditionExpression>\n" +
            "    </bpmn2:sequenceFlow>\n" +
            "    <bpmn2:endEvent id=\"EndEvent_02vx5jj\" name=\"End-Implement\">\n" +
            "      <bpmn2:incoming>SequenceFlow_1s6hi6r</bpmn2:incoming>\n" +
            "    </bpmn2:endEvent>\n" +
            "    <bpmn2:sequenceFlow id=\"SequenceFlow_1s6hi6r\" sourceRef=\"UserTask_09wiquf\" targetRef=\"EndEvent_02vx5jj\" />\n" +
            "  </bpmn2:process>\n" +
            "  <bpmndi:BPMNDiagram id=\"BPMNDiagram_ChangeRequest-CatItemCategory-default_diagram\">\n" +
            "    <bpmndi:BPMNPlane id=\"BPMNDiagram_ChangeRequest-CatItemCategory-default\" bpmnElement=\"ChangeRequest-CatItemCategory-default1554893219\">\n" +
            "      <bpmndi:BPMNShape id=\"BPMNDiagram_ChangeRequest-CatItemCategory-defaultChangeRequest-CatItemCategory-default-pool-participant\" bpmnElement=\"ChangeRequest-CatItemCategory-default-pool-participant\" isHorizontal=\"true\">\n" +
            "        <dc:Bounds x=\"248\" y=\"98\" width=\"1040\" height=\"551\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"BPMNDiagram_ChangeRequest-CatItemCategory-defaultChangeRequest-CatItemCategory-default-pool-laneset-1554893219-lane-9\" bpmnElement=\"ChangeRequest-CatItemCategory-default-pool-laneset-1554893219-lane-9\" isHorizontal=\"true\">\n" +
            "        <dc:Bounds x=\"278\" y=\"98\" width=\"1010\" height=\"431\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"StartEvent_00htd7i_di\" bpmnElement=\"StartEvent_00htd7i\">\n" +
            "        <dc:Bounds x=\"323\" y=\"348\" width=\"36\" height=\"36\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"296\" y=\"384\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_0tsxvfo_di\" bpmnElement=\"SequenceFlow_0tsxvfo\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"359\" y=\"366\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"426\" y=\"366\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"348\" y=\"341\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNShape id=\"UserTask_0un1rwo_di\" bpmnElement=\"UserTask_0un1rwo\">\n" +
            "        <dc:Bounds x=\"789\" y=\"402\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"EndEvent_1ge7wxq_di\" bpmnElement=\"EndEvent_1ge7wxq\">\n" +
            "        <dc:Bounds x=\"1077\" y=\"424\" width=\"36\" height=\"36\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"1050\" y=\"460\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_0v3fa35_di\" bpmnElement=\"SequenceFlow_0v3fa35\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"889\" y=\"442\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"993\" y=\"442\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"993\" y=\"442\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"1077\" y=\"442\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"963\" y=\"432\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNShape id=\"UserTask_1fytr6i_di\" bpmnElement=\"UserTask_1fytr6i\">\n" +
            "        <dc:Bounds x=\"426\" y=\"326\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"UserTask_1r85tib_di\" bpmnElement=\"UserTask_1r85tib\">\n" +
            "        <dc:Bounds x=\"572\" y=\"250\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNShape id=\"ExclusiveGateway_1m7r66b_di\" bpmnElement=\"ExclusiveGateway_1m7r66b\" isMarkerVisible=\"true\">\n" +
            "        <dc:Bounds x=\"752\" y=\"265\" width=\"50\" height=\"50\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"732\" y=\"315\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_07gbhm3_di\" bpmnElement=\"SequenceFlow_07gbhm3\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"476\" y=\"326\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"476\" y=\"290\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"572\" y=\"290\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"446\" y=\"298\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_115x6b0_di\" bpmnElement=\"SequenceFlow_115x6b0\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"672\" y=\"290\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"752\" y=\"290\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"667\" y=\"265\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_1o7zucb_di\" bpmnElement=\"SequenceFlow_1o7zucb\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"777\" y=\"315\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"777\" y=\"366\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"526\" y=\"366\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"710\" y=\"326\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNShape id=\"UserTask_09wiquf_di\" bpmnElement=\"UserTask_09wiquf\">\n" +
            "        <dc:Bounds x=\"922\" y=\"258\" width=\"100\" height=\"80\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_17adjgx_di\" bpmnElement=\"SequenceFlow_17adjgx\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"777\" y=\"265\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"777\" y=\"203\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"972\" y=\"203\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"972\" y=\"258\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"830\" y=\"178\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_0ejcxta_di\" bpmnElement=\"SequenceFlow_0ejcxta\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"802\" y=\"290\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"839\" y=\"290\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"839\" y=\"402\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"775.5\" y=\"265\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNShape id=\"EndEvent_02vx5jj_di\" bpmnElement=\"EndEvent_02vx5jj\">\n" +
            "        <dc:Bounds x=\"1089\" y=\"280\" width=\"36\" height=\"36\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"1062\" y=\"316\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNShape>\n" +
            "      <bpmndi:BPMNEdge id=\"SequenceFlow_1s6hi6r_di\" bpmnElement=\"SequenceFlow_1s6hi6r\">\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"1022\" y=\"298\" />\n" +
            "        <di:waypoint xsi:type=\"dc:Point\" x=\"1089\" y=\"298\" />\n" +
            "        <bpmndi:BPMNLabel>\n" +
            "          <dc:Bounds x=\"1010.5\" y=\"273\" width=\"90\" height=\"20\" />\n" +
            "        </bpmndi:BPMNLabel>\n" +
            "      </bpmndi:BPMNEdge>\n" +
            "      <bpmndi:BPMNShape id=\"Lane_1alu7bq_di\" bpmnElement=\"Lane_1alu7bq\">\n" +
            "        <dc:Bounds x=\"278\" y=\"529\" width=\"1010\" height=\"120\" />\n" +
            "      </bpmndi:BPMNShape>\n" +
            "    </bpmndi:BPMNPlane>\n" +
            "  </bpmndi:BPMNDiagram>\n" +
            "</bpmn2:definitions>";
    
    private final ProcessDefinitionDAO processDefinitionDAO;
    private final BpmnContentDAO bpmnContentDAO;
    private final ModuleDAO moduleDAO;
    
    // Facets that need default workflows
    private static final List<String> FACETS = Arrays.asList(
        "Business Area", "Capability", "Glossary", "Data Sets", "Client", 
        "Committee", "Policy", "Process", "Product", "Project", 
        "Regulation", "System", "Interface"
    );
    
    public DefaultWorkflowService() {
        this.processDefinitionDAO = new ProcessDefinitionDAO();
        this.bpmnContentDAO = new BpmnContentDAO();
        this.moduleDAO = new ModuleDAO();
    }
    
    /**
     * Generate BPMN XML for a specific facet based on template
     */
    public String generateBpmnForFacet(String facetName) {
        long timestamp = System.currentTimeMillis();
        String workflowName = "DefaultWorkflow-1-" + facetName;
        String processId = "changerequest-Wf-" + sanitizeFacetName(facetName) + "-" + timestamp;
        String collaborationId = "ChangeRequest-" + sanitizeFacetName(facetName) + "-default" + timestamp;
        String participantId = "ChangeRequest-" + sanitizeFacetName(facetName) + "-default-pool-participant";
        String laneSetId = processId + "-laneset-" + timestamp;
        String laneId = laneSetId + "-lane-9";
        
        // Get the correct role name for this facet
        String roleName = getRoleNameForFacet(facetName);
        
        // Replace template values with facet-specific values
        String bpmn = TEMPLATE_WORKFLOW
            .replace("DefaultWorkflow-1-Client", workflowName)
            .replace("changerequest-Wf-Client-7824-1742123186297-13-0", processId)
            .replace("ChangeRequest-CatItemCategory-default1554893219", collaborationId)
            .replace("ChangeRequest-CatItemCategory-default-pool-participant", participantId)
            .replace("ChangeRequest-CatItemCategory-default-pool-laneset-1554893219", laneSetId)
            .replace("ChangeRequest-CatItemCategory-default-pool-laneset-1554893219-lane-9", laneId)
            .replace("3:Client Segmentation Owner", "3:" + roleName)
            .replace("ChangeRequest-CatItemCategory", "ChangeRequest-" + sanitizeFacetName(facetName))
            .replace("1554893219", String.valueOf(timestamp));
        
        return bpmn;
    }
    
    /**
     * Sanitize facet name for use in IDs
     */
    private String sanitizeFacetName(String facetName) {
        return facetName.replaceAll("[^a-zA-Z0-9]", "");
    }
    
    /**
     * Get the correct role name for a facet
     * Maps facet names to their actual role names in the database
     * Based on object_role table data
     * 
     * @param facetName The facet name (e.g., "Data Sets", "Business Area")
     * @return The correct role name (e.g., "Dataset Owner", "Business Area Head")
     */
    private String getRoleNameForFacet(String facetName) {
        if (facetName == null || facetName.trim().isEmpty()) {
            return "Owner";
        }
        
        // Handle special cases where facet name doesn't match role name
        // Based on object_role.sql data
        switch (facetName.trim()) {
            case "Data Sets":
                return "Dataset Owner";  // module 11, id 17
            case "Client":
                return "Client Segmentation Owner";  // module 17, id 27
            case "System":
                return "System Business Owner";  // module 13, id 21
            case "Business Area":
                return "Business Area Head";  // module 15, id 25
            case "Project":
                return "Project Manager";  // module 5, id 9
            default:
                // For most facets, use facet name + " Owner"
                // Examples: "Glossary Owner", "Policy Owner", "Process Owner", etc.
                return facetName.trim() + " Owner";
        }
    }
    
    /**
     * Create default workflow for a facet
     */
    public boolean createDefaultWorkflowForFacet(String facetName) {
        try {
            // Get module ID for facet
            Integer moduleId = null;
            try {
                List<Module> modules = moduleDAO.getAllModules();
                for (Module m : modules) {
                    if (facetName.equals(m.getPrimaryName())) {
                        moduleId = m.getId();
                        break;
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting modules", e);
                return false;
            }
            
            if (moduleId == null) {
                logger.warn("Module not found for facet: {}", facetName);
                return false;
            }
            
            // Check if default workflow already exists
            List<ProcessDefinition> existing = processDefinitionDAO.findByEntityId(moduleId);
            boolean hasDefault = existing.stream()
                .anyMatch(pd -> pd.isDefault() && pd.getPrimaryName().startsWith("DefaultWorkflow-1-"));
            
            if (hasDefault) {
                logger.debug("Default workflow already exists for facet: {}", facetName);
                return true;
            }
            
            // Generate BPMN XML
            String bpmnXml = generateBpmnForFacet(facetName);
            String workflowName = "DefaultWorkflow-1-" + facetName;
            
            // Create ProcessDefinition
            ProcessDefinition pd = new ProcessDefinition();
            pd.setPrimaryName(workflowName);
            pd.setReference(workflowName);
            pd.setDefault(true);
            pd.setDescription("Default workflow for " + facetName);
            pd.setStatus("Enabled");
            pd.setLastUserChange(1); // System user
            pd.setEntityId(moduleId);
            
            int processDefId = processDefinitionDAO.create(pd);
            
            // Save BPMN to database
            bpmnContentDAO.saveOrUpdate(processDefId, bpmnXml);
            
            // Save BPMN to file system
            try {
                ensureDefaultWorkflowsDirectory();
                String filename = workflowName + ".bpmn";
                Path filePath = getDefaultWorkflowsPath().resolve(filename);
                Files.write(filePath, bpmnXml.getBytes("UTF-8"));
                logger.info("Created default workflow file: {}", filePath.toAbsolutePath());
            } catch (IOException e) {
                logger.warn("Failed to save BPMN file to disk: {}", e.getMessage());
                // Continue - database save is more important
            }
            
            logger.info("Created default workflow for facet: {} (ID: {})", facetName, processDefId);
            return true;
            
        } catch (Exception e) {
            logger.error("Error creating default workflow for facet: " + facetName, e);
            return false;
        }
    }
    
    /**
     * Create default workflows for all facets
     */
    public void createDefaultWorkflowsForAllFacets() {
        logger.info("Creating default workflows for all facets...");
        int created = 0;
        int skipped = 0;
        
        for (String facet : FACETS) {
            if (createDefaultWorkflowForFacet(facet)) {
                created++;
            } else {
                skipped++;
            }
        }
        
        logger.info("Default workflows creation completed. Created: {}, Skipped: {}", created, skipped);
    }
    
    /**
     * Get the default workflows directory path (returns only the directory path, not individual files)
     */
    public String getDefaultWorkflowsDirectoryPath() {
        try {
            Path workflowsPath = getDefaultWorkflowsPath();
            // Normalize the path (convert to Windows format if needed)
            String path = workflowsPath.toAbsolutePath().normalize().toString();
            // Ensure it uses backslashes on Windows
            if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                path = path.replace("/", "\\");
            }
            return path;
        } catch (Exception e) {
            logger.error("Error getting default workflows directory path", e);
            // Fallback to base directory
            return getBaseDirectory().resolve(DEFAULT_WORKFLOWS_DIR).toAbsolutePath().normalize().toString();
        }
    }
    
    /**
     * Ensure default workflows directory exists
     */
    private void ensureDefaultWorkflowsDirectory() throws IOException {
        Path dirPath = getDefaultWorkflowsPath();
        if (!Files.exists(dirPath)) {
            Files.createDirectories(dirPath);
            logger.info("Created default workflows directory: {}", dirPath.toAbsolutePath());
        }
    }
    
    /**
     * Reset default workflow for a facet (reset to original or create if doesn't exist)
     * @param facetName The facet name
     * @return true if successful, false otherwise
     */
    public boolean resetDefaultWorkflowForFacet(String facetName) {
        try {
            // Get module ID for facet
            Integer moduleId = null;
            try {
                List<Module> modules = moduleDAO.getAllModules();
                for (Module m : modules) {
                    if (facetName.equals(m.getPrimaryName())) {
                        moduleId = m.getId();
                        break;
                    }
                }
            } catch (SQLException e) {
                logger.error("Error getting modules for facet: {}", facetName, e);
                return false;
            }
            
            if (moduleId == null) {
                logger.warn("Module not found for facet: {}", facetName);
                return false;
            }
            
            // Find all workflows with default name pattern for this facet
            // This includes duplicates that need to be cleaned up
            List<ProcessDefinition> existing = processDefinitionDAO.findByEntityId(moduleId);
            String expectedWorkflowName = "DefaultWorkflow-1-" + facetName;
            
            // Find all workflows with the default name (to detect duplicates)
            List<ProcessDefinition> defaultWorkflows = existing.stream()
                .filter(pd -> pd.getPrimaryName() != null && pd.getPrimaryName().equals(expectedWorkflowName))
                .collect(Collectors.toList());
            
            // Select the workflow to keep (priority: is_default = 1, then first found)
            ProcessDefinition existingDefault = defaultWorkflows.stream()
                .filter(pd -> pd.isDefault())
                .findFirst()
                .orElse(defaultWorkflows.isEmpty() ? null : defaultWorkflows.get(0));
            
            // Delete duplicate workflows (keep only one)
            if (defaultWorkflows.size() > 1) {
                logger.info("Found {} duplicate default workflows for facet: {}. Deleting duplicates...", 
                           defaultWorkflows.size(), facetName);
                for (ProcessDefinition duplicate : defaultWorkflows) {
                    if (existingDefault == null || duplicate.getId() != existingDefault.getId()) {
                        try {
                            // Delete BPMN content first
                            bpmnContentDAO.delete(duplicate.getId());
                            // Delete process definition
                            processDefinitionDAO.delete(duplicate.getId());
                            logger.info("Deleted duplicate default workflow: {} (ID: {})", 
                                       duplicate.getPrimaryName(), duplicate.getId());
                        } catch (Exception e) {
                            logger.error("Error deleting duplicate workflow ID: {}", duplicate.getId(), e);
                        }
                    }
                }
            }
            
            // Generate new BPMN XML with original template
            String bpmnXml = generateBpmnForFacet(facetName);
            String workflowName = "DefaultWorkflow-1-" + facetName;
            
            if (existingDefault != null) {
                // Reset existing workflow
                int processDefId = existingDefault.getId();
                
                // Update ProcessDefinition to original values
                existingDefault.setPrimaryName(workflowName);
                existingDefault.setReference(workflowName);
                existingDefault.setDefault(true);
                existingDefault.setDescription("Default workflow for " + facetName);
                existingDefault.setStatus("Enabled");
                existingDefault.setLastUserChange(1); // System user
                processDefinitionDAO.update(existingDefault);
                
                // Update BPMN content
                bpmnContentDAO.saveOrUpdate(processDefId, bpmnXml);
                
                // Update file system
                try {
                    ensureDefaultWorkflowsDirectory();
                    String filename = workflowName + ".bpmn";
                    Path filePath = getDefaultWorkflowsPath().resolve(filename);
                    Files.write(filePath, bpmnXml.getBytes("UTF-8"));
                    logger.info("Reset default workflow file: {}", filePath.toAbsolutePath());
                } catch (IOException e) {
                    logger.warn("Failed to save BPMN file to disk: {}", e.getMessage());
                    // Continue - database save is more important
                }
                
                logger.info("Reset default workflow for facet: {} (ID: {})", facetName, processDefId);
                return true;
            } else {
                // Create new default workflow
                ProcessDefinition pd = new ProcessDefinition();
                pd.setPrimaryName(workflowName);
                pd.setReference(workflowName);
                pd.setDefault(true);
                pd.setDescription("Default workflow for " + facetName);
                pd.setStatus("Enabled");
                pd.setLastUserChange(1); // System user
                pd.setEntityId(moduleId);
                
                int processDefId = processDefinitionDAO.create(pd);
                
                // Save BPMN to database
                bpmnContentDAO.saveOrUpdate(processDefId, bpmnXml);
                
                // Save BPMN to file system
                try {
                    ensureDefaultWorkflowsDirectory();
                    String filename = workflowName + ".bpmn";
                    Path filePath = getDefaultWorkflowsPath().resolve(filename);
                    Files.write(filePath, bpmnXml.getBytes("UTF-8"));
                    logger.info("Created default workflow file: {}", filePath.toAbsolutePath());
                } catch (IOException e) {
                    logger.warn("Failed to save BPMN file to disk: {}", e.getMessage());
                    // Continue - database save is more important
                }
                
                logger.info("Created default workflow for facet: {} (ID: {})", facetName, processDefId);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Error resetting default workflow for facet: " + facetName, e);
            return false;
        }
    }
    
    /**
     * Reset all default workflows (reset existing or create new)
     * @return Result object with counts of created, reset, and errors
     */
    public ResetResult resetAllDefaultWorkflows() {
        logger.info("Resetting all default workflows...");
        int created = 0;
        int reset = 0;
        List<String> errors = new java.util.ArrayList<>();
        
        for (String facet : FACETS) {
            try {
                // Find existing default workflow to determine if it's a reset or create
                Integer moduleId = null;
                try {
                    List<Module> modules = moduleDAO.getAllModules();
                    for (Module m : modules) {
                        if (facet.equals(m.getPrimaryName())) {
                            moduleId = m.getId();
                            break;
                        }
                    }
                } catch (SQLException e) {
                    logger.error("Error getting modules for facet: {}", facet, e);
                    errors.add("Facet " + facet + ": " + e.getMessage());
                    continue;
                }
                
                if (moduleId == null) {
                    logger.warn("Module not found for facet: {}", facet);
                    errors.add("Facet " + facet + ": Module not found");
                    continue;
                }
                
                // Check if default workflow exists
                // Check for workflow with is_default = 1 first, then by name
                List<ProcessDefinition> existing = processDefinitionDAO.findByEntityId(moduleId);
                String expectedWorkflowName = "DefaultWorkflow-1-" + facet;
                boolean hasDefault = existing.stream()
                    .anyMatch(pd -> (pd.isDefault() && pd.getPrimaryName() != null && pd.getPrimaryName().startsWith("DefaultWorkflow-1-")) ||
                                   (pd.getPrimaryName() != null && pd.getPrimaryName().equals(expectedWorkflowName)));
                
                if (resetDefaultWorkflowForFacet(facet)) {
                    if (hasDefault) {
                        reset++;
                    } else {
                        created++;
                    }
                } else {
                    errors.add("Facet " + facet + ": Failed to reset/create");
                }
            } catch (Exception e) {
                logger.error("Error processing facet: " + facet, e);
                errors.add("Facet " + facet + ": " + e.getMessage());
            }
        }
        
        logger.info("Default workflows reset completed. Created: {}, Reset: {}, Errors: {}", 
                    created, reset, errors.size());
        
        return new ResetResult(created, reset, errors);
    }
    
    /**
     * Result class for reset operation
     */
    public static class ResetResult {
        private final int created;
        private final int reset;
        private final List<String> errors;
        
        public ResetResult(int created, int reset, List<String> errors) {
            this.created = created;
            this.reset = reset;
            this.errors = errors;
        }
        
        public int getCreated() {
            return created;
        }
        
        public int getReset() {
            return reset;
        }
        
        public List<String> getErrors() {
            return errors;
        }
    }
}

