# 🔐 AI Security & Permission Audit Report

> **Auditor:** AI Security & Access Control Auditor Agent
> **Date:** 2026-02-25
> **Version:** Final Approved Specification
> **Status:** ✅ Validated Against Finalized Access Control Logic

---

## 📋 Table of Contents

1. [Global Access Rule](#global-access-rule)
2. [Role-Based Access Matrix](#role-based-access-matrix)
   - [SuperAdmin](#1-superadmin)
   - [Admin](#2-admin)
   - [WebUser](#3-webuser)
   - [Guest](#4-guest)
3. [Lock Rules](#lock-rules)
4. [Soft Delete Rules](#soft-delete-rules)
5. [Search Rules](#search-rules)
6. [Map Relationship Rules](#map-relationship-rules)
7. [Impact Tab Rule](#impact-tab-rule)
8. [System Setting Rule](#system-setting-rule)
9. [Security Principles](#security-principles)
10. [Audit Findings](#audit-findings)
    - [Violations](#violations)
    - [Edge Case Risks](#edge-case-risks)
    - [Confirmation Checklist](#confirmation-checklist)
    - [Automated Test Scenarios](#automated-test-scenarios)

---

## 🌐 Global Access Rule

> Object visibility is granted **ONLY** if **one** of the following conditions is true:

```
(User has access to Object's Segment AND access to Object's Facet)
    OR
(User is a direct Stakeholder of the Object)
```

### Universally Visible to All Users:

| Item | Condition |
|------|-----------|
| Objects in Enterprise (Public) Segment | Always visible |
| Change Requests (CRs) in Enterprise (Public) Segment | Always visible |

---

## 👤 Role-Based Access Matrix

---

### 1️⃣ SuperAdmin

**Scope:** Unrestricted — entire system.

#### ✅ CAN SEE:

| Permission | Details |
|---|---|
| All objects in all Facets | No facet restriction |
| All Segments | Including private and restricted segments |
| All Admin Panel sections | Full admin access |
| All Change Requests | Regardless of origin or segment |
| Soft-Deleted objects | Objects with `status = deleted` |
| Locked objects | Includes full lock metadata (reason + timestamp) |
| All relationships | No relationship hiding |
| All dashboards | Own dashboards + all shared dashboards |

#### ❌ CANNOT:

> **None.** SuperAdmin has no restrictions.

#### 🔑 Special Exclusive Rule:

> Only **SuperAdmins** can **permanently delete** objects from the system.

---

### 2️⃣ Admin

**Scope:** Limited to assigned and explicitly granted Segments + Enterprise (Public) Segment.

#### ✅ CAN SEE:

| Permission | Details |
|---|---|
| Objects in assigned segments | Full object visibility within scope |
| Locked objects (assigned segments) | Includes lock reason metadata |
| Deleted objects (assigned segments) | Objects with `status = deleted` |
| Limited Admin Panel sections | Subset of SuperAdmin's admin view |
| Custom Fields | View **and** edit permissions |
| Manage Locks | Only for objects within accessible segments |

#### 👁️ CAN SEE BUT CANNOT EDIT:

| Item | Access Level |
|---|---|
| Default Workflows | Read-only |
| Roles & Responsibilities | Read-only |
| Ownership Transfer | Read-only |
| Locked Users tab | Visible tab, but cannot view users inside |
| Segments list | Visible, but **cannot create** new segments |

#### ❌ CANNOT SEE:

| Restricted Item |
|---|
| Objects in non-assigned segments |
| Role Permissions configuration |
| Dropdown Configurations |
| Locked users in non-assigned segments |
| System statistics |

---

### 3️⃣ WebUser

**Scope:** Permitted segments + direct stakeholder access (with isolation).

#### ✅ CAN SEE:

| Permission | Details |
|---|---|
| Objects in permitted segments | Standard access |
| Objects where they are a Stakeholder | Even if segment is not permitted |
| Own dashboards | Personal dashboards |
| Shared dashboards | Only those explicitly shared with them |
| Own Change Requests | CRs they created |
| CRs where they are a Stakeholder | **ONLY** if they also have access to that CR's segment |

#### 🔒 Stakeholder Access — Isolation Rule:

When access is granted via Stakeholder status only (segment not permitted):

| What Is Visible | What Is Hidden |
|---|---|
| The object itself | Related objects |
| — | Parent objects |
| — | Child objects |
| — | Contextual segment metadata |

#### ❌ CANNOT SEE:

| Restricted Item |
|---|
| Admin Panel (any section) |
| Deleted objects (`status = deleted`) |
| Locked objects (sees only 🔒 icon if **they** locked it) |
| CRs unrelated to them |
| System statistics |

---

### 4️⃣ Guest

**Scope:** Public-facing pages only.

#### ✅ CAN SEE:

| Accessible Item | Condition |
|---|---|
| Login page | Always |
| Public pages | If they exist in the system |
| Search tab | Enterprise segment only + `Viewing = Public` |
| Maps tab | Enterprise segment only |

#### ❌ CANNOT SEE:

| Restricted Item |
|---|
| Dashboards (homepage) |
| Private objects |
| Any restricted segment data |
| Admin Panel |
| User data or metadata |

---

## 🔒 Lock Rules

### Lock Metadata Visibility:

| User Type | Lock Icon | Lock Reason | Lock Timestamp |
|---|---|---|---|
| SuperAdmin | ✅ | ✅ | ✅ |
| Admin (responsible for segment) | ✅ | ✅ | ✅ |
| Object Stakeholder | ✅ | ✅ | ✅ |
| CR Owner | ✅ | ✅ | ✅ |
| All other users | ✅ (icon only) | ❌ | ❌ |

### Manage Locks Tab:

| Role | Scope |
|---|---|
| SuperAdmin | Sees **all** locked objects in the system |
| Admin | Sees locked objects **only** within accessible segments |
| WebUser | No access to Manage Locks tab |
| Guest | No access to Manage Locks tab |

---

## 🗑️ Soft Delete Rules (`status = deleted`)

| Role | Can See Deleted Objects? |
|---|---|
| SuperAdmin | ✅ Yes — across all segments |
| Admin | ✅ Yes — only within responsible segments |
| WebUser | ❌ Never |
| Guest | ❌ Never |

---

## 🔎 Search Rules

### Search Returns:

> All objects the **requesting user has access to** based on their role and segment permissions.

### Locked Object Visibility in Search:

| Role | Locked Objects Appear in Search? |
|---|---|
| SuperAdmin | ✅ Yes — always |
| Admin (assigned segment) | ✅ Yes — within their segments |
| Stakeholder | ✅ Yes — for objects they are stakeholder of |
| WebUser (non-stakeholder) | ❌ No |
| Guest | ❌ No |

---

## 🗺️ Map Relationship Rules

| Access Scenario | Behaviour |
|---|---|
| User has access to **both** objects | Show full relationship |
| User has access to **only one side** | Show `"Hidden Relationship Exists"` + 🔒 icon on inaccessible object |
| User has access to **neither** object | Show nothing |

---

## 📌 Impact Tab Rule

**Rule for Private Segment Objects:**

If **Object A** is in a Private Segment, it can only link to:
- Objects in the **Enterprise (Public)** segment, OR
- Objects **inside the same private segment**

**Cascading Visibility Rule:**

> Users who have access to Object A **must automatically see** linked objects — provided those links comply with the above rule.

---

## ⚙️ System Setting Rule

**Location:** `Admin Panel → Customize & Configure → Search Settings → "Hide Non-Public Objects"`

| Setting State | Effect |
|---|---|
| **Enabled** | Hides objects where `Axon Viewing = "Non-Public"` for users who are **NOT** stakeholders |
| **Disabled** | No additional hiding beyond standard permission matrix |

> **Note:** Stakeholders are exempt from this setting — they still see their objects regardless.

---

## 🛡️ Security Principles

| # | Principle | Description |
|---|---|---|
| 1️⃣ | **Stakeholder Isolation Rule** | Stakeholder access grants view of object only — no related objects, parents, children, or segment metadata |
| 2️⃣ | **Relationship Hiding Rule** | Partial access to relationship yields `"Hidden Relationship Exists"` — never silent leakage |
| 3️⃣ | **Lock Metadata Restriction Rule** | Lock reason and timestamp visible only to SuperAdmin, responsible Admin, Stakeholders, and CR Owner |
| 4️⃣ | **Search Filtering Rule** | Search results strictly bound to user's permission scope — locked objects excluded unless authorized |

---

## 🔍 Audit Findings

---

### ❗ Violations (Potential Issues to Validate)

| # | Violation | Area | Severity | Description |
|---|---|---|---|---|
| V-01 | Stakeholder CR access without segment check | Change Requests | 🔴 High | A WebUser who is a CR Stakeholder must ALSO have access to that CR's Segment. If segment check is bypassed, privilege escalation occurs. |
| V-02 | Admin creating new segment via API | Admin Panel | 🔴 High | Admin can view segments list but must not be able to POST/PUT new segments via direct API call — must be enforced server-side, not just UI-hidden. |
| V-03 | Soft-deleted objects leaking in search | Search | 🔴 High | If search query does not explicitly filter `status != deleted` for WebUser/Guest, deleted objects may appear. |
| V-04 | Lock metadata exposed in API response | Locks | 🟠 Medium | API returning lock reason + timestamp to unauthorized users (WebUser/Guest) even if UI doesn't render it. |
| V-05 | Relationship traversal leaking segment data | Maps / Impact Tab | 🟠 Medium | If linked-object data is returned server-side before permission check, segment metadata may be embedded in response payload. |
| V-06 | Guest bypassing search filter | Search | 🟠 Medium | Guest must only see Enterprise segment + `Viewing = Public`. If filter is applied client-side only, a direct API call could bypass it. |
| V-07 | Stakeholder isolation not enforced on API | Object API | 🟠 Medium | If API for an object returns `parentId`, `childrenIds`, or `segmentName` in the object payload when accessed via Stakeholder path — isolation is broken. |
| V-08 | Admin sees Role Permissions via direct URL | Admin Panel | 🟡 Low | Role Permissions page must be server-side protected, not just removed from Admin's navigation menu. |
| V-09 | WebUser sees 🔒 icon on objects they didn't lock | Locked Object Display | 🟡 Low | Lock icon should NOT appear for WebUser unless they locked the object themselves. If the icon is shown for all locked objects, it leaks lock existence metadata. |
| V-10 | Permanent delete exposed to Admin via API | Object Deletion | 🔴 High | Permanent delete must be SuperAdmin-only — server-side authorization check, not just UI restriction. |

---

### ⚠️ Edge Case Risks

| # | Risk | Description |
|---|---|---|
| E-01 | **Admin re-assigned from segment** | When an Admin is removed from a segment, their existing sessions should immediately lose access. Token/session invalidation must be triggered. |
| E-02 | **Object moved between segments** | If an object moves from Private Segment A to Public (Enterprise), users who only had Stakeholder access may now see it with full segment context — violating expected isolation. |
| E-03 | **Stakeholder added after object locked** | If a user becomes a Stakeholder AFTER an object is locked, they should gain lock metadata visibility. If roles are cached, they may not immediately gain access. |
| E-04 | **Impact tab with cross-segment private objects** | Object A (Private Seg 1) links to Object B (Private Seg 2). User has access to Seg 1 but not Seg 2. Must show `"Hidden Relationship Exists"` — must NOT show any metadata of Object B. |
| E-05 | **Guest with stale session token** | If a Guest account is upgraded to WebUser, old session tokens should not inherit new permissions without re-authentication. |
| E-06 | **Search Setting "Hide Non-Public" with Stakeholder** | A Stakeholder whose object is `Non-Public` must still see it when setting is enabled. Edge case: user is both a Stakeholder AND has segment access — must handle without double-counting or hiding. |
| E-07 | **Soft-delete + Stakeholder** | If an object is soft-deleted and a user is a Stakeholder, the object must remain invisible to WebUser (soft-delete rule takes priority over Stakeholder rule). |
| E-08 | **Admin Manage Locks + object migrated to non-assigned segment** | Admin locks an object, then that object is moved to a segment they don't manage. Admin should lose lock management rights to that object immediately. |
| E-09 | **CR with mixed-segment objects** | A CR touching objects in both Enterprise and Private segments — visibility rules for each object in the CR must be evaluated independently. |
| E-10 | **Deleted object in Map** | If a soft-deleted object exists in a relationship shown on the Map, it must be invisible to WebUser/Guest even if the non-deleted side is visible. |

---

### ✅ Confirmation Checklist

#### Access Control — Core

| # | Check | Expected Result |
|---|---|---|
| C-01 | SuperAdmin sees all objects in all segments | ✅ Pass |
| C-02 | SuperAdmin sees soft-deleted objects | ✅ Pass |
| C-03 | SuperAdmin can permanently delete objects | ✅ Pass |
| C-04 | Admin only sees objects in assigned segments | ✅ Pass |
| C-05 | Admin cannot see Role Permissions | ✅ Pass |
| C-06 | Admin cannot create new segments | ✅ Pass |
| C-07 | WebUser cannot access Admin Panel | ✅ Pass |
| C-08 | WebUser cannot see deleted objects | ✅ Pass |
| C-09 | Guest can only see Enterprise + Public objects | ✅ Pass |
| C-10 | Guest sees no dashboards | ✅ Pass |

#### Stakeholder Isolation

| # | Check | Expected Result |
|---|---|---|
| C-11 | Stakeholder sees target object only | ✅ Pass |
| C-12 | Stakeholder cannot see parent/child objects via Stakeholder path | ✅ Pass |
| C-13 | Stakeholder cannot see segment metadata via Stakeholder path | ✅ Pass |
| C-14 | WebUser Stakeholder on CR must also have segment access | ✅ Pass |

#### Locks

| # | Check | Expected Result |
|---|---|---|
| C-15 | Lock reason hidden from WebUser (non-stakeholder) | ✅ Pass |
| C-16 | Lock reason visible to SuperAdmin | ✅ Pass |
| C-17 | Lock reason visible to responsible Admin | ✅ Pass |
| C-18 | Lock icon only visible to WebUser if they locked the object | ✅ Pass |
| C-19 | Locked objects appear in search for SuperAdmin | ✅ Pass |
| C-20 | Locked objects do NOT appear in search for unauthorized WebUser | ✅ Pass |

#### Soft Delete

| # | Check | Expected Result |
|---|---|---|
| C-21 | Soft-deleted objects invisible to WebUser | ✅ Pass |
| C-22 | Soft-deleted objects invisible to Guest | ✅ Pass |
| C-23 | Soft-deleted objects visible to SuperAdmin | ✅ Pass |
| C-24 | Soft-deleted objects visible to Admin (assigned segment) | ✅ Pass |

#### Maps & Relationships

| # | Check | Expected Result |
|---|---|---|
| C-25 | Full relationship shown when user sees both sides | ✅ Pass |
| C-26 | "Hidden Relationship Exists" shown for partial access | ✅ Pass |
| C-27 | Nothing shown when user has access to neither object | ✅ Pass |
| C-28 | Private segment object only links to Enterprise or same-segment objects | ✅ Pass |

#### Search

| # | Check | Expected Result |
|---|---|---|
| C-29 | Search results respect segment permissions | ✅ Pass |
| C-30 | "Hide Non-Public" setting filters Non-Public objects for non-Stakeholders | ✅ Pass |
| C-31 | "Hide Non-Public" does NOT hide objects from their Stakeholders | ✅ Pass |

---

### 🔍 Suggested Automated Test Scenarios

#### 🔴 Critical — Must-Pass Tests

```gherkin
Scenario TC-001: SuperAdmin accesses private segment object
  Given I am logged in as SuperAdmin
  When I navigate to an object in a private segment
  Then I should see the object with full metadata
  And I should see lock reason and timestamp if locked

Scenario TC-002: WebUser accesses object outside permitted segment
  Given I am logged in as WebUser
  And I am NOT a Stakeholder of Object-X
  And Object-X is in a Private Segment I don't have access to
  When I try to access Object-X
  Then I should receive a 403 Forbidden response

Scenario TC-003: WebUser as Stakeholder — Isolation Enforced
  Given I am logged in as WebUser
  And I am a Stakeholder of Object-Y (in inaccessible segment)
  When I access Object-Y
  Then I should see only Object-Y's own fields
  And I should NOT see parentId, childrenIds, or segmentName in the API response

Scenario TC-004: Admin reads Role Permissions via direct URL
  Given I am logged in as Admin
  When I make a GET request to /admin/role-permissions
  Then I should receive a 403 Forbidden response (server-side enforcement)

Scenario TC-005: Soft-deleted object not visible in WebUser search
  Given Object-Z has status = deleted
  And I am logged in as WebUser
  When I perform a search that would match Object-Z
  Then Object-Z should NOT appear in search results

Scenario TC-006: Guest bypasses search filter via direct API call
  Given I am logged in as Guest
  When I make a direct API call to /search?segment=private&q=test
  Then only Enterprise + Public results should be returned (server enforced)

Scenario TC-007: Admin permanently deletes object via API
  Given I am logged in as Admin
  When I make a DELETE request to /objects/{id}/permanent
  Then I should receive a 403 Forbidden response

Scenario TC-008: Map — partial access shows hidden relationship
  Given I am logged in as WebUser with access to Object-A only
  And Object-A has a relationship to Object-B (in inaccessible segment)
  When I view the Map for Object-A
  Then I should see "Hidden Relationship Exists" with a 🔒 icon for Object-B
  And no metadata from Object-B should be present in the API response

Scenario TC-009: CR visibility — Stakeholder without segment access
  Given I am logged in as WebUser
  And I am a Stakeholder of CR-100
  But I do NOT have access to CR-100's segment
  When I try to view CR-100
  Then I should NOT see CR-100

Scenario TC-010: Lock icon visibility — WebUser non-owner
  Given Object-M is locked by a different user
  And I am logged in as WebUser (not the locker, not a stakeholder)
  When I view a list containing Object-M
  Then I should NOT see a lock icon on Object-M
```

#### 🟠 Medium Priority Tests

```gherkin
Scenario TC-011: Admin session invalidated after segment removal
  Given Admin-A is assigned to Segment-S
  And Admin-A has an active session
  When Admin-A is removed from Segment-S
  Then Admin-A's next request to Segment-S objects should return 403

Scenario TC-012: "Hide Non-Public" setting — Stakeholder exemption
  Given "Hide Non-Public Objects" is ENABLED
  And Object-N has Axon Viewing = "Non-Public"
  And I am logged in as WebUser and I am a Stakeholder of Object-N
  When I search for Object-N
  Then Object-N SHOULD appear in my results

Scenario TC-013: Impact Tab — cross-segment private link
  Given Object-A (Private Seg 1) links to Object-B (Private Seg 2)
  And I have access to Seg 1 but NOT Seg 2
  When I view the Impact Tab for Object-A
  Then I should see "Hidden Relationship Exists" for the Object-B link
  And ZERO metadata from Object-B or Seg 2 should appear

Scenario TC-014: Manage Locks — Admin scope enforced
  Given I am logged in as Admin assigned to Segment-X
  When I access Manage Locks tab
  Then I should ONLY see locked objects from Segment-X
  And locked objects from Segment-Y (unassigned) should NOT appear

Scenario TC-015: Soft-deleted object on Map
  Given Object-D is soft-deleted
  And Object-E (visible) has a relationship to Object-D
  And I am logged in as WebUser
  When I view the Map for Object-E
  Then Object-D should NOT appear (not even as "Hidden Relationship Exists")
```

---

## 📊 Summary Risk Assessment

| Category | Status | Risk Count |
|---|---|---|
| Privilege Escalation | ⚠️ Review Required | 3 |
| Cross-Segment Leakage | ⚠️ Review Required | 4 |
| Locked Object Behavior | ⚠️ Review Required | 2 |
| Deleted Object Visibility | ✅ Well-Defined | 1 |
| Stakeholder Isolation | ⚠️ Review Required | 3 |
| Relationship Hiding | ✅ Well-Defined | 2 |
| Admin Panel Restrictions | ⚠️ Review Required | 2 |
| Guest Access | ✅ Well-Defined | 1 |
| Enterprise Segment Visibility | ✅ Well-Defined | 0 |
| Metadata Exposure | ⚠️ Review Required | 2 |

> **Total Identified Violations:** 10
> **Total Edge Case Risks:** 10
> **Total Confirmation Checks:** 31
> **Total Automated Test Scenarios:** 15

---

## 🔐 Final Recommendation

1. **Enforce all restrictions server-side** — Never rely solely on UI hiding. API endpoints must validate permissions independently.
2. **Audit API response payloads** for Stakeholder-path requests — strip all relational and segment metadata before returning.
3. **Session invalidation** must be real-time when Admin segment assignments change.
4. **Soft-delete filter** must be applied at the query layer (SQL `WHERE status != 'deleted'`) for WebUser/Guest, not post-retrieval.
5. **Penetration test** all 10 identified violations before release.
6. **Automate** the 15 test scenarios in the CI/CD pipeline as regression tests.

---

*Document generated by AI Security & Access Control Auditor Agent — 2026-02-25*
*Based on the Final Approved Visibility & Permission Logic Specification*
