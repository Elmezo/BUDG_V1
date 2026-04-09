# BUDG Technical Architecture

## A Modern Approach to Data Governance

---

## Executive Summary

BUDG is a data governance platform designed as a practical, maintainable alternative to enterprise solutions like Informatica Axon. This document outlines the architectural decisions, technology choices, and implementation strategies that differentiate BUDG from traditional enterprise governance tools.

The platform prioritizes clarity, extensibility, and operational simplicity without sacrificing the depth required for enterprise-grade data governance.

---

## 1. Platform Philosophy

### Governance by Adoption, Not Enforcement

Traditional governance platforms enforce compliance through rigid rules and mandatory workflows. This approach creates friction, slows adoption, and often results in shadow governance practices where users work around the system rather than with it.

BUDG takes a different approach:

- **Gradual Adoption**: Users can start with basic cataloging and progressively adopt more sophisticated governance features
- **Flexible Workflows**: Governance processes adapt to organizational needs rather than forcing organizations to adapt to the tool
- **Low Barrier to Entry**: Minimal configuration required to begin capturing governance metadata

### Simplicity Over Feature Overload

Enterprise governance platforms often suffer from feature bloat, where decades of accumulated functionality create complexity that obscures core value. BUDG follows a different principle:

| Principle | Implementation |
|-----------|----------------|
| **Do one thing well** | Each component has a single, clear responsibility |
| **Explicit over implicit** | Configuration is visible and understandable |
| **Predictable behavior** | Actions produce consistent, expected results |
| **Minimal dependencies** | Components can be understood and modified independently |

This philosophy extends to every architectural decision, from the choice of servlet-based architecture to the configuration-driven workflow engine.

---

## 2. Backend Architecture

### Technology Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Runtime | Java | 17 LTS |
| Servlet API | Jakarta Servlet | 6.0 |
| Application Server | Apache Tomcat | 11.x |
| Database | MySQL | 8.x |
| Search Engine | Elasticsearch | 8.14 |
| Build Tool | Apache Maven | 3.x |

### Layered Architecture

BUDG implements a clean three-tier architecture with explicit separation of concerns:

```
┌─────────────────────────────────────────────────────────────┐
│                     API Layer (Servlets)                    │
│  DatasetServlet, GlossaryServlet, AttributeServlet, ...    │
├─────────────────────────────────────────────────────────────┤
│                   Service Layer (Business Logic)            │
│  DatasetService, WorkflowService, SearchService, ...        │
├─────────────────────────────────────────────────────────────┤
│                   Data Access Layer (DAO/SQL)               │
│  WorkflowInstanceDAO, WorkflowTaskDAO, DatabaseConnection   │
└─────────────────────────────────────────────────────────────┘
```

#### API Layer (Servlets)

The servlet layer handles HTTP request/response processing, input validation, and response formatting. Each servlet is responsible for a specific entity type or functional area:

- **Entity Servlets**: `DatasetServlet`, `GlossaryServlet`, `AttributeServlet`, `SystemServlet`
- **Relationship Servlets**: `AttributeRelationshipServlet`, `DatasetImpactServlet`
- **Workflow Servlets**: `WorkflowInstanceServlet`, `WorkflowTaskServlet`
- **Administrative Servlets**: `AdminFacetsServlet`, `AppConfigServlet`

#### Service Layer

Business logic is encapsulated in service classes that are independent of HTTP processing:

- **Entity Services**: `DatasetService`, `GlossaryService`, `PolicyService`
- **Cross-cutting Services**: `SearchService`, `EmailService`, `NotificationService`
- **Infrastructure Services**: `LdapAuthService`, `LdapSyncService`, `DocumentStorageService`

#### Data Access Layer

Direct SQL access with prepared statements provides:

- Clear visibility into database operations
- Fine-grained control over query optimization
- No ORM abstraction overhead
- Explicit transaction management

### Stateless Request Handling

All HTTP requests are processed statelessly:

- No server-side session state
- JWT tokens carry authentication context
- Each request contains all information needed for processing
- Horizontal scaling requires no session replication

### Comparison with Axon

| Aspect | BUDG | Axon |
|--------|------|------|
| **Architecture** | Clean servlet-based with explicit layers | Tightly coupled enterprise services |
| **Configuration** | Database-driven, modifiable at runtime | XML/property files, requires restart |
| **Dependencies** | Minimal, well-defined | Complex dependency graph |
| **Debugging** | Straightforward stack traces | Layered enterprise abstractions |
| **Onboarding** | Standard Java servlet knowledge | Proprietary framework expertise |

---

## 3. Search and Relationship Engine

### Graph-Based Traversal

BUDG implements a graph traversal engine that discovers relationships across governance entities. The `GraphTraversalService` uses breadth-first search (BFS) to explore connections:

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Dataset   │───▶│  Glossary   │───▶│  Policy     │
└─────────────┘    └─────────────┘    └─────────────┘
       │                  │                  │
       ▼                  ▼                  ▼
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│  Attribute  │    │   System    │    │ Regulation  │
└─────────────┘    └─────────────┘    └─────────────┘
```

#### Key Features

- **Depth Control**: Configurable maximum traversal depth prevents infinite expansion and ensures predictable performance
- **Facet-Level Filtering**: Post-traversal filtering allows search constraints to be applied after relationship discovery
- **Segment Access Enforcement**: Security boundaries are respected during traversal
- **Correlation ID Tracking**: Every traversal operation is traceable for debugging and audit

#### Performance Optimizations

- **Seed Set Limiting**: Initial seed sets are capped at 1,000 entries to prevent performance degradation
- **Visited Set Tracking**: Prevents cycles and redundant processing
- **Batch Relationship Queries**: Related IDs are fetched in batches rather than individual queries

### Elasticsearch Integration

Full-text search is powered by Elasticsearch 8.14 with the following configuration:

- **Index per Entity Type**: Separate indices for datasets, glossaries, people, systems, and other entity types
- **Fuzzy Search Support**: Configurable through `app_config` table
- **Bulk Synchronization**: Database-to-index sync handles large volumes efficiently
- **Dynamic Index Creation**: New entity types can be indexed without code changes

### Context-Aware Search

The Unison Search system provides cross-facet search with context awareness:

- **Multi-facet Results**: Single search query returns results across all governance objects
- **Relevance Ranking**: Results ordered by match quality and relationship proximity
- **Filter Propagation**: Filters applied to one facet affect related facets through graph traversal

### Comparison with Axon Search

| Aspect | BUDG | Axon |
|--------|------|------|
| **Search Engine** | Elasticsearch (configurable) | EDC-dependent search |
| **Relationship Discovery** | BFS graph traversal | Pre-computed relationships |
| **Extensibility** | Add facets via configuration | Schema changes require development |
| **Performance Tuning** | Depth/seed limits configurable | Limited tuning options |

---

## 4. Configuration-Driven Logic

### Philosophy

Hard-coded business rules create maintenance burden and deployment friction. BUDG externalizes governance logic to configuration wherever possible.

### Feature Flags

Runtime feature toggles enable gradual rollout and A/B testing:

```sql
-- Example: Enable V2 workflow engine
INSERT INTO app_config (config_key, config_value) 
VALUES ('workflow.engine.version', 'v2');
```

Feature flags control:

- Search algorithm selection (fuzzy vs. exact)
- Workflow engine version (V1/V2)
- UI feature visibility
- Integration endpoints

### Dynamic Filters and Facets

Facets and their associated filters are defined in the database:

- **Facet Registry**: `unison_search_config` table defines searchable entity types
- **Filter Definitions**: Per-facet filters are configured without code changes
- **Field Mappings**: Search and return fields configured in the database

### Workflow Configuration

Workflow definitions use BPMN-compatible XML:

- **Lifecycle Stages**: Configurable status progression
- **Role Assignments**: Dynamic resolver for workflow participants
- **Notification Rules**: Configurable triggers and templates
- **SLA Configuration**: Deadline tracking with configurable thresholds

### Benefits Over Static Configuration

| Aspect | Configuration-Driven | Hard-Coded |
|--------|---------------------|------------|
| **Change Velocity** | Minutes (database update) | Days (code change, test, deploy) |
| **Risk** | Low (isolatable) | High (affects entire application) |
| **Visibility** | Queryable via SQL | Requires code review |
| **Environment Variation** | Supported naturally | Requires branching/profiles |

---

## 5. Workflow and Authorization Model

### Workflow Architecture

BUDG implements a flexible workflow engine with the following components:

```
┌───────────────────────────────────────────────────────────────┐
│                    Workflow Component Overview                 │
├─────────────────────┬─────────────────────────────────────────┤
│ WorkflowEngine      │ Central BPMN processing engine          │
│ WorkflowInstance    │ Runtime state of a workflow execution   │
│ WorkflowTask        │ Individual task within a workflow       │
│ WorkflowMapping     │ Associates workflows with entity types  │
│ WorkflowNotification│ Event-driven notification dispatch      │
└─────────────────────┴─────────────────────────────────────────┘
```

### Facet-Level Workflows

Unlike platforms that apply workflows uniformly, BUDG supports facet-specific workflow configuration:

- **Dataset Workflows**: Multi-stage approval with data steward involvement
- **Glossary Workflows**: Definition review and semantic validation
- **Policy Workflows**: Legal review and compliance sign-off
- **Change Request Workflows**: Impact assessment and stakeholder approval

### Dynamic Role Resolution

Role assignment is computed at runtime rather than statically assigned:

```java
// WorkflowAuthorizationUtil resolves roles dynamically
public static boolean isAuthorized(String userId, WorkflowTask task) {
    // Resolve current role based on:
    // - User's organizational unit
    // - Task's facet type
    // - Workflow configuration
    // - Segment access rules
}
```

Supported roles include:

- **Admin**: Full system access
- **Super Admin**: Administrative functions plus user management
- **Data Steward**: Entity-level governance authority
- **Requestor**: Workflow initiator
- **Approver**: Stage-specific approval authority

### Workflow Authorization Decoupling

Authorization logic is separated from UI rendering:

- Backend enforces authorization rules regardless of UI state
- API endpoints validate permissions independently
- UI reflects but does not control access rights

### Comparison with Axon Workflows

| Aspect | BUDG | Axon |
|--------|------|------|
| **Customization** | BPMN-based, database-configurable | Requires professional services |
| **Role Model** | Dynamic resolution | Static assignment |
| **Facet Support** | Per-facet workflow configuration | Uniform workflow application |
| **Notifications** | Rule-based, configurable | Fixed notification patterns |

---

## 6. Security and Enterprise Readiness

### Authentication Architecture

BUDG supports multiple authentication mechanisms:

#### LDAP Integration

Full enterprise LDAP support via UnboundID LDAP SDK:

- **Bind Authentication**: Supports simple bind and search-then-bind
- **Group Membership**: Role derivation from LDAP group membership
- **SSL/TLS Support**: LDAPS and StartTLS for secure communication
- **Truststore Configuration**: Custom CA certificate support
- **LDAP Injection Prevention**: Input sanitization on all filter values

#### JWT Token Authentication

Stateless authentication using JSON Web Tokens:

- **Access Tokens**: Short-lived tokens for API access
- **Refresh Tokens**: Long-lived tokens for session renewal
- **Configurable Expiration**: Token lifetimes set via `system_settings`
- **Secure Storage**: Tokens signed with configurable secret keys

### Role-Based Access Control

RBAC is enforced at multiple levels:

```
┌─────────────────────────────────────────────────────────┐
│                   Access Control Layers                  │
├─────────────────────────────────────────────────────────┤
│ 1. Authentication Filter (AuthFilter)                   │
│    - Validates JWT token                                │
│    - Extracts user context                              │
├─────────────────────────────────────────────────────────┤
│ 2. Permission Service                                   │
│    - Checks role-based permissions                      │
│    - Validates segment access                           │
├─────────────────────────────────────────────────────────┤
│ 3. Entity-Level Authorization                           │
│    - Ownership validation                               │
│    - Stakeholder verification                           │
└─────────────────────────────────────────────────────────┘
```

### Dual Enforcement

Security is enforced on both frontend and backend:

- **Frontend**: UI elements hidden/disabled based on permissions
- **Backend**: API endpoints independently validate authorization
- **Defense in Depth**: Frontend restrictions are convenience, not security

### Audit and Logging

Comprehensive logging infrastructure:

- **Structured Logging**: JSON-formatted logs via Logstash Logback Encoder
- **Correlation IDs**: Request tracing across service boundaries
- **Activity Logging**: User actions recorded in `activity_log` table
- **Configurable Verbosity**: Log levels adjustable without restart

### Comparison with Axon Security

| Aspect | BUDG | Axon |
|--------|------|------|
| **Authentication** | LDAP + Local DB + JWT | LDAP (through EDC) |
| **Authorization** | Transparent RBAC | Opaque permission model |
| **Audit Trail** | Queryable database + logs | Limited visibility |
| **Customization** | Role mappings configurable | Requires vendor support |

---

## 7. Integration Strategy

### API-First Design

Every BUDG capability is accessible via REST API:

- **Consistent Interface**: All endpoints follow the same patterns
- **JSON Request/Response**: Standard data format throughout
- **RESTful Semantics**: HTTP methods reflect operation intent
- **Versioning Ready**: API structure supports future versioning

### Loose Coupling

External integrations are designed for minimal dependency:

```
┌─────────────────────────────────────────────────────────────┐
│                    Integration Points                        │
├─────────────────┬───────────────────────────────────────────┤
│ Catalogs        │ Import/export via API, no direct coupling │
│ Lineage Tools   │ Dataset relationship ingestion            │
│ BI Platforms    │ Glossary term synchronization             │
│ ITSM Systems    │ Change request integration                │
│ Email Servers   │ SMTP configuration for notifications      │
└─────────────────┴───────────────────────────────────────────┘
```

### No Forced Dependencies

Unlike Axon, which requires Informatica EDC for full functionality, BUDG operates independently:

- Search works without external catalog
- Workflows operate without enterprise service bus
- Reporting generates without BI tool integration
- Authentication works without identity federation

### Import/Export Capabilities

Bulk data operations support governance at scale:

- **Excel Export**: Apache POI generates formatted reports
- **PDF Generation**: iText produces governance documentation
- **Bulk Upload**: CSV/Excel import for mass data entry
- **API Export**: JSON export for system integration

---

## 8. Frontend Architecture

### Technology Choices

| Layer | Technology | Rationale |
|-------|------------|-----------|
| Structure | HTML5 | Universal browser support |
| Styling | CSS (with Tailwind option) | Flexibility and performance |
| Logic | Vanilla JavaScript | No framework lock-in |
| Build | Vite | Fast development experience |

### Search-Driven UX

The frontend is built around search as the primary navigation mechanism:

- **Unified Search Bar**: Single entry point for all governance objects
- **Faceted Results**: Results grouped by entity type
- **Progressive Disclosure**: Detail revealed on demand
- **Keyboard Navigation**: Power-user efficiency

### Lightweight Architecture Benefits

| Benefit | Impact |
|---------|--------|
| **Fast Load Times** | Minimal JavaScript bundle size |
| **Easy Modification** | No framework-specific patterns to learn |
| **Broad Compatibility** | Works on older browsers and devices |
| **Independent Deployment** | Frontend can update without backend changes |

### Internationalization

Built-in support for multiple languages:

- **Message Bundles**: `en.json`, `ar.json` for translations
- **RTL Support**: Arabic language with proper text direction
- **Dynamic Switching**: Language change without page reload

---

## 9. Cost, Control, and Ownership

### Lower Operational Cost

| Cost Factor | BUDG | Enterprise Alternatives |
|-------------|------|------------------------|
| **Licensing** | Open/internal | Per-user or per-core |
| **Infrastructure** | Standard JVM + MySQL | Specialized requirements |
| **Operations** | Standard monitoring | Vendor-specific tooling |
| **Training** | Generic Java skills | Proprietary knowledge |

### No Vendor Lock-In

BUDG uses exclusively open standards and technologies:

- **Database**: Standard SQL, portable to other RDBMS
- **Search**: Elasticsearch (replaceable with OpenSearch)
- **Application Server**: Standard servlet container
- **Languages**: Java, JavaScript, HTML, CSS

### Internal Roadmap Control

Development priorities are set internally:

- **Feature Requests**: Addressed based on organizational need
- **Bug Fixes**: Immediate attention for critical issues
- **Customization**: Any aspect modifiable without vendor negotiation
- **Upgrade Schedule**: Determined by operations, not vendor lifecycle

### Scaling with the Organization

BUDG grows with governance maturity:

1. **Initial**: Basic catalog and glossary
2. **Developing**: Add workflows and stakeholder tracking
3. **Maturing**: Implement change management and impact analysis
4. **Advanced**: Full governance with lineage and compliance

---

## 10. BUDG vs. Axon: Technical Comparison

| Dimension | BUDG | Informatica Axon |
|-----------|------|------------------|
| **Architecture** | Clean servlet layers, explicit dependencies | Monolithic enterprise services |
| **Customization** | Database-driven configuration | Professional services engagement |
| **Search** | Elasticsearch with graph traversal | EDC-dependent, static |
| **Workflow Flexibility** | BPMN-based, per-facet configuration | Uniform, limited customization |
| **Integration** | API-first, loose coupling | Tight EDC integration required |
| **Security Model** | Transparent RBAC with dual enforcement | Opaque permission system |
| **Cost of Change** | Configuration update | Development project |
| **Time to Value** |  Days to weeks | Months |
| **Operational Complexity** | Standard JVM monitoring | Specialized tooling |
| **Vendor Dependency** | None | High |

---

## Conclusion

BUDG represents a deliberate alternative to traditional enterprise governance platforms. By choosing proven, maintainable technologies and prioritizing operational simplicity, BUDG delivers governance capabilities without the complexity, cost, and vendor dependency that characterize solutions like Informatica Axon.

The architecture decisions documented here reflect a commitment to:

- **Transparency**: Every component is understandable and modifiable
- **Flexibility**: Configuration-driven behavior adapts to organizational needs
- **Sustainability**: Standard technologies ensure long-term maintainability
- **Independence**: No external vendor controls the platform roadmap

For organizations seeking data governance capabilities without enterprise software overhead, BUDG provides a practical, proven foundation.

---

## Appendix: Technology Summary

| Category | Components |
|----------|------------|
| **Backend** | Java 17, Jakarta Servlets 6.0, Tomcat 11 |
| **Database** | MySQL 8.x with standard SQL |
| **Search** | Elasticsearch 8.14 |
| **Authentication** | JWT (jjwt-api), LDAP (UnboundID SDK) |
| **Logging** | SLF4J, Logback, Logstash Encoder |
| **Reporting** | Apache POI (Excel), iText (PDF) |
| **Email** | Jakarta Mail (Angus Mail) |
| **Frontend** | HTML5, CSS, JavaScript, Vite |
