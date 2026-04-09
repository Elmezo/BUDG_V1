# Backend — Unison Search (صفحة `search.html`)

وثيقة مرجعية لـ **الخادم (Java)** المرتبط بتجربة **Unison Search** في الواجهة `http://localhost:8080/search.html`. الحزمة الجذرية: `com.example.unisonsearch`.

---

## 1. الهدف المعماري

| طبقة | دورها |
|------|--------|
| **Servlets** | نقاط HTTP، JSON، CORS، استخراج `userId` |
| **UnisonSearchService** | تنفيذ البحث المركّب (FIND / AND / OR / NOT)، المسافات البيانية، الفلاتر، الـ facets الخاصة (مثل Active Tasks) |
| **SearchService** | بناء وتنفيذ SQL عبر `QueryBuilder`، دعم `searchGroups`، تخصيص الحقول، الـ segments |
| **GraphTraversalService** | توسعة النتائج عبر علاقات الرسم البياني بين الكيانات |
| **CompoundQueryService** | دمج نتائج شروط متعددة |
| **UnifiedSearchService** | مسار بديل لبحث مرتبط بكائن محدد (`objectId`) لمجموعة facets محدودة |
| **UnisonFacetService** | تفضيلات المستخدم لشبكة الـ facets (ظهور، ترتيب، حقول نشطة) |
| **KeywordSearchService / FuzzySearchUtil** | بحث نصي وضبابي حسب الإعدادات |
| **Repository** | `DatabaseHelper`, `QueryBuilder`, `TaskRepository`, SQL مساعد للـ segments |

---

## 2. المصادقة والفلاتر (`AuthFilter`)

- **`/api/*`** يمر عبر `AuthFilter` (انظر `com.example.budg_v2.filter.AuthFilter`).
- **استثناء صريح:** أي طلب يبدأ بـ **`/api/unison/`** يُسمح به **دون مصادقة** (بحث عام حسب التعليق في الكود). عملياً **`POST /api/unison/search`** قد يعمل بدون جلسة؛ مع ذلك الـ servlet نفسه يحاول قراءة `userId` من السمة، الجلسة، أو كوكي `ACCESS_TOKEN` لتطبيق **تفضيلات الـ facets** وتقييد الـ segment عند توفر مستخدم.
- **`/api/unified/search`** ليس تحت `/api/unison/` → يخضع لقواعد الـ API العادية (يتطلب مصادقة عادة).
- مسارات **`/UnisonSearch/*`** غير مذكورة في نفس الـ filter كـ `/api/*`؛ تحقق من نشر التطبيق إن كان هناك filter آخر على `/*`. الافتراض: أجزاء من واجهة البحث تستدعي `/UnisonSearch/api/...` مباشرة.

---

## 3. نقاط النهاية (Endpoints) — خريطة كاملة

### 3.1 البحث الأساسي (الواجهة تستخدمه بشكل رئيسي)

| Method | المسار | الـ Servlet | الوصف |
|--------|--------|-------------|--------|
| `POST` | **`/api/unison/search`** | `UnisonSearchApiServlet` | البحث المركّب عبر كل الـ facets؛ جسم JSON حسب `UnisonSearchRequest`. |
| `GET` | `/api/unison/search` | نفس الـ servlet | **405** — يُرجع رسالة لاستخدام POST. |

**جسم الطلب (مختصر):**

```json
{
  "searches": [
    {
      "operator": "FIND",
      "facet": "DATASET",
      "keyword": "نص",
      "filters": { },
      "hierarchicalOptions": { },
      "searchFields": { "name": true, "ref": false },
      "indentLevel": 0
    }
  ],
  "options": {
    "maxDepth": 1,
    "includeCounts": true
  }
}
```

- **`maxDepth`:** عمق اجتياز الرسم (0 = بدون توسعة؛ القيم السالبة تُضبط إلى 1 في الـ servlet).
- **مسار موحّد اختياري:** إذا وُجد **`objectId` > 0** في جذر JSON **و** الـ facet من القائمة المدعومة (`dataset`, `system`, `glossary`, `attribute`, `interface`, `project`, `process`, `policy`, `capability`)، يُستخدم **`UnifiedSearchService`** ثم تُحوَّل النتيجة إلى شكل `UnisonSearchResponse`. باقي الـ facets تبقى على **`UnisonSearchService`**.

### 3.2 إعدادات الـ facets للمستخدم والمدير

النمط: **`/api/unison/*`** — `UnisonFacetServlet` (`pathInfo` بعد الـ servlet mapping).

| Method | المسار الفعلي | الوصف |
|--------|----------------|--------|
| `GET` | `/api/unison/facets` | جلب إعدادات الـ facets للمستخدم الحالي. |
| `POST` | `/api/unison/facets/save` | حفظ التخطيط (مصفوفة `facets` في JSON). |
| `POST` | `/api/unison/facets/reset` | إعادة للافتراضي. |
| `GET` | `/api/unison/defaults` | افتراضيات SuperAdmin (يتطلب صلاحية admin). |
| `POST` | `/api/unison/defaults/save` | حفظ افتراضيات SuperAdmin. |

### 3.3 القيم الافتراضية لأعمدة الشبكة (UNISON_DEFAULTS)

| Method | المسار | الـ Servlet |
|--------|--------|-------------|
| * | `/UnisonSearch/api/defaults` | `UnisonDefaultsServlet` |
| * | `/api/unison-defaults` | نفس الـ servlet (تحت `/api/` → خاضع لـ AuthFilter) |

### 3.4 فلاتر الواجهة (Filter panel)

| Method | المسار | الوصف |
|--------|--------|--------|
| `GET` | **`/UnisonSearch/api/filter-fields?facetId=DATASET`** | قائمة حقول الفلترة من `FilterMetadataConfig`. |
| `GET` | **`/UnisonSearch/api/filter-values/{fieldId}?facetId=DATASET`** | قيم ممكنة لحقل فلترة (مسار `pathInfo` = `/fieldId`). |

### 3.5 بحث الأشخاص (Autocomplete في الفلاتر)

| Method | المسار | الـ Servlet |
|--------|--------|-------------|
| `GET` | `/UnisonSearch/api/people/search?query=...&limit=10` | `PeopleSearchServlet` |

### 3.6 البحوث المحفوظة (Saved searches)

| Method | المسار | الوصف |
|--------|--------|--------|
| متعدد | **`/api/search/*`** | `SavedSearchServlet`: قائمة، تفاصيل، تشغيل (`.../id/run`)، إنشاء/تحديث/حذف، مشاركة، إلخ. يتطلب مستخدمًا (`userId` من الجلسة). |

### 3.7 مسار Legacy تحت `/UnisonSearch/*`

| Method | المسار | الـ Servlet | ملاحظات |
|--------|--------|-------------|----------|
| `GET` | `/UnisonSearch/{module}/...` | `UnisonSearchServlet` | بحث حسب `SearchParams`؛ يتضمن حالات مثل `config/fuzzy-search`، `activeTasks` / `active-tasks` مع `WorkflowTaskDAO`، إلخ. |

### 3.8 إعدادات الكائنات المرتبطة (Related objects)

| Method | المسار | الـ Servlet |
|--------|--------|-------------|
| * | `/api/unison-search/related-objects/config` | `RelatedObjectsConfigServlet` |

### 3.9 API موحّد منفصل (بديل معماري)

| Method | المسار | الـ Servlet |
|--------|--------|-------------|
| `POST` | **`/api/unified/search`** | `UnifiedSearchApiServlet` — نموذج `SearchRequest` / `SearchResponse`، مع `ResultFilter` و `SearchLimiter` بعد التنفيذ. |

---

## 4. نماذج JSON الرئيسية

### 4.1 `UnisonSearchRequest` (`model/UnisonSearchRequest.java`)

- **`searches`:** قائمة `SearchItem`.
- **`SearchItem`:** `operator` (FIND, AND, OR, NOT)، `facet`، `keyword`، `filters` (Map)، `hierarchicalOptions`، `searchFields`، `indentLevel`.
- **`SearchOptions`:** `maxDepth` (افتراضي 1)، `includeCounts`.

### 4.2 `UnisonSearchResponse` (`model/UnisonSearchResponse.java`)

- **`success`**, **`error`** (عند الفشل).
- **`results`:** `Map<String, FacetResult>` — المفتاح عادة معرف الـ facet بصيغة موحّدة (مثل `DATASET`).
- **`searchCounter`**, **`executionTimeMs`**.
- **`relatedObjects`:** `facet → (relatedFacet → Set<ids>)` للعلاقات المعروضة في الواجهة.

### 4.3 `FacetResult` (`model/FacetResult.java`)

- **`ids`**, **`count`**, **`totalCount`**, **`hasActiveFilter`**, **`depthById`**, **`rows`** (صفوف جاهزة للعرض في الجدول بعد الإثراء).

---

## 5. سلوك `UnisonSearchApiServlet` (ملخص التنفيذ)

1. قراءة JSON → `UnisonSearchRequest` (Gson مع deserializer مخصص لـ `filters`: مصفوفة تُهمل كخريطة فارغة).
2. التحقق من وجود `searches` غير فارغة؛ وإلا **400**.
3. تحديد `maxDepth` من `options`.
4. اختيار **Unified** مقابل **Unison** حسب `objectId` والـ facet كما في القسم 3.1.
5. استدعاء **`unisonSearchService.executeUnisonSearch(..., userId)`** (أو المسار الموحّد).
6. إن وُجد **`userId`** تطبيق **`applyFacetPreferences`** عبر `UnisonFacetService.getFacetsForUser`: إعادة ترتيب النتائج، إخفاء facets غير نشطة، وقص أعمدة `rows` حسب `activeFields` مع الإبقاء على `ID` والحقول المخصصة الديناميكية.
7. إرجاع JSON لـ `UnisonSearchResponse`.

**استخراج `userId`:** `request.getAttribute("userId")`، ثم الجلسة، ثم JWT من كوكي `ACCESS_TOKEN`.

---

## 6. `UnisonSearchService` — ماذا يفعل (مستوى عالٍ)

- يتحقق من صحة المشغّل الأول (مثلاً **NOT** لا يكون أول شرط).
- ينفّذ شروطًا مركّبة، يدمج مع **GraphTraversalService** و **CompoundQueryService**.
- يدعم فلاتر الـ segment وسياق **Super Admin** حيث ينطبق.
- حالات خاصة: **Active Tasks** (مهلة زمنية في السجلات)، توسعة هرمية (أطفال capability، إلخ)، **Change Requests**، صفوف مخصصة لـ People / Org Unit / Role عند الحاجة.
- يملأ **`relatedObjects`** و **`FacetResult.rows`** للعرض في `search.html`.

للتفاصيل الدقيقة لكل facet راجع الملف الطويل: `service/UnisonSearchService.java`.

---

## 7. طبقة البيانات والاستعلام

- **`QueryBuilder`:** يبني SQL من `SearchParams` أو من تعريف `searchGroups` (JSON) مع سياق segment.
- **`DatabaseHelper`:** تنفيذ الاستعلامات وربط النتائج.
- **`FilterMetadataConfig`:** تعريف حقول الفلترة لكل facet (يغذي `FilterFieldsServlet` / `FilterValuesServlet`).
- **`SegmentAccessSql` / `SegmentAccessContext`:** تقييد البيانات حسب segment المستخدم.

---

## 8. الـ Parser والكلمات المفتاحية (مسار آخر في النظام)

- `parser/QueryParser`, `SQLGeneratorVisitor`, `OperatorNode` — لتحليل استعلامات منطقية وتحويلها إلى SQL عند المسارات التي تستخدمها (متكامل مع طبقة البحث حسب الإعداد).

---

## 9. صلة `search.html` بالـ backend

| احتياج الواجهة | Endpoint / خدمة |
|-----------------|-----------------|
| بحث مركّب + تبويبات facets | `POST /api/unison/search` |
| أعمدة افتراضية | `/UnisonSearch/api/defaults` أو `/api/unison-defaults` |
| تبويبات المستخدم | `GET/POST /api/unison/facets...` |
| لوحة الفلاتر | `/UnisonSearch/api/filter-fields`, `/UnisonSearch/api/filter-values/*` |
| أشخاص في الفلتر | `/UnisonSearch/api/people/search` |
| مهام نشطة / بيانات facet بالمعرفات | `/api/active-tasks`, استدعاءات أخرى في `search-api.js` + `/UnisonSearch/...` حسب الفئة |
| بحوث محفوظة / تاريخ | `/api/search/*` |
| حقول مخصصة (custom fields) | `/api/custom-fields/metadata` (تطبيق BUDG الرئيسي، ليس داخل حزمة unisonsearch فقط) |

---

## 10. أخطاء شائعة واختبار API

- **400:** `searches` فارغة أو غير صالحة؛ أو `facetId` مفقود في فلاتر الـ UnisonSearch API.
- **401/403:** على مسارات تتطلب جلسة (مثل `/api/unison/facets` بدون مستخدم).
- **500:** أخطاء SQL أو استثناءات غير متوقعة؛ الرد غالبًا `UnisonSearchResponse` مع `success: false` و `error`.

**مثال اختبار سريع (POST):**

```http
POST /api/unison/search
Content-Type: application/json

{
  "searches": [
    { "operator": "FIND", "facet": "DATASET", "keyword": "test", "filters": {} }
  ],
  "options": { "maxDepth": 1, "includeCounts": true }
}
```

---

## 11. ملفات مرجعية في المشروع

| المسار |
|--------|
| `src/main/java/com/example/unisonsearch/servlet/*.java` |
| `src/main/java/com/example/unisonsearch/service/UnisonSearchService.java` |
| `src/main/java/com/example/unisonsearch/service/SearchService.java` |
| `src/main/java/com/example/unisonsearch/model/UnisonSearchRequest.java` |
| `src/main/java/com/example/unisonsearch/model/UnisonSearchResponse.java` |
| `src/main/java/com/example/unisonsearch/config/FilterMetadataConfig.java` |
| `src/main/webapp/search.html` + `assets/js/UnisonSearch/search-api.js` |

---

**See also:** `unison-search-testsprite-coverage.md` — مصفوفة اختبارات شاملة لكل استدعاءات الشبكة والرحلات E2E لـ TestSprite.

---
