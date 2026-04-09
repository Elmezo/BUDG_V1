# تقرير مشاكل أسماء الجداول - UnisonSearch

## تاريخ: 2026-01-15

## ملخص تنفيذي

تم العثور على **18 جدول** مستخدم في الكود **غير موجود** في قاعدة البيانات.
من هذه **5 جداول** موجودة لكن **بترتيب معكوس** (reversed).

---

## المشاكل المكتشفة

### 1️⃣ جداول بترتيب معكوس (Reversed Table Names)

| الكود يستخدم | قاعدة البيانات تحتوي | الحالة |
|--------------|----------------------|---------|
| `policy_x_regulation` | `regulation_x_policy` | ⚠️ عكس |
| `process_x_glossary` | `glossary_x_process` | ⚠️ عكس |
| `project_x_glossary` | `glossary_x_project` | ⚠️ عكس |
| `regulator_x_regulation` | `regulation_x_regulator` | ⚠️ عكس |
| `regulatorytheme_x_regulation` | `regulation_x_regulatorytheme` | ⚠️ عكس |

**الأثر**: الاستعلامات ستفشل مع خطأ "table doesn't exist"

**الحل**: تغيير أسماء الجداول في الكود لتطابق قاعدة البيانات

---

### 2️⃣ جداول غير موجودة تماماً

| اسم الجدول | الحالة | ملاحظات |
|------------|---------|----------|
| `attribute_x_glossary` | ❌ غير موجود | تم تعليقه بالفعل |
| `dataset_x_businessarea` | ❌ غير موجود | تم تعليقه بالفعل |
| `dataset_x_glossary` | ❌ غير موجود | تم تعليقه بالفعل |
| `glossary_x_attribute` | ❌ غير موجود | تم تعليقه بالفعل |
| `glossary_x_dataset` | ❌ غير موجود | تم تعليقه بالفعل |

**الأثر**: تم معالجته في التحديث السابق (معلق بالفعل)

---

### 3️⃣ جداول إضافية غير موجودة

| اسم الجدول | استخدام محتمل |
|------------|---------------|
| `dataset_x_object` | غير معرف - قد يكون خطأ إملائي |
| `geography_x_objectxpeople` | يجب التحقق من وجوده |
| `legalentity_x_geography` | اسم مختلف: `legal_x_geography` موجود |
| `legalentity_x_objectxpeople` | اسم مختلف: `legal_x_objectxpeople` موجود |
| `object_x_ip` | غير معرف - جزء من اسم عمود؟ |
| `orgunit_x_objectxpeople` | غير موجود |
| `regulator_x_objectxpeople` | غير موجود |
| `regulatorytheme_x_objectxpeople` | غير موجود |

---

## الإجراءات المطلوبة

### ⚠️ عالية الأولوية

1. **إصلاح الجداول المعكوسة** (5 جداول):
   - `policy_x_regulation` → `regulation_x_policy`
   - `process_x_glossary` → `glossary_x_process`
   - `project_x_glossary` → `glossary_x_project`
   - `regulator_x_regulation` → `regulation_x_regulator`
   - `regulatorytheme_x_regulation` → `regulation_x_regulatorytheme`

### 🔍 متوسطة الأولوية

2. **التحقق من الجداول الأخرى**:
   - `legalentity_x_*` vs `legal_x_*`
   - `geography_x_objectxpeople`
   - `orgunit_x_objectxpeople`
   - `regulator_x_objectxpeople`
   - `regulatorytheme_x_objectxpeople`

---

## الملفات المتأثرة

1. `UnisonSearchService.java` - استعلامات مباشرة
2. `RelationshipManager.java` - تعريف العلاقات
3. أي ملفات أخرى في `unisonsearch` package

---

## ملاحظات

- تم بالفعل تعليق 5 جداول غير موجودة في التحديث السابق ✅
- الأولوية الآن: إصلاح الجداول المعكوسة (5 جداول)
- بعض الأسماء مثل `object_x_ip` قد تكون أجزاء من أسماء أعمدة وليست جداول

