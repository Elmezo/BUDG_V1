# Default Workflow Management System - Quick Start

## 🚀 Quick Setup (5 Minutes)

### 1. Database Setup
```bash
mysql -u root -p project < database/workflow_schema.sql
```

### 2. Verify Installation
Navigate to: **Admin Panel → Operating Model → Default Workflows**

### 3. Create Your First Workflow

1. **Select Facet** (e.g., "People", "Policy", etc.)
2. **Fill Form**:
   - Name: "Approval Workflow" (min 6 chars)
   - Description: "Standard approval process" (min 6 chars)
   - Type: Select from dropdown
   - Active: ✓
3. **Upload BPMN**: Use `data/bpmn/sample_workflow.bpmn` for testing
4. **Click "Save Workflow"**

---

## 📋 What You Get

### Backend (Java)
- ✅ 7 REST API endpoints
- ✅ Complete workflow runtime engine
- ✅ BPMN XML parsing
- ✅ File + database storage
- ✅ Type 2 special handling (creates 2 mapping rows)

### Frontend (JavaScript)
- ✅ Workflow management form
- ✅ BPMN upload/download
- ✅ Workflows list table
- ✅ Real-time validation

### Database
- ✅ 7 tables for workflow management
- ✅ Sample CR types pre-populated

---

## 🔗 API Endpoints

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET/POST/PUT/DELETE | `/api/process_definitions` | Workflow CRUD |
| GET/POST | `/api/process_definitions/{id}/bpmn` | BPMN file ops |
| POST | `/api/workflow_types` | Type mapping |
| GET | `/api/changerequest_types` | List types |
| POST | `/api/workflow_instances` | Start workflow |
| POST | `/api/workflow_instances/{id}/tasks/{taskId}/complete` | Complete task |

---

## 🎯 Type 2 Behavior

When you select **"Type 2"** and save:
- ✅ Creates **2 rows** in `changerequest_workflow_crtype`
- ✅ One for "Type 1"
- ✅ One for "Type 2"

Verify:
```sql
SELECT * FROM changerequest_workflow_crtype WHERE Process_Definition_ID = 1;
```

---

## 📖 Full Documentation

- **Setup Guide**: `WORKFLOW_SETUP.md`
- **Walkthrough**: See artifacts folder
- **Sample BPMN**: `data/bpmn/sample_workflow.bpmn`

---

## ⚡ Next Steps

1. **Test the UI** - Create a workflow via admin panel
2. **Upload BPMN** - Use the sample file
3. **Integrate with CR** - Connect to change request module
4. **Add bpmn-js** - For visual workflow designer (optional)

---

## 🆘 Troubleshooting

**Can't save workflow?**
- Check name is >= 6 chars and unique
- Ensure facet is selected

**BPMN upload fails?**
- Save workflow first
- Check file is valid XML

**Type 2 not creating 2 rows?**
- Verify you selected exactly "Type 2" from dropdown
- Check database after save

---

## ✨ Features

- ✅ Sequential workflows
- ✅ Exclusive gateways
- ✅ User tasks with due dates
- ✅ Condition evaluation
- ✅ Dual storage (DB + file)
- ✅ Type mapping
- ✅ Workflow runtime engine

**Ready to use!** 🎉
