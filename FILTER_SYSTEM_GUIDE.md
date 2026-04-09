# نظام الفلتر المتقدم في Unison Search

## نظرة عامة

تم تنفيذ نظام فلتر متقدم وديناميكي يدعم أنواع مختلفة من الفلاتر حسب نوع الـ Facet.

## المميزات

### 1. فلاتر ديناميكية حسب الـ Facet
كل Facet له مجموعة فلاتر خاصة به:

**Dataset:**
- Lifecycle (Dropdown)
- Type (Dropdown)
- Status (Dropdown)
- Axon Status (Dropdown)
- Created Date (Date Range)
- Last Updated (Date Range)
- Last Approved Date (Date Range)
- Created By (People Search)

**Glossary:**
- Lifecycle
- Status
- Classification
- Created Date
- Last Updated
- Created By

**Process:**
- Lifecycle
- Status
- Type
- Classification
- Created Date
- Last Updated
- Created By

**Policy:**
- Lifecycle
- Status
- Type
- Created Date
- Last Updated
- Created By

**Capability:**
- Lifecycle
- Status
- Classification
- Type
- Created Date
- Last Updated

**System:**
- Lifecycle
- Status
- Classification
- Type
- Created Date
- Last Updated

### 2. أنواع الفلاتر

#### Dropdown Filters (مع Checkboxes)
- تعرض قيم من جداول الـ lookup في قاعدة البيانات
- تدعم اختيار قيم متعددة
- تحتوي على search box لتصفية القيم
- مثال: Lifecycle, Status, Type

#### Date Range Filters
- تسمح باختيار تاريخ من (FROM) وإلى (TO)
- Date pickers مع أيقونات تقويم
- مثال: Created Date, Last Updated

#### People Search Filters
- بحث ديناميكي عن المستخدمين
- عرض النتائج في dropdown
- إمكانية إضافة أكثر من شخص
- عرض الأشخاص المحددين كـ chips
- مثال: Created By, Last Updated By

### 3. الفلاتر الهرمية (Hierarchical Filters)

للـ Facets الهرمية (Glossary, Process, Policy, Capability):

**Include Children:**
- Do not include Children (افتراضي)
- Include Immediate Children (مستوى واحد فقط)
- Include all Children (جميع المستويات)

**Apply Filters to Children:**
- Do not apply filters (عرض جميع الأبناء بدون فلترة)
- Apply the filter above (تطبيق نفس الفلاتر على الأبناء)

## كيفية الاستخدام

### 1. فتح لوحة الفلتر
اضغط على زر "Filter" في شريط البحث

### 2. إضافة فلتر جديد
1. اضغط على "Add new filter"
2. اختر نوع الفلتر من القائمة المنسدلة
3. سيظهر صف جديد بالفلتر المختار

### 3. تكوين الفلتر

**Dropdown Filter:**
1. اضغط على "Select options"
2. استخدم search box إذا كانت القيم كثيرة
3. علّم على القيم التي تريدها
4. الزر سيُحدّث ليعرض عدد القيم المحددة

**Date Range Filter:**
1. حدد تاريخ البداية (FROM)
2. حدد تاريخ النهاية (TO)
3. يمكنك تحديد واحد فقط أو الاثنين

**People Search Filter:**
1. ابدأ الكتابة في صندوق البحث
2. ستظهر قائمة بالمستخدمين المطابقين
3. اضغط على اسم المستخدم لإضافته
4. يمكنك إضافة أكثر من مستخدم
5. لحذف مستخدم، اضغط على × في الـ chip

### 4. إضافة فلاتر متعددة
كرر العملية لإضافة فلاتر إضافية

### 5. تطبيق الفلاتر
اضغط على زر "Apply Filters" في أسفل اللوحة

### 6. مسح جميع الفلاتر
اضغط على زر "Clear All" لحذف جميع الفلاتر

## البنية التقنية

### Backend

#### API Endpoints

**GET /UnisionSearch/api/filter-fields**
- Parameters: `facetId`
- Returns: قائمة بالفلاتر المتاحة للـ facet
- Example: `/UnisionSearch/api/filter-fields?facetId=DATASET`

**GET /UnisionSearch/api/filter-values/{fieldId}**
- Parameters: `facetId`
- Returns: القيم المتاحة لفلتر معين
- Example: `/UnisionSearch/api/filter-values/lifecycle?facetId=DATASET`

#### Java Classes

- `FilterField.java` - Model class للفلتر
- `FilterType.java` - Enum لأنواع الفلاتر
- `FilterMetadataConfig.java` - Configuration لفلاتر كل facet
- `FilterFieldsServlet.java` - Servlet لإرجاع الفلاتر المتاحة
- `FilterValuesServlet.java` - Servlet لإرجاع قيم الفلتر
- `UnisonSearchService.java` - معالجة الفلاتر في البحث

### Frontend

#### JavaScript Files

- `search-filters.js` - إدارة الفلاتر (جديد)
- `search-input.js` - تكامل مع نظام البحث (محدّث)

#### Key Functions

**في search-filters.js:**
- `loadFilterFields(facetId)` - تحميل الفلاتر المتاحة
- `addFilterRow(filterField)` - إضافة صف فلتر جديد
- `createDropdownValueSelector()` - إنشاء dropdown مع checkboxes
- `createDateRangeValueSelector()` - إنشاء date range picker
- `createPeopleValueSelector()` - إنشاء people search
- `buildFiltersObject()` - بناء object الفلاتر للـ backend
- `clearAllFilters()` - مسح جميع الفلاتر

**في search-input.js:**
- `applyFiltersAndSearch()` - تطبيق الفلاتر وتنفيذ البحث
- `updateHierarchicalFilterVisibility()` - إظهار/إخفاء الفلاتر الهرمية

## إضافة Facet جديد

لإضافة فلاتر لـ facet جديد:

1. افتح `FilterMetadataConfig.java`
2. أضف entry جديد في الـ static block:

```java
List<FilterField> myFacetFilters = new ArrayList<>();
myFacetFilters.add(new FilterField("lifecycle", "Lifecycle", FilterType.DROPDOWN, "lifecycle_status", "Lifecycle"));
myFacetFilters.add(new FilterField("type", "Type", FilterType.DROPDOWN, "my_facet_type", "Type"));
myFacetFilters.add(new FilterField("createdDate", "Created Date", FilterType.DATE_RANGE, null, "CreateDatetime"));
FACET_FILTERS.put("MY_FACET", myFacetFilters);
```

## ملاحظات مهمة

1. **Column Names:** تأكد من أن أسماء الأعمدة في `fieldName` تطابق أسماء الأعمدة الفعلية في قاعدة البيانات
2. **Lookup Tables:** جداول الـ lookup يجب أن تحتوي على `ID` و `PrimaryName` و `DeletedDatetime`
3. **People Search:** يتطلب endpoint `/api/people/search` (قد يحتاج إنشاء إذا لم يكن موجوداً)
4. **Performance:** القيم تُحمل عند الطلب (lazy loading) لتحسين الأداء
5. **Caching:** يمكن إضافة caching للقيم المستخدمة بكثرة

## أمثلة

### مثال 1: فلترة Dataset بـ Lifecycle
1. افتح Filter panel
2. Add new filter → Lifecycle
3. Select options → Approved, Draft
4. Apply Filters
5. النتائج ستعرض فقط Datasets بـ Lifecycle = Approved أو Draft

### مثال 2: فلترة Process بـ Created Date
1. Add new filter → Created Date
2. FROM: 2024-01-01
3. TO: 2024-12-31
4. Apply Filters
5. النتائج ستعرض Processes المنشأة في 2024

### مثال 3: فلترة Glossary مع Hierarchical Options
1. Add new filter → Lifecycle → Approved
2. Include Immediate Children
3. Apply the filter above
4. Apply Filters
5. النتائج ستعرض Glossary items الـ Approved مع أبنائهم المباشرين الـ Approved أيضاً

## Troubleshooting

### الفلاتر لا تظهر في "Add new filter"
- تحقق من Console للرسائل: `[Filters] Loaded filter fields`
- تأكد من أن الـ facet موجود في `FilterMetadataConfig`
- تحقق من أن الـ API endpoint يعمل

### القيم لا تُحمل في Dropdown
- تحقق من اسم جدول الـ lookup في `FilterMetadataConfig`
- تأكد من وجود الجدول في قاعدة البيانات
- تحقق من Console للرسائل: `[Filters] Loading values for: {fieldId}`

### Apply Filters لا يعمل
- تحقق من Console للرسائل: `[Filters] Applying filters and executing search`
- تأكد من وجود search condition أو اختيار filter واحد على الأقل
- افحص Network tab للتأكد من إرسال الـ request

## التطوير المستقبلي

- إضافة filter types جديدة (Number range, Boolean, etc.)
- إضافة Save Filter Presets
- إضافة Filter History
- تحسين Performance مع Caching
- إضافة Filter Analytics
