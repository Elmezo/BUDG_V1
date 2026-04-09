# Default Workflow Management System - Setup Guide

## Overview

This system provides a complete BPMN-based workflow management solution for change requests, including:
- Visual workflow designer (BPMN upload/download)
- Workflow runtime engine
- Task management and progression
- Gateway evaluation (exclusive gateways with conditions)
- Type mapping with special Type 2 handling

## Database Setup

### 1. Run the schema script

```bash
mysql -u root -p project < database/workflow_schema.sql
```

This creates:
- `process_definition` - Workflow metadata
- `changerequest_type` - CR types (pre-populated with Create, Edit, Delete, Archive)
- `changerequest_workflow_crtype` - Workflow-to-type mappings
- `workflow_instance` - Runtime instances
- `workflow_instance_task` - Individual tasks
- `process_definition_bpmn` - BPMN XML storage
- `workflow_instance_variable` - Runtime variables for gateway conditions

### 2. Verify tables

```sql
SHOW TABLES LIKE '%process%';
SHOW TABLES LIKE '%workflow%';
```

## File System Setup

The system stores BPMN files at: `d:\BUDG_V2\data\bpmn\`

This directory will be created automatically on first use, but ensure the application has write permissions.

## Backend Components

### DAO Layer
- `ProcessDefinitionDAO` - Workflow CRUD
- `WorkflowMappingDAO` - Type mappings
- `WorkflowInstanceDAO` - Runtime instances
- `WorkflowTaskDAO` - Task operations
- `BpmnContentDAO` - BPMN XML storage

### Servlets (REST API)
- `GET/POST/PUT/DELETE /api/process_definitions` - Workflow management
- `GET/POST /api/process_definitions/{id}/bpmn` - BPMN file operations
- `GET /api/changerequest_types` - List CR types
- `POST /api/workflow_types` - Create type mapping (handles Type 2 → 2 rows)
- `GET /api/roles?module={id}` - Get roles for lane assignment
- `POST /api/workflow_instances` - Start workflow
- `GET /api/workflow_instances/by-cr/{crId}` - Get workflow status
- `POST /api/workflow_instances/{id}/tasks/{taskId}/complete` - Complete task

### Utilities
- `BpmnFileManager` - File system operations
- `BpmnParser` - XML parsing and element extraction
- `WorkflowEngine` - Runtime execution engine

## Frontend Usage

### Access
Navigate to: **Admin Panel → Operating Model → Default Workflows**

### Creating a Workflow

1. **Select Facet** - Choose the module/entity
2. **Enter Details**:
   - Workflow Name (min 6 chars, unique per facet)
   - Description (min 6 chars)
   - Reference (optional)
   - Change Request Type
   - Active toggle
3. **Upload BPMN** - Upload a .bpmn or .xml file
4. **Save** - Click "Save Workflow"

### Type 2 Special Behavior

When you select "Type 2" from the CR Type dropdown and save:
- The system creates **2 separate rows** in `changerequest_workflow_crtype`
- One row for "Type 1"
- One row for "Type 2"

This allows the workflow to be triggered for both types.

### BPMN Editor Integration (Future Enhancement)

The frontend includes a placeholder for bpmn-js integration. To add visual editing:

1. Install bpmn-js:
```bash
npm install bpmn-js bpmn-js-properties-panel camunda-bpmn-moddle
```

2. Add to your HTML:
```html
<script src="https://unpkg.com/bpmn-js@latest/dist/bpmn-modeler.production.min.js"></script>
```

3. Initialize in `default-workflows.js`:
```javascript
const modeler = new BpmnJS({
    container: '#bpmnContainer',
    keyboard: { bindTo: document }
});
```

## Workflow Runtime

### Starting a Workflow

When a Change Request is created:

```javascript
fetch('/api/workflow_instances', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        processDefinitionId: 1,
        changeRequestId: 123
    })
});
```

This:
1. Creates a `workflow_instance` record
2. Parses the BPMN to find the start event
3. Creates initial `workflow_instance_task` records for the first user tasks

### Completing a Task

```javascript
fetch('/api/workflow_instances/1/tasks/5/complete', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
        action: 'Approved',
        comment: 'Looks good',
        userId: 10
    })
});
```

This:
1. Marks the task as completed
2. Evaluates gateway conditions (if any)
3. Creates next tasks
4. Updates workflow instance status
5. Returns `{ workflowCompleted: true/false, nextTasks: [...] }`

### Gateway Conditions

The engine supports simple condition expressions:

```xml
<sequenceFlow id="flow1" sourceRef="gateway1" targetRef="task2">
  <conditionExpression>${last_approval == 'Approved'}</conditionExpression>
</sequenceFlow>
```

Variables available:
- `last_approval` - The action from the last completed task
- `last_user` - User ID who completed the last task

## API Examples

### Create Workflow
```bash
curl -X POST http://localhost:8080/api/process_definitions \
  -H "Content-Type: application/json" \
  -d '{
    "primaryName": "Approval Workflow",
    "reference": "WF-001",
    "description": "Standard approval process",
    "status": "Enabled",
    "entityId": 1
  }'
```

### Upload BPMN
```bash
curl -X POST http://localhost:8080/api/process_definitions/1/bpmn \
  -H "Content-Type: application/json" \
  -d '{
    "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>..."
  }'
```

### Start Workflow
```bash
curl -X POST http://localhost:8080/api/workflow_instances \
  -H "Content-Type: application/json" \
  -d '{
    "processDefinitionId": 1,
    "changeRequestId": 123
  }'
```

### Complete Task
```bash
curl -X POST http://localhost:8080/api/workflow_instances/1/tasks/5/complete \
  -H "Content-Type: application/json" \
  -d '{
    "action": "Approved",
    "comment": "OK",
    "userId": 10
  }'
```

## Troubleshooting

### BPMN file not found
- Check that `d:\BUDG_V2\data\bpmn\` exists and is writable
- Verify the file was saved (check database `process_definition_bpmn` table)

### Workflow not starting
- Ensure BPMN has a valid start event
- Check that BPMN XML is well-formed
- Look for errors in server logs

### Tasks not progressing
- Verify sequence flows are correctly defined
- Check gateway conditions match expected variable values
- Ensure tasks are marked as "Pending" or "InProgress" before completion

## Limitations

Current version supports:
- ✅ Sequential flows
- ✅ Exclusive gateways with simple conditions
- ✅ User tasks with custom properties
- ✅ Start and end events

Not yet supported:
- ❌ Parallel gateways
- ❌ Event-based gateways
- ❌ Timer events
- ❌ Message events
- ❌ Sub-processes
- ❌ Visual BPMN editor (requires bpmn-js integration)

## Next Steps

1. Integrate bpmn-js for visual workflow design
2. Add parallel gateway support
3. Implement timer events for due dates
4. Add workflow versioning
5. Create workflow analytics dashboard
