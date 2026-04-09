# Unison Search — تغطية اختبارات TestSprite (شاملة)

هذا الملف يجمع **كل الأماكن** التي يمكن لـ TestSprite (أو أدوات E2E/API مشابهة) أن يختبرها لصفحة **`http://localhost:8080/search.html`** والـ APIs المرتبطة بها.  
مرجع مكمّل: `unison-search-search-html.md` (واجهة)، `unison-search-backend.md` (خادم).

---

## 1. حالات الجلسة والمصادقة (يجب تكرار الاختبار لكل منها حيث ينطبق)

| الحالة | التأثير على السلوك |
|--------|---------------------|
| **زائر بدون تسجيل** | `POST /api/unison/search` مسموح من `AuthFilter`؛ قد لا تُطبَّق تفضيلات الـ facets/الأعمدة كمستخدم مسجّل. |
| **مستخدم عادي مسجّل** (كوكي `ACCESS_TOKEN` + جلسة) | معظم `GET/POST /api/*` تعمل؛ segments، saved searches، export، bulk حسب الصلاحيات. |
| **Super Admin** | `/api/unison/defaults`, `POST /api/unison/defaults/save`, `/admin/api/export-config`, مسارات إدارية أخرى. |

**Assertions مقترحة:** بعد تسجيل الدخول، طلبات مثل `GET /api/me`, `GET /api/unison/facets` تُرجع 200؛ بدون جلسة قد تُرجع 401/403 حسب المسار.

---

## 2. مصفوفة الشبكة — كل استدعاءات الشبكة من `assets/js/UnisonSearch/` (للـ mock / spy / contract tests)

استخدم العمود «متى» لمعرفة أي رحلة تُشغّل الطلب.

| # | Method | مسار (نمط) | ملف يستدعي | متى / ملاحظات |
|---|--------|------------|------------|----------------|
| 1 | `POST` | `/api/unison/search` | `search-api.js` (`executeUnisonSearch`) | أي بحث مركّب؛ حمولة `searches` + `options`. |
| 2 | `GET` | `/UnisonSearch/api/defaults` | `search-columns.js` | تحميل أعمدة UNISON_DEFAULTS عند الإقلاع. |
| 3 | `GET` | `/UnisonSearch/api/filter-fields?facetId=` | `search-filters.js` | فتح/تهيئة لوحة الفلاتر لfacet نشط. |
| 4 | `GET` | `/api/custom-fields/metadata?facetId=` | `search-filters.js` | حقول مخصصة مع الفلاتر. |
| 5 | `GET` | `/UnisonSearch/api/filter-values/{fieldId}?facetId=` | `search-filters.js` | قيم قائمة منسدلة للفلتر. |
| 6 | `GET` | `/UnisonSearch/api/people/search?query=&limit=` | `search-filters.js` | اكتمال كتابة أشخاص في فلتر. |
| 7 | `GET` | متعدد أنماط `/api/changerequests` (+ query) | `search-api.js` | facet طلبات التغيير. |
| 8 | `GET` | `/api/active-tasks` | `search-api.js` | جدول **Active Tasks** (مسار مخصص في الواجهة). |
| 9 | `GET` | `/api/unison/facets` | `search-settings.js`, `facet-management.js` | تبويبات/إعدادات facets. |
| 10 | `POST` | `/api/unison/facets/save` | `facet-management.js` | حفظ تخطيط التبويبات. |
| 11 | `POST` | `/api/unison/facets/reset` | `facet-management.js` | إعادة الافتراضي. |
| 12 | `POST` | `/api/unison/defaults/save` | `facet-management.js` | (مدير) حفظ افتراضيات عامة. |
| 13 | `POST` | `/api/unison/columns/save` | `search-settings.js` | حفظ تفضيلات أعمدة المستخدم لfacet (`facetId`, `columns[]`). |
| 14 | `GET`/`POST` | `/api/unison-search/related-objects/config` | `search-settings.js` | إعدادات عرض الكائنات المرتبطة. |
| 15 | `GET` | `/api/unison-search/related-objects?facet=&id=` | `related-objects.js` | تحميل تفاصيل lazy عند توسيع صف؛ **تأكد من وجود الـ servlet في النشر** إن فشل الطلب. |
| 16 | `GET` | `/api/me` | `saved-searches.js`, `bulk-selection.js` | هوية المستخدم للبحوث المحفوظة/الصلاحيات. |
| 17 | `GET`/`POST`/`PUT`/`DELETE` | `/api/search/*` | `saved-searches.js`, `manage-searches.js` | قائمة، إنشاء (`/api/search/`)، تشغيل `/{id}/run`، مشاركة `/{id}/share`، `my`, `shared`, `recent`. |
| 18 | `GET` | `/api/quick-link/current`, `/api/search/quick-link` | `saved-searches.js` | روابط سريعة للبحث. |
| 19 | `GET` | `/api/users/list?search=` | `manage-searches.js` | مشاركة بحث مع مستخدمين. |
| 20 | `GET` | `/UnisonSearch/{module}` (مثلاً `system`, `glossary`) | `search-debug.js` | أدوات تصحيح (إن وُجدت في البيئة). |
| 21 | `GET` | `/UnisonSearch/config/fuzzy-search` | `search-debug.js`, `search-config.js` | إعدادات البحث الضبابي. |
| 22 | `GET` | `/UnisonSearch/attribute?q=` | `search-links.js` | روابط من الجدول لسيناريوهات attribute. |
| 23 | `GET` | `/maps.html` | `search-maps-integration.js` | تضمين خرائط عند تفعيل تكامل الخرائط. |
| 24 | متغير | `MAP_TEMPLATE_URL` (من `search-map.js`) | `search-map.js` | قالب خريطة جغرافية. |
| 25 | `GET` | `/api/{facet}/dashboard/stats` | `search-dashboard.js` | لكل facet مدعوم في لوحة التحكم (قائمة طويلة: dataset, system, people, …). |
| 26 | `GET` | `/admin/api/export-config` | `search-export.js` | تكوين التصدير (صلاحيات admin). |
| 27 | `GET` | `/api/export/people?format=` | `search-export.js` | تصدير facet **People** (pdf/xlsx/csv حسب المعامل). |
| 28 | `POST` | `/api/export/unison-search` | `search-export.js` | تصدير باقي الـ facets مع خيارات stakeholders. |
| 29 | `GET` | `/api/user/current` | `search-export.js` | سياق المستخدم للتصدير. |
| 30 | `GET` | `/api/bulk-update/can-access` | `bulk-selection.js` | إظهار/تفعيل **Bulk Update**. |
| 31 | `POST` | `/api/admin/environment/data-migration` | `bulk-selection.js` | **Bulk Migrate** (بيئة/بيانات). |
| 32 | `POST` | `/api/table-snapshot/export` | `bulk-selection.js` | تصدير لقطة جدول (.bsnap وما شابه). |
| 33 | `POST` | `/api/workflow_tasks/{taskId}/complete` | `search-table.js` | إكمال مهمة من صف **Active Tasks** عند وجود UI لذلك. |

**ملاحظة:** `POST /api/unified/search` موجود في الخادم لكن الواجهة الرئيسية لـ `search.html` تستخدم **`/api/unison/search`**؛ يمكن لـ TestSprite اختبار الـ contract منفصلًا إذا لزم.

---

## 3. رحلات E2E مقترحة (TestSprite scenarios)

لكل رحلة: **ترتيب الخطوات + ما تتوقع أن تراه في الشبكة أو الـ DOM.**

### A — تحميل الصفحة
1. افتح `search.html`.
2. توقّع: طلبات `defaults`, `facets` (إن كان المستخدم مسجّلاً), i18n، وربما `loadCategoryData` عبر `/api/unison/search` أو مسارات facet حسب التهيئة.
3. DOM: `#searchCategorySidebar`, `.search-main-input`, جدول `.search-table` أو `.no-data-row`.

### B — رابط مع استعلام
1. افتح `search.html?q=keyword`.
2. بعد ~500ms: قيمة الحقل مملوءة ويُنقَر `.search-action-btn` تلقائيًا.
3. توقّع: `POST /api/unison/search` (أو سلسلة تحميل بيانات إضافية).

### C — اختيار facet وتصفية
1. انقر `.category-item` (مثلاً `data-category="dataset"`).
2. افتح `.filter-btn` → `#filterPanel`.
3. توقّع: `filter-fields` + `custom-fields/metadata`؛ عند اختيار حقل قيم: `filter-values`.

### D — بحث مركّب
1. غيّر `.search-type-select` (AND/OR/NOT بعد FIND).
2. أضف شروطًا عبر `#searchCounter` / `#queryBuilderContainer` إن ظهرت.
3. `POST /api/unison/search` مع مصفوفة `searches` متعددة و`indentLevel` إن وُجد.
4. للتفاصيل الكاملة لسلوك البحث انظر **§8** أدناه.

### E — List ↔ Dashboard
1. انقر `.view-tab[data-view="dashboard"]`.
2. توقّع: `GET /api/<facet>/dashboard/stats` حسب الفئة النشطة.
3. العودة إلى List: `.view-tab[data-view="list"]`.

### F — الإعدادات والأعمدة
1. `#settingsBtn` → `#chooseColumnsBtn` → تفعيل/إلغاء مربعات في `#columnsCheckboxes`.
2. عند الحفظ: `POST /api/unison/columns/save`.

### G — التصدير
1. من القائمة: People → `#exportSubmenu*`؛ غير People → `#exportSubmenuOtherFacets*`.
2. توقّع: `GET /api/export/people` أو `POST /api/export/unison-search`؛ وقد `GET /api/user/current`.

### H — بحث محفوظ وتاريخ
1. `.history-btn` / تدفقات `saved-searches.js`.
2. توقّع: `GET /api/search/my`, `recent`, `POST /api/search/`, `GET .../run`.

### I — Bulk actions
1. تحديد صفوف (checkboxes حسب `bulk-selection.js`).
2. `.bulk-update` / `.bulk-delete` / `#bulkMigrateBtn`.
3. توقّع: `GET /api/bulk-update/can-access`؛ طلبات migrate/snapshot حسب الخيار.

### J — Active Tasks
1. اختر facet المهام النشطة إن وُجد في الشريط الجانبي.
2. توقّع: `GET /api/active-tasks`؛ وربما `POST /api/workflow_tasks/.../complete`.

### K — Change requests
1. facet طلبات التغيير.
2. توقّع: استدعاءات `/api/changerequests`.

### L — كائنات مرتبطة (Related)
1. إن وُجد زر/أيقونة توسيع صف (`.expand-related` أو ما يعادلها في الجدول).
2. توقّع: بيانات من `relatedObjects` في رد البحث، أو `GET /api/unison-search/related-objects?...`.

### M — خرائط وجغرافيا
1. facet جغرافي عند تفعيل الخريطة.
2. توقّع: `fetch('/maps.html')` أو قالب الخريطة من `search-map.js`.

### N — موبايل
1. عرض ضيق: `#mobileCategoryToggle` يظهر/يُستخدم؛ `#searchCategorySidebar` تُفتح وتُغلق (`aria-expanded`).

### O — RTL / لغة
1. تبديل لغة إن وُجد في التطبيق: `#rtl-styles` يُفعّل؛ تحقق من اتجاه `.search-controls-bar` والشريط الجانبي.

### P — إدارة التبويبات (Add Category)
1. `#module-container` → منتقي الوحدات من `modules.js`.
2. ثم `POST /api/unison/facets/save` بعد إعادة الترتيب.

---

## 4. اختبارات سلبية وحدود (يجب ألا تنهار الواجهة)

| السيناريو | التوقع المعقول |
|-----------|----------------|
| `POST /api/unison/search` بجسم `{}` أو `searches: []` | 400 أو `success: false` مع `error`. |
| `GET /api/unison/search` | 405 مع رسالة JSON. |
| `filter-fields` بدون `facetId` | 400 من الخادم. |
| انتهاء الجلسة أثناء جلب `/api/unison/facets` | 401 + معالجة `auth-error-handler.js` (رسالة/إعادة توجيه). |
| استجابة بحث فارغة | جدول فارغ أو رسالة no data؛ لا أخطاء console حرجة. |
| timeout بطيء للبحث | ظهور loading overlay (`loading-manager`) ثم اختفاؤه. |

---

## 5. مفاتيح DOM إضافية (للاستقرار في الـ selectors)

- لوحة الاستعلام: `#queryBuilderContent`, `#queryBuilderContainer`
- شرائح فلاتر نشطة: `#activeFiltersChips`, `.active-filters-list`
- فلاتر هرمية: `#hierarchicalFiltersSection`, `#filterRelationshipsBtn`, `#filterApplyHierarchicalBtn`
- إغلاق الفلتر: `#filterPanelClose`
- تبويبات العرض: `.view-tab.active`
- صف بلا بيانات: `.no-data-row`, `.no-data-message`
- جدول مهام/مهام نشطة قد يستخدم أصنافًا إضافية من `search-table.js` (مثل `active-tasks-table`)

---

## 6. تغطية حسب ملف JS (مرجع سريع لـ TestSprite «ما الذي أختبره بعد تغيير الملف؟»)

| ملف | منطقة اختبار |
|-----|----------------|
| `search-init.js` | أول تحميل، auto-load أول facet، ترتيب التهيئة مع i18n |
| `search-input.js` | إدخال البحث، ربط counter، تزامن مع Unison |
| `search-filters.js` | لوحة الفلتر بالكامل + people autocomplete |
| `search-table.js` | فرز الأعمدة، نقر الاسم/الحالة، مهام سير العمل |
| `search-dashboard.js` | كل endpoints الـ `/dashboard/stats` |
| `search-export.js` | مساري التصدير + admin config |
| `saved-searches.js` / `manage-searches.js` | CRUD ومشاركة البحث |
| `bulk-selection.js` | صلاحيات bulk، migrate، snapshot |
| `facet-management.js` | حفظ/إعادة تعيين facets |
| `related-objects.js` | توسيع الصف + API related |
| `search-geographic-map.js` / `search-maps-integration.js` / `search-map.js` | خرائط |
| `segments-cube-panel.js` (محمّل من `search.html`) | لوحة المكعبات إن ظهرت في السياق |

---

## 7. Checklist سريع قبل اعتبار التغطية «كاملة»

- [ ] تحميل صفحة + header حقن من `main.js`/`header.js`
- [ ] **بحث (انظر §8):** FIND، استعلام فارغ، AND/OR/NOT، بحث عبر facetين، مسار الاقتراحات، مسار المشغّلات داخل النص، صفوف محددة + بحث
- [ ] بحث بسيط + بحث بـ `?q=`
- [ ] facet واحد على الأقل لكل نوع رئيسي تستخدمه المنظمة (dataset, people, CR, active tasks, …)
- [ ] فلاتر: حقول + قيم + Apply/Clear
- [ ] List + Dashboard لنفس الفئة
- [ ] أعمدة مخصصة + حفظ
- [ ] تصدير (People + غير People)
- [ ] بحث محفوظ: إنشاء، تشغيل، مشاركة (إن مفعّل)
- [ ] Bulk: على الأقل can-access + مسار واحد ناجح/مرفوض
- [ ] جلسة مسجّل + جلسة بدون تسجيل (حيث ينطبق)
- [ ] موبايل: زر الفئات
- [ ] مراقبة الشبكة: التأكد أن كل المسارات في الجدول §2 إما ناجحة أو مُعالَجة UI عند الفشل

---

## 8. تخصص — اختبارات البحث (Search deep dive)

المنطق الأساسي في `search-input.js` (`performSearch`, `executeMultiConditionSearch`, `addSearchCondition`) و`search-api.js` (`executeUnisonSearch`). استخدم هذا القسم كـ **test plan** منفصل لـ TestSprite حول «البحث» فقط.

### 8.1 عناصر الواجهة المرتبطة بالبحث

| عنصر | Selector / ملاحظة |
|------|-------------------|
| صندوق الكلمة | `.search-main-input` |
| مشغّل السطر (FIND / AND / OR / NOT) | `.search-type-select` — القيم `find`, `and`, `or`, `not`؛ خيارات AND/OR/NOT تُعطَّل حتى أول FIND ناجح (`disableOperatorOptions` / `enableOperatorOptions`) |
| تنفيذ البحث | `.search-action-btn` |
| لوحة الملخص | `#searchCounter`، `#clearSearchBtn`، `#queryBuilderContainer` / `#queryBuilderContent` |
| اقتراحات أثناء الكتابة | `.search-suggestions` (إن وُجدت) — مفتاح **Enter** له مسار خاص عند ظهور الاقتراحات |
| فلاتر مدمجة في طلب Unison | تُبنى عبر `buildFiltersObject()` وتُرسل داخل كل `searches[]` عند وجود فلاتر مطبّقة |
| «البحث في الحقول» (Search in) | يُملأ `searchFields` على عناصر الطلب عند اختيار أعمدة؛ يؤثر على `addSearchCondition` و`executeMultiConditionSearch` |
| Facets هرمية | `GLOSSARY`, `PROCESS`, `POLICY`, `CAPABILITY` — تُضاف `hierarchicalOptions` (`childInclusion`, `applyFilters`) من `window.hierarchicalFilterOptions` |

### 8.2 متى يُستدعى `POST /api/unison/search`؟

في `executeMultiConditionSearch`: يُستخدم Unison إذا **`uniqueFacets.size > 1`** **أو** وُجد شرط **`FIND`** ضمن الشروط النشطة. وإلا قد يُحمَّل الجدول عبر مسارات محلية (`loadCategoryData` / بيانات الفئة) دون نفس الحمولة المركّبة.

**Assertions:** راقب الشبكة بعد كل سيناريو في §8.3 وتأكد من ظهور الطلب (أو عدمه) كما هو متوقّع.

**ملاحظة تقنية:** مسار `executeMultiConditionSearch` يمرّر **`maxDepth: 1`** ثابتًا إلى `executeUnisonSearch`؛ بينما الدالة في `search-api.js` تستخدم افتراضيًا `2` عند الاستدعاء من أماكن أخرى — اختبر حسب المسار الفعلي للحدث.

### 8.3 سيناريوهات `performSearch` (E2E + API)

| # | السيناريو | الخطوات | التوقع |
|---|-----------|---------|--------|
| S1 | **FIND + كلمة** | اختر facet → اكتب نصًا → `.search-type-select` = find → انقر بحث | شرط FIND يُضاف؛ `POST /api/unison/search` مع `operator: FIND` و`keyword` = النص؛ تفعيل AND/OR/NOT في القائمة |
| S2 | **استعلام فارغ + facet** | احذف النص من الصندوق → بحث | يُضاف `FIND` بـ `keyword` المعادل لـ «الكل» (`*`) وتشغيل بحث متعدد الشروط؛ جدول يعرض كل البيانات المسموحة للfacet (مع segment إن وُجد) |
| S3 | **AND/OR/NOT بدون facet** | بدون تبويب فئة → غيّر المشغّل إلى AND واكتب نصًا → بحث | **لا** `POST`؛ Toast تحذير (`search.selectCategoryFirst` / نص مشابه) |
| S4 | **AND/OR/NOT بعد FIND مع استعلام فارغ** | بعد S1 أو S2 → مشغّل AND → صندوق فارغ → بحث | يُضاف شرط بـ `*`؛ استدعاء بحث متعدد |
| S5 | **AND/OR/NOT أول مرة (بدون شروط سابقة)** | امسح الشروط → مشغّل OR + كلمة | يُضاف تلقائيًا **FIND `*`** ثم الشرط OR (auto-FIND في `addSearchCondition`) |
| S6 | **AND/OR/NOT مع استعلام فارغ غير مسموح** | FIND موجود → AND لكن بدون نص حيث لا يُحوَّل لـ `*` | Toast خطأ (`search.enterSearchValue`)؛ إعادة المشغّل لـ FIND حسب الكود |
| S7 | **صفوف محددة + بحث بدون كتابة** | حدّد صفوفًا في الجدول (bulk) → اترك الصندوق فارغًا → بحث | مسار **bulk**: يُبنى `buildBulkSelectionCondition`؛ شروط تُستبدل؛ `executeMultiConditionSearch` — ليس نفس سلوك FIND العادي |
| S8 | **صفوف محددة + كتابة في الصندوق** | حدّد صفوفًا + اكتب كلمة بحث → بحث | **يُفضّل** البحث بالكلمة (`query.trim()` يمنع مسار bulk-only) |
| S9 | **مشغّلات داخل مربع النص (AST)** | اكتب استعلامًا يحتوي `AND`/`OR`/`NOT` أو أقواس أو `"quoted"` أو `field:value` | مسار **inline**: `loadCategoryData(category)` دون إضافة شرط dropdown فقط؛ يُفرغ الصندوق بعد التنفيذ؛ تحديث العداد |
| S10 | **اقتراحات + Enter** | أظهر `.search-suggestions` ببيانات → Enter | قد يُعرض جدول من `currentSuggestionsData` دون نفس تسلسل S1؛ يضبط `isSearchCommitted`؛ مسار فرعي لـ related counts |
| S11 | **مسح البحث** | `#clearSearchBtn` | إزالة الشروط/الحالة حسب التطبيق؛ إعادة تفعيل تعطيل AND/OR/NOT إن عاد للحالة الأولى |
| S12 | **ترتيب الشروط — FIND أولًا** | أعد ترتيب بناء الشروط في واجهة الباني (إن وُجد سحب) | في الطلب النهائي يجب أن يبقى أول عنصر `FIND` (الكود يعيد ترتيب `searches` في `executeMultiConditionSearch`) |
| S13 | **بحث عبر facetين** | FIND على dataset ثم AND على system (أو facet آخر) | `uniqueFacets.size > 1` → Unison إلزامي؛ حمولة تحتوي facetين على الأقل |
| S14 | **فلاتر + بحث** | طبّق فلاتر من `#filterPanel` ثم نفّذ بحث | كل عنصر في `searches` يحمل `filters` من `buildFiltersObject()` إن وُجدت |
| S15 | **استجابة Unison فارغة** | كلمة لا نتائج لها | الواجهة قد تُركّب `emptyResults` مع `totalCount` للحفاظ على «0 من Y» في الشريط الجانبي — تحقق من عدم كسر العدادات |
| S16 | **بحث متزامن (سباق)** | نفّذ بحثين سريعين متتاليين | `currentSearchToken`: الرد الأقدم يُتجاهل — الجدول يطابق آخر بحث فقط |
| S17 | **عمق الرسم** | (إن وُجد UI لـ maxDepth في بيئتك) | افتراض مسار الشروط المتعددة = 1؛ استدعاءات أخرى قد تمرّر 2 من `search-api.js` |

### 8.4 اختبارات API مباشرة للبحث (Contract)

| الاختبار | الطلب | التحقق |
|----------|--------|--------|
| C1 | `POST /api/unison/search` جسم صالح بشرط FIND واحد | `success: true`، `results` كائن، مفاتيح facet متوقعة |
| C2 | أول عنصر `operator: NOT` | `success: false` أو خطأ منطقي من الخادم (راجع `UnisonSearchService`) |
| C3 | `searches: []` | 400 أو خطأ في الرد |
| C4 | تضمين `searchFields` `{ "name": true, "ref": false }` | النتائج تتسق مع تقييد الأعمدة في البحث النصي |
| C5 | `hierarchicalOptions` لـ CAPABILITY | لا خطأ 500؛ سلوك أطفال حسب القيم |
| C6 | `indentLevel` على شروط متداخلة | الخادم يفسّر التجميع حسب التطبيق |

### 8.5 بحث ضبابي وإعدادات (`fuzzy`)

- `GET /UnisonSearch/config/fuzzy-search` (`search-config.js`, `search-debug.js`) يؤثر على سلوك البحث المحلي/التهيئة حسب الربط في بيئتك.
- اختبار: بعد تغيير الإعداد (إن وُجد UI)، نفّذ FIND وقارن النتائج قبل/بعد.

### 8.6 اختبارات سلبية خاصة بالبحث

| الحالة | التوقع |
|--------|--------|
| كلمات طويلة جدًا / يونيكود / RTL في `.search-main-input` | لا تعطل الصفحة؛ رد خادم أو نتائج فارغة بشكل مضبوط |
| محارف خاصة واقتباس في الوضع غير AST | سلوك `normalizeQuery` / الخادم — لا 500 متكرر |
| انقطاع الشبكة أثناء `POST /api/unison/search` | رسالة خطأ في الواجهة (`showErrorMessage` / toast)؛ إزالة حالة التحميل |

### 8.7 ربط مع ملفات أخرى

- **`search-fuzzy.js`:** منطق مطابقة ضبابية محلية إن وُجد.
- **`search-messages.js`:** رسائل المستخدم لنجاح/فشل البحث.
- **`saved-searches.js`:** تشغيل بحث محفوظ يعيد بناء الشروط ثم نفس مسار التنفيذ — اختبر «run» بعد §8.3.

---

*استخدم هذا الملف كخريطة اختبار واحدة لـ TestSprite؛ ربطه بالوثيقتين الأخريين يغطي الواجهة والخادم بالتفصيل. **§8** مخصّص لتوسيع تغطية اختبارات البحث فقط.*
