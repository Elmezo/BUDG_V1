# ملخص إصلاحات أسماء جداول العلاقات

## تاريخ: 2026-01-15

## المشاكل المكتشفة والمعالجة

تم اكتشاف **5 جداول** مستخدمة في الكود بترتيب معكوس (reversed) مقارنة بقاعدة البيانات الفعلية.

---

## الإصلاحات المنفذة

### 1️⃣ `process_x_glossary` → `glossary_x_process`

**قاعدة البيانات**: `glossary_x_process`
- الأعمدة: `Glossary_ID`, `Process_ID`

**الملفات المصلحة**:
- ✅ `UnisonSearchService.java` (السطر 6623)
  - تغيير الجدول + تغيير العمود من `process_id` إلى `Process_ID`
- ✅ `RelationshipManager.java` (السطر 539 + 784)
  - تغيير الجدول + تغيير الأعمدة من lowercase إلى PascalCase

---

### 2️⃣ `project_x_glossary` → `glossary_x_project`

**قاعدة البيانات**: `glossary_x_project`
- الأعمدة: `Project_ID`, `Glossary_ID`

**الملفات المصلحة**:
- ✅ `UnisonSearchService.java` (السطر 6610)
  - تغيير الجدول + تغيير العمود من `project_id` إلى `Project_ID`
- ✅ `RelationshipManager.java` (السطر 541 + 762)
  - تغيير الجدول + تغيير الأعمدة من lowercase إلى PascalCase
  - إلغاء تعليق العلاقة (كانت معلقة بافتراض أن الجدول غير موجود)

---

### 3️⃣ `policy_x_regulation` → `regulation_x_policy`

**قاعدة البيانات**: `regulation_x_policy`
- الأعمدة: `RegulationID`, `PolicyID` (بدون underscore)

**الملفات المصلحة**:
- ✅ `RelationshipManager.java` (السطر 658 + 668)
  - policy → regulation: من `Policy_ID` إلى `PolicyID`
  - regulation → policy: من `Regulation_ID` إلى `RegulationID`

---

### 4️⃣ `regulator_x_regulation` → `regulation_x_regulator`

**قاعدة البيانات**: `regulation_x_regulator`
- الأعمدة: `RegulationID`, `RegulatorID` (بدون underscore)

**الملفات المصلحة**:
- ✅ `UnisonSearchService.java` (السطر 7129)
  - تغيير الجدول + تغيير العمود من `Regulator_ID` إلى `RegulatorID`
- ✅ `RelationshipManager.java` (السطر 672 + 690)
  - regulation → regulator: من `Regulation_ID` إلى `RegulationID`
  - regulator → regulation: من `Regulator_ID` إلى `RegulatorID`

---

### 5️⃣ `regulatorytheme_x_regulation` → `regulation_x_regulatorytheme`

**قاعدة البيانات**: `regulation_x_regulatorytheme`
- الأعمدة: `Regulation_ID`, `RegulatoryTheme_ID`

**الملفات المصلحة**:
- ✅ `UnisonSearchService.java` (السطر 7064 + 7191)
  - تغيير اسم الجدول (الأعمدة بنفس الأسماء)
- ✅ `RelationshipManager.java` (السطر 670 + 682)
  - تغيير اسم الجدول (الأعمدة بنفس الأسماء)

---

## ملخص التغييرات

| المجموع | اسم الملف |
|---------|-----------|
| 4 إصلاحات | UnisonSearchService.java |
| 10 إصلاحات | RelationshipManager.java |
| **14 إصلاح كلي** | **المجموع** |

---

## التأثير

### قبل الإصلاح:
- ❌ الاستعلامات تفشل مع `table doesn't exist`
- ❌ العلاقات بين الجداول لا تعمل
- ❌ بعض العلاقات معلقة بافتراض أن الجداول غير موجودة

### بعد الإصلاح:
- ✅ جميع أسماء الجداول تطابق قاعدة البيانات
- ✅ أسماء الأعمدة صحيحة
- ✅ العلاقات المعلقة تم تفعيلها (process ↔ glossary, project ↔ glossary)
- ✅ لا توجد أخطاء في linter

---

## ملاحظات مهمة

1. **lowercase vs PascalCase**: تم تصحيح أسماء الأعمدة من lowercase إلى PascalCase حيث لزم الأمر
2. **Underscore**: بعض الجداول تستخدم `RegulationID` بدون underscore بدلاً من `Regulation_ID`
3. **العلاقات المفعلة**: تم إلغاء تعليق العلاقات التي كانت معلقة بافتراض خاطئ

---

## الجداول المتبقية غير الموجودة

تم التأكيد على أن هذه الجداول **غير موجودة فعلاً** وتم تعليقها في التحديث السابق:
- ❌ `dataset_x_businessarea`
- ❌ `glossary_x_dataset`
- ❌ `glossary_x_attribute`

---

## التحقق

✅ تم التحقق من:
- جميع أسماء الجداول مقابل `database/db/lite_clean.sql`
- جميع أسماء الأعمدة في كل جدول
- عدم وجود أخطاء في linter

