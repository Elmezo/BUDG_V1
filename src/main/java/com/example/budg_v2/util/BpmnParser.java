package com.example.budg_v2.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utility class for parsing BPMN XML
 */
public class BpmnParser {

    private static final Logger logger = LoggerFactory.getLogger(BpmnParser.class);

    /**
     * Parse BPMN XML string to DOM Document
     */
    public static Document parseXml(String xmlContent) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        InputSource is = new InputSource(new StringReader(xmlContent));
        return builder.parse(is);
    }

    /**
     * Find start event in BPMN
     */
    public static Element findStartEvent(Document doc) {
        NodeList startEvents = doc.getElementsByTagNameNS("*", "startEvent");
        if (startEvents.getLength() > 0) {
            return (Element) startEvents.item(0);
        }
        return null;
    }

    /**
     * Find all user tasks in BPMN
     */
    public static List<Element> findUserTasks(Document doc) {
        List<Element> tasks = new ArrayList<>();
        // Find user tasks
        NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
        for (int i = 0; i < userTasks.getLength(); i++) {
            tasks.add((Element) userTasks.item(i));
        }

        // Find generic tasks (some diagrams use 'task' instead of 'userTask')
        NodeList genericTasks = doc.getElementsByTagNameNS("*", "task");
        for (int i = 0; i < genericTasks.getLength(); i++) {
            tasks.add((Element) genericTasks.item(i));
        }

        return tasks;
    }

    /**
     * Find element by ID
     */
    public static Element findElementById(Document doc, String elementId) {
        NodeList allElements = doc.getElementsByTagName("*");
        for (int i = 0; i < allElements.getLength(); i++) {
            Element element = (Element) allElements.item(i);
            if (elementId.equals(element.getAttribute("id"))) {
                return element;
            }
        }
        return null;
    }

    /**
     * Get outgoing sequence flows from an element
     */
    public static List<String> getOutgoingFlows(Element element) {
        List<String> flows = new ArrayList<>();
        // 1. Try child elements (standard BPMN but sometimes omitted by exporters)
        NodeList outgoing = element.getElementsByTagNameNS("*", "outgoing");
        for (int i = 0; i < outgoing.getLength(); i++) {
            flows.add(outgoing.item(i).getTextContent().trim());
        }

        // 2. Fallback: Search for sequenceFlow elements by sourceRef in the document
        // This handles cases where elements are self-closing (e.g., startEvent) and
        // don't include <outgoing> children
        if (flows.isEmpty()) {
            String elementId = element.getAttribute("id");
            if (elementId != null && !elementId.isEmpty()) {
                Document doc = element.getOwnerDocument();
                // Find all sequence flows in the document (or parent process)
                // Scoping to document is safer/easier here given we have the element
                NodeList seqFlows = doc.getElementsByTagNameNS("*", "sequenceFlow");
                for (int i = 0; i < seqFlows.getLength(); i++) {
                    Element flow = (Element) seqFlows.item(i);
                    String sourceRef = flow.getAttribute("sourceRef");
                    if (elementId.equals(sourceRef)) {
                        String flowId = flow.getAttribute("id");
                        if (flowId != null && !flowId.isEmpty()) {
                            flows.add(flowId);
                        }
                    }
                }
            }
        }
        return flows;
    }

    /**
     * Find target element of a sequence flow
     */
    public static String getSequenceFlowTarget(Document doc, String flowId) {
        Element flow = findElementById(doc, flowId);
        if (flow != null) {
            return flow.getAttribute("targetRef");
        }
        return null;
    }

    /**
     * Get name attribute from a sequence flow element
     * 
     * @param flow Sequence flow element
     * @return Flow name, or null if not present
     */
    public static String getSequenceFlowName(Element flow) {
        if (flow == null) {
            return null;
        }
        String name = flow.getAttribute("name");
        if (name != null && !name.trim().isEmpty()) {
            return name.trim();
        }
        return null;
    }

    /**
     * Get condition expression from sequence flow
     */
    public static String getFlowCondition(Element flow) {
        NodeList conditions = flow.getElementsByTagNameNS("*", "conditionExpression");
        if (conditions.getLength() > 0) {
            return conditions.item(0).getTextContent().trim();
        }
        return null;
    }

    /**
     * Extract custom properties from user task (Camunda extensions)
     */
    public static Map<String, String> extractTaskProperties(Element userTask) {
        Map<String, String> properties = new HashMap<>();

        // Get task name
        properties.put("name", userTask.getAttribute("name"));

        // Try to find Camunda extension elements
        NodeList extensionElements = userTask.getElementsByTagNameNS("*", "extensionElements");
        if (extensionElements.getLength() > 0) {
            Element extensions = (Element) extensionElements.item(0);

            // Look for properties
            NodeList props = extensions.getElementsByTagNameNS("*", "property");
            for (int i = 0; i < props.getLength(); i++) {
                Element prop = (Element) props.item(i);
                String name = prop.getAttribute("name");
                String value = prop.getAttribute("value");
                if (name != null && !name.isEmpty()) {
                    properties.put(name, value);
                }
            }
        }

        // Check for Camunda attributes directly
        if (userTask.hasAttribute("camunda:dueDate")) {
            properties.put("dueDate", userTask.getAttribute("camunda:dueDate"));
        }
        if (userTask.hasAttribute("camunda:assignee")) {
            properties.put("assignee", userTask.getAttribute("camunda:assignee"));
        }
        if (userTask.hasAttribute("camunda:candidateGroups")) {
            properties.put("role", userTask.getAttribute("camunda:candidateGroups"));
        }
        if (userTask.hasAttribute("camunda:unlockObject")) {
            properties.put("unlockObject", userTask.getAttribute("camunda:unlockObject"));
        }

        return properties;
    }

    /**
     * Check if element is an end event
     */
    public static boolean isEndEvent(Element element) {
        return "endEvent".equals(element.getLocalName());
    }

    /**
     * Extract custom properties from end event (Status and Lifecycle)
     * Similar to extractTaskProperties but for end events
     */
    public static Map<String, String> extractEndEventProperties(Element endEvent) {
        Map<String, String> properties = new HashMap<>();

        // Get event name
        properties.put("name", endEvent.getAttribute("name"));

        // Try to find Camunda extension elements
        NodeList extensionElements = endEvent.getElementsByTagNameNS("*", "extensionElements");
        if (extensionElements.getLength() > 0) {
            Element extensions = (Element) extensionElements.item(0);

            // Look for properties
            NodeList props = extensions.getElementsByTagNameNS("*", "property");
            for (int i = 0; i < props.getLength(); i++) {
                Element prop = (Element) props.item(i);
                String name = prop.getAttribute("name");
                String value = prop.getAttribute("value");
                if (name != null && !name.isEmpty()) {
                    properties.put(name, value);
                }
            }
        }

        // Check for Camunda attributes directly
        if (endEvent.hasAttribute("camunda:status")) {
            properties.put("status", endEvent.getAttribute("camunda:status"));
        }
        if (endEvent.hasAttribute("camunda:lifecycle")) {
            properties.put("lifecycle", endEvent.getAttribute("camunda:lifecycle"));
        }

        return properties;
    }

    /**
     * Check if element is a user task
     */
    public static boolean isUserTask(Element element) {
        String name = element.getLocalName();
        return "userTask".equals(name) || "task".equals(name);
    }

    /**
     * Check if element is an exclusive gateway
     */
    public static boolean isExclusiveGateway(Element element) {
        return "exclusiveGateway".equals(element.getLocalName());
    }

    /**
     * Check if element is a parallel gateway
     */
    public static boolean isParallelGateway(Element element) {
        return "parallelGateway".equals(element.getLocalName());
    }

    /**
     * Check if element is any type of gateway (exclusive or parallel)
     */
    public static boolean isGateway(Element element) {
        return isExclusiveGateway(element) || isParallelGateway(element);
    }

    /**
     * Find all parallel gateway elements in BPMN document
     * 
     * @param doc BPMN document
     * @return List of parallel gateway elements
     */
    public static List<Element> findParallelGateways(Document doc) {
        List<Element> gateways = new ArrayList<>();
        NodeList parallelGateways = doc.getElementsByTagNameNS("*", "parallelGateway");
        for (int i = 0; i < parallelGateways.getLength(); i++) {
            gateways.add((Element) parallelGateways.item(i));
        }
        return gateways;
    }

    /**
     * Get all task nodes reachable from a gateway's outgoing flows
     * This method follows sequence flows from the gateway to find target task nodes
     * 
     * @param doc     BPMN document
     * @param gateway Gateway element
     * @return List of task node IDs reachable from the gateway
     */
    public static List<String> getOutgoingTaskNodes(Document doc, Element gateway) {
        List<String> taskNodeIds = new ArrayList<>();
        List<String> outgoingFlows = getOutgoingFlows(gateway);

        for (String flowId : outgoingFlows) {
            String targetId = getSequenceFlowTarget(doc, flowId);
            if (targetId != null) {
                Element targetElement = findElementById(doc, targetId);
                if (targetElement != null) {
                    // If target is a task, add it
                    if (isUserTask(targetElement)) {
                        taskNodeIds.add(targetId);
                    }
                    // If target is another gateway, recursively find tasks
                    else if (isGateway(targetElement)) {
                        taskNodeIds.addAll(getOutgoingTaskNodes(doc, targetElement));
                    }
                }
            }
        }

        return taskNodeIds;
    }

    /**
     * Find all lanes in BPMN
     */
    public static List<Element> findLanes(Document doc) {
        List<Element> lanes = new ArrayList<>();
        NodeList laneNodes = doc.getElementsByTagNameNS("*", "lane");
        for (int i = 0; i < laneNodes.getLength(); i++) {
            lanes.add((Element) laneNodes.item(i));
        }
        return lanes;
    }

    /**
     * Get name of the lane (role)
     * Checks 'camunda:roleName', 'camunda_1:roleName', and 'name' attributes
     */
    public static String getLaneName(Element lane) {
        if (lane.hasAttribute("camunda:roleName")) {
            return lane.getAttribute("camunda:roleName");
        }
        if (lane.hasAttribute("camunda_1:roleName")) {
            return lane.getAttribute("camunda_1:roleName");
        }
        // Fallback to standard name attribute
        return lane.getAttribute("name");
    }

    /**
     * Get IDs of all flow nodes (tasks, events) in a lane
     */
    public static List<String> getFlowNodesInLane(Element lane) {
        List<String> nodeIds = new ArrayList<>();
        NodeList flowNodeRefs = lane.getElementsByTagNameNS("*", "flowNodeRef");
        for (int i = 0; i < flowNodeRefs.getLength(); i++) {
            nodeIds.add(flowNodeRefs.item(i).getTextContent().trim());
        }
        return nodeIds;
    }

    /**
     * Evaluate simple condition expression
     * Supports: ${variable == 'value'} or ${variable != 'value'}
     */
    public static boolean evaluateCondition(String condition, Map<String, String> variables) {
        if (condition == null || condition.isEmpty()) {
            return true; // Default flow
        }

        // Remove ${ and }
        condition = condition.trim();
        if (condition.startsWith("${") && condition.endsWith("}")) {
            condition = condition.substring(2, condition.length() - 1).trim();
        }

        // Parse simple equality: variable == 'value'
        if (condition.contains("==")) {
            String[] parts = condition.split("==");
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String expectedValue = parts[1].trim().replace("'", "").replace("\"", "");
                String actualValue = variables.get(varName);
                return expectedValue != null && expectedValue.equals(actualValue);
            }
        }

        // Parse simple inequality: variable != 'value'
        if (condition.contains("!=")) {
            String[] parts = condition.split("!=");
            if (parts.length == 2) {
                String varName = parts[0].trim();
                String expectedValue = parts[1].trim().replace("'", "").replace("\"", "");
                String actualValue = variables.get(varName);
                return actualValue != null && !expectedValue.equals(actualValue);
            }
        }

        logger.warn("Could not evaluate condition: {}", condition);
        return false;
    }

    /**
     * Get role for a specific task by finding the lane that contains it
     * 
     * @param doc    BPMN document
     * @param taskId ID of the task element
     * @return Role name from lane, or null if not found
     */
    public static String getRoleForTask(Document doc, String taskId) {
        // Find all lanes
        List<Element> lanes = findLanes(doc);

        logger.info("Checking role for task: {}. Found {} lanes.", taskId, lanes.size());

        // Check each lane to see if it contains this task
        for (Element lane : lanes) {
            List<String> nodeIds = getFlowNodesInLane(lane);
            String laneName = getLaneName(lane);

            logger.debug("Lane '{}' contains nodes: {}", laneName, nodeIds);

            if (nodeIds.contains(taskId)) {
                // Found the lane containing this task
                if (laneName != null && !laneName.trim().isEmpty()) {
                    String normalized = normalizeRoleName(laneName);
                    //system.out.println(
                     //       "DEBUG CHECK: Task " + taskId + " found in lane '" + laneName + "' -> role: " + normalized);
                    logger.info("Task {} found in lane '{}' -> role: {}", taskId, laneName, normalized);
                    return normalized;
                }
            }
        }

        // Heuristic: If there is exactly one lane, assume the task belongs to it
        // This handles cases where the modeler might not correctly link the task to the
        // lane in flowNodeRef
        if (lanes.size() == 1) {
            String laneName = getLaneName(lanes.get(0));
            if (laneName != null && !laneName.trim().isEmpty()) {
                String normalized = normalizeRoleName(laneName);
                logger.warn("Task {} not explicitly linked to lane, but single lane '{}' found. Assuming role: {}",
                        taskId, laneName, normalized);
                return normalized;
            }
        }

        logger.warn("No role found for task: {}", taskId);
        return null;
    }

    /**
     * Normalize role name: trim → uppercase → replace spaces with underscores
     * Example: "Dataset Owner" → "DATASET_OWNER"
     * 
     * @param roleName Original role name
     * @return Normalized role name
     */
    public static String normalizeRoleName(String roleName) {
        if (roleName == null) {
            return null;
        }
        return roleName.trim()
                .toUpperCase()
                .replaceAll("\\s+", "_");
    }

    /**
     * Get task due days from BPMN element
     * Priority: 1) camunda:properties -> dueDays, 2) camunda:dueDate attribute, 3)
     * default
     * 
     * @param taskElement User task element
     * @return Number of days, or null if not found (will use default)
     */
    public static Integer getTaskDueDays(Element taskElement) {
        if (taskElement == null) {
            return null;
        }

        // Priority 1: Check camunda:properties -> dueDays property
        NodeList extensionElements = taskElement.getElementsByTagNameNS("*", "extensionElements");
        if (extensionElements.getLength() > 0) {
            Element extensions = (Element) extensionElements.item(0);
            NodeList props = extensions.getElementsByTagNameNS("*", "property");
            for (int i = 0; i < props.getLength(); i++) {
                Element prop = (Element) props.item(i);
                String name = prop.getAttribute("name");
                String value = prop.getAttribute("value");
                if ("dueDays".equals(name) && value != null && !value.trim().isEmpty()) {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid dueDays value in BPMN: {}", value);
                    }
                }
            }
        }

        // Priority 2: Check camunda:dueDate or camunda_1:dueDate attribute
        if (taskElement.hasAttribute("camunda:dueDate")) {
            String dueDateValue = taskElement.getAttribute("camunda:dueDate");
            try {
                return Integer.parseInt(dueDateValue.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid camunda:dueDate value in BPMN: {}", dueDateValue);
            }
        }
        if (taskElement.hasAttribute("camunda_1:dueDate")) {
            String dueDateValue = taskElement.getAttribute("camunda_1:dueDate");
            try {
                return Integer.parseInt(dueDateValue.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid camunda_1:dueDate value in BPMN: {}", dueDateValue);
            }
        }

        // No SLA definition found - will use default
        logger.debug("No dueDays found for task: {}, will use default", taskElement.getAttribute("id"));
        return null;
    }

    /**
     * Get task escalation days from BPMN element
     * Only checks camunda:properties -> escalationDays (no attribute fallback)
     * 
     * @param taskElement User task element
     * @return Number of days after due date for escalation, or null if not found
     */
    public static Integer getTaskEscalationDays(Element taskElement) {
        if (taskElement == null) {
            return null;
        }

        // Only check camunda:properties -> escalationDays
        NodeList extensionElements = taskElement.getElementsByTagNameNS("*", "extensionElements");
        if (extensionElements.getLength() > 0) {
            Element extensions = (Element) extensionElements.item(0);
            NodeList props = extensions.getElementsByTagNameNS("*", "property");
            for (int i = 0; i < props.getLength(); i++) {
                Element prop = (Element) props.item(i);
                String name = prop.getAttribute("name");
                String value = prop.getAttribute("value");
                if ("escalationDays".equals(name) && value != null && !value.trim().isEmpty()) {
                    try {
                        return Integer.parseInt(value.trim());
                    } catch (NumberFormatException e) {
                        logger.warn("Invalid escalationDays value in BPMN: {}", value);
                    }
                }
            }
        }

        return null; // No escalation defined
    }
}
