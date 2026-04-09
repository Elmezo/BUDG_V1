# Checklist: Bulk Delete و Validation الحذف مقابل مواصفات Axon

مقارنة منطق Bulk Delete والتحقق من صحة الحذف (object validation) في الكود مع مواصفات Informatica Axon.

---

## 1. الشروط العامة لأي Object قبل الحذف

| الشرط في المستند | الحالة في الكود | الملف / الملاحظة |
|------------------|------------------|-------------------|
| الحالة = **Deleted** | **موجود** | `BulkDeleteValidationHelper.java`: `validateStatus()` ترفض إذا الـ status غير "Deleted" (للـ facets التي لها `hasStatus=true`) |
| لا Related Objects في تبويب **Impact** | **موجود** | نفس الملف: `checkImpactRelationships()` و `getAllImpactTablesForFacet()` تغطي جداول الـ Impact لكل facet |
| لا **Child Objects** | **موجود** | نفس الملف: `checkChildObjects()` باستخدام `parentColumn` في الـ config |

---

## 2. من يقدر يحذف؟ (الأدوار)

| المطلوب في المستند | الحالة في الكود | الملاحظة |
|--------------------|------------------|----------|
| **SuperAdmin** فقط للحذف النهائي | **مطبق** | `BulkDeleteServlet` و `BulkDeleteService` يتحققان من `isUserSuperAdmin(userId)` للحذف النهائي؛ Admin/WebUser يسمح لهم بتغيير الحالة لـ Deleted فقط. |
| **Admin / WebUser** يغيرون الحالة لـ Deleted فقط | **مطبق** | فصل بين "تحديث الحالة إلى Deleted" (Admin/WebUser) و "إزالة نهائية" (SuperAdmin فقط). |

---

## 3. Object Types: ما هو موجود في الـ Validation مقابل المستند

### 3.1 موجود في المستند **وموجود** في الكود (BulkDeleteValidationHelper FACET_CONFIGS)

- **Attribute** – مع special checks: `attribute-relationships` (Process, Policy, Project)
- **Capability** – مع `capability-relationships`
- **Committee** – مع `committee-relationships`
- **Data Set (dataset)** – مع `dataset-relationships`, `dataset-content-summary`, `dataset-attributes`
- **Geography** – بدون status، مع `geography-regulator-links`, `geography-legal-entity-links`, `geography-regulation-links`
- **Glossary** – مع special checks كاملة (alias, system, dataset, attribute, dq-rule links, relationships)
- **People** – مع `people-managers`, `people-stakeholder-links`, `people-dq-rules`
- **Process** – مع `process-relationships`
- **Project** – مع `project-relationships`
- **Regulation** – مع `regulation-relationships`, `regulation-regulator-links`
- **Regulator** – بدون status، مع `regulator-regulation-links`, `regulator-geography-links`
- **Regulatory Theme** – مع `regulatory-theme-regulations`
- **Interface (system-interface)** – مع `interface-glossary-links`
- **System** – مع `datasets` (system-datasets)
- **Data Quality Rule** – مع validation خاصة (لا Attributes، لا Technical Rule Reference؛ مسموح مرتبط بـ System)

### 3.2 سابقاً ناقص وتمت إضافته

- **Data Quality Rule** – تمت إضافة الـ facet إلى `FACET_CONFIGS` و `FACET_TABLE_MAP` مع الشروط الخاصة.

---

## 4. استثناءات: لا Bulk Delete في

| المستند | الكود | الحالة |
|---------|--------|--------|
| Active Tasks | `EXCLUDED_FACETS_FOR_DELETE`: active-task, activetask, active_task | **موجود** |
| Physical Fields | نفس الـ config: physical-field, physicalfield, physical_field | **موجود** |
| Change Requests | نفس الـ config: change-request, changerequest, change_request | **موجود** |

**زيادة في الكود:** الـ config يستبعد أيضًا **role** من Bulk Delete، وهذا غير مذكور في المستند.

---

## 5. Geography و Regulator (بدون Axon Status)

| المستند | الكود | الحالة |
|---------|--------|--------|
| لا Axon Status لـ Geography و Regulator | في `BulkDeleteValidationHelper`: لـ geography و regulator `hasStatus = false` فلا يطبق `validateStatus()` | **موجود** |
| شروط خاصة فقط | special checks مطبقة (geography-regulator-links, geography-legal-entity-links, geography-regulation-links؛ regulator-regulation-links, regulator-geography-links) | **موجود** |

---

## 6. تحذير Stakeholders و Confirmation

| المستند | الكود | الحالة |
|---------|--------|--------|
| إذا فيه Stakeholders يظهر Confirmation؛ عند الموافقة يتم حذفهم ثم حذف الـ Object | `checkStakeholders()` يعيد warning؛ عند تأكيد STAKEHOLDERS يتم `removeStakeholders()` ثم الحذف | **موجود** |

---

## 7. Special Checks (Bulk Delete)

تم تنفيذ جميع الـ special checks الناقصة في `BulkDeleteValidationHelper.performSpecialCheck()`:

- **Glossary:** `glossary-alias-names`, `glossary-system-links`, `glossary-dataset-links`, `glossary-attribute-links`, `glossary-dq-rule-links`, `glossary-relationships`
- **People:** `people-managers`, `people-dq-rules`, `people-stakeholder-links`

---

## 8. ما هو **زيادة** عندنا عن المستند (Bulk Delete / Validation)

- **Facets مدعومة في Bulk Delete** ولا يذكرها المستند كقائمة Axon: **policy**, **legal-entity**, **org-unit**, **business-area**, **client**, **product**.
- **استبعاد الـ role** من Bulk Delete في `BulkUpdateDefinitionConfig.EXCLUDED_FACETS_FOR_DELETE`.

---

## 9. ملخص

- **موجود ومطابق:** الشروط العامة (Deleted + Impact + Child)، استثناءات Active Tasks / Physical Fields / Change Requests، معاملة Geography و Regulator بدون status، تحذير وتأكيد Stakeholders، ومعظم شروط الـ Object types، تمييز SuperAdmin vs Admin/WebUser، Data Quality Rule، وكل الـ special checks لـ Glossary و People.
- **زيادة:** دعم Bulk Delete لـ policy, legal-entity, org-unit, business-area, client, product؛ واستبعاد **role** من Bulk Delete.
