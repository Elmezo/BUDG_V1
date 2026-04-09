# TestSprite — تقرير اختبارات الخادم (Unison Search)

---

## 1️⃣ Document Metadata

- **Project Name:** BUDG_V2
- **Date:** 2026-04-08
- **Prepared by:** TestSprite MCP + مسار نفق محلي (`generateCodeAndExecute`)
- **Scope:** واجهات الخادم المرتبطة بـ `http://localhost:8080/search.html` — تركيز على **البحث** (`/api/unison/search`، فلاتر، defaults، مهام نشطة، أشخاص)
- **مرجع الخطة:** `testsprite_tests/testsprite_backend_test_plan.json`
- **ملخص الكود:** `testsprite_tests/tmp/code_summary.yaml` (نوع `backend`)

---

## 2️⃣ Requirement Validation Summary

### Requirement: بحث Unison أساسي وسلبيات الطلب

**الوصف:** `POST /api/unison/search` يقبل استعلام FIND صالحاً؛ `GET` غير مسموح؛ الجسم الفارغ أو NOT كأول عامل يُرفض منطقياً.

| Test | الملف | الحالة | ملاحظات |
|------|--------|--------|---------|
| TC001 | `TC001_post_api_unison_search_with_valid_FIND_query.py` | ناجح | FIND على facet مع `keyword` و`options` |
| TC002 | `TC002_get_api_unison_search_returns_method_not_allowed.py` | ناجح | توافق مع `UnisonSearchApiServlet` (405) |
| TC003 | `TC003_post_api_unison_search_rejects_empty_searches.py` | ناجح | `searches` فارغة → 400 أو `success: false` |
| TC004 | `TC004_post_api_unison_search_rejects_NOT_as_first_operator.py` | ناجح | يطابق رسالة الخدمة عند NOT أولاً |

- **روابط التصور (TestSprite):** مذكورة في `testsprite_tests/tmp/raw_report.md` لمشروع الاختبار `9e21f103-80ab-4614-a08d-b67ca8c3157a`.

---

### Requirement: لوحة الفلاتر وتهيئة الأعمدة (مسارات `/UnisonSearch/api/*`)

**الوصف:** حقول الفلتر تتطلب `facetId`؛ DATASET يعيد تعريفات؛ defaults يعيد بنية UNISON_DEFAULTS.

| Test | الملف | الحالة | ملاحظات |
|------|--------|--------|---------|
| TC005 | `TC005_get_UnisonSearch_filter_fields_requires_facetId.py` | ناجح | بدون `facetId` → 400 |
| TC006 | `TC006_get_UnisonSearch_filter_fields_for_DATASET_returns_metadata.py` | ناجح | بيانات وصفية للفلتر |
| TC007 | `TC007_get_UnisonSearch_api_defaults_for_grid_columns.py` | فشل ثم **تصحيح محلي** | الاختبار المولّد توقّع مفاتيح `columns`/`defaultView` غير موجودة في الـ API؛ الشكل الفعلي: **`facets` + `lastUpdated`** (انظر `UnisonDefaultsServlet` و`search-columns.js`). تم تحديث `TC007_*.py` ليطابق العقد الحقيقي. |

---

### Requirement: بحث متقدّم ومسارات مساندة للواجهة

**الوصف:** `searchFields` في جسم البحث؛ `active-tasks`؛ اكتمال أسماء الأشخاص في الفلاتر.

| Test | الملف | الحالة | ملاحظات |
|------|--------|--------|---------|
| TC008 | `TC008_post_unison_search_with_searchFields_restriction.py` | ناجح | تقييد حقول البحث النصي |
| TC009 | `TC009_get_active_tasks_for_search_facet_context.py` | ناجح | يعتمد على المصادقة في البيئة |
| TC010 | `TC010_get_people_search_autocomplete_endpoint.py` | ناجح | `people/search` بدون 500 |

---

## 3️⃣ Coverage & Matching Metrics

| المجموعة | إجمالي | ناجح | فاشل (قبل التصحيح) |
|----------|--------|------|---------------------|
| بحث Unison + سلبيات | 4 | 4 | 0 |
| فلاتر + defaults | 3 | 2 (+1 بعد إصلاح TC007) | 1 ( assertion خاطئ) |
| مساندة (searchFields / tasks / people) | 3 | 3 | 0 |
| **الإجمالي** | **10** | **9 مؤكد من TestSprite** | **1** (TC007 — تم إصلاح السكربت محلياً) |

نسبة النجاح في التشغيل السحابي الأول: **90%** (9/10). بعد تصحيح assertions في `TC007`، يُتوقع **100%** عند إعادة التشغيل المحلي بـ `requests` ضد `localhost:8080`.

---

## 4️⃣ Key Gaps / Risks

1. **عقد `/UnisonSearch/api/defaults`:** مولّد TestSprite افتراض مفاتيح عامة (`columns`) بدل **`facets`** القادمة من `app_config`. أي خطة اختبار يجب أن تنبع من `UnisonDefaultsServlet` والوثيقة `docsV/unison-search-backend.md`.
2. **النفق:** ظهر تحذير `Tunnel probe ECONNRESET` ثم اكتمل التنفيذ؛ إذا فشلت جولات لاحقة، تحقق من أن التطبيق يعمل على المنفذ المتوقع وأن الـ CLI يستخدم نفس المسار.
3. **مصادقة:** بعض السكربتات تستخدم `HTTPBasicAuth`؛ مسارات مثل `/api/unison/search` و`/UnisonSearch/*` قد لا تحتاجها — راجع `AuthFilter` لكل مسار قبل الاعتماد على Basic auth في CI.
4. **تغطية إضافية مقترحة (بحث فقط):** `POST` مع AND/OR متعدد facets، `hierarchicalOptions` لـ CAPABILITY، `GET /api/unified/search`، وسباق طلبات متزامنة — مذكورة في `docsV/unison-search-testsprite-coverage.md` §8.

---

*لتقرير واجهة `search.html` (E2E) راجع `testsprite_tests/testsprite-mcp-test-report.md`.*
