# Dataset Mutually Exclusive Fields Update

## Summary
تم تحديث Dataset Bulk Update ليتوافق مع BUDG behavior حيث أن System Short Name و Glossary Name و Segment هي حقول متعارضة (mutually exclusive) - يمكن تحديث واحد فقط في كل مرة.

## Changes Made

### 1. Backend - BulkUpdateDefinitionConfig.java

#### Updated Dataset Definitions
تم تحديث Dataset definitions لتتضمن فقط الحقول المطلوبة:
- ✅ **BUDG Viewing** (`axon_viewing`) - LOOKUP
- ✅ **Type** (`type`) - LOOKUP  
- ✅ **System Short Name** (`system_short_name`) - REFERENCE → Column: `MasterSource`
- ✅ **Lifecycle** (`lifecycle`) - LOOKUP
- ✅ **BUDG Status** (`axon_status`) - LOOKUP
- ✅ **Glossary Name** (`glossary_name`) - REFERENCE → Column: `glossary`
- ✅ **Segment** (`segment`) - REFERENCE

#### Updated Mutually Exclusive Fields Set
```java
public static final Set<String> DATASET_MUTUALLY_EXCLUSIVE_FIELDS = Set.of(
    "system_short_name", "glossary_name", "segment"
);
```

### 2. Backend - BulkUpdateService.java

#### Updated Validation
- ✅ `checkMutuallyExclusiveFields()` يتحقق من أن لا يتم تحديث أكثر من حقل واحد من الحقول المتعارضة
- ✅ عند تحديث أحد الحقول المتعارضة، يتم مسح الحقلين الآخرين تلقائياً:
  - عند تحديث `system_short_name` → يتم مسح `glossary` و `segment`
  - عند تحديث `glossary_name` → يتم مسح `MasterSource` و `segment`
  - عند تحديث `segment` → يتم مسح `MasterSource` و `glossary`

### 3. Frontend - bulk-update.js

#### Added Validation Function
```javascript
function validateMutuallyExclusiveSelections(updates) {
    // Validates that only one of system_short_name, glossary_name, segment is selected
}
```

#### Updated handleValueChange()
- ✅ يمنع اختيار حقل متعارض إذا كان حقل آخر متعارض محدد بالفعل
- ✅ يعرض رسالة خطأ واضحة للمستخدم
- ✅ يعيد تعيين الحقل إلى القيمة السابقة

#### Updated executeBulkUpdate()
- ✅ يتحقق من الحقول المتعارضة قبل إرسال الطلب للـ backend
- ✅ يعرض رسالة خطأ إذا تم اختيار أكثر من حقل متعارض

#### Updated Lookup API Methods
تم تحديث `getLookupApiMethod()` و `getLookupApiEndpoint()` لدعم الحقول الجديدة:
- `type` → `getDatasetTypeList()` أو `/api/dataset-type/list`
- `system_short_name` → `getSystemsList()` أو `/api/system/list`
- `glossary_name` → `getGlossaryList()` أو `/api/glossary/list`

## Testing Checklist

### ✅ Frontend Validation
- [ ] محاولة اختيار System Short Name + Glossary Name → يجب منع و عرض رسالة خطأ
- [ ] محاولة اختيار System Short Name + Segment → يجب منع و عرض رسالة خطأ
- [ ] محاولة اختيار Glossary Name + Segment → يجب منع و عرض رسالة خطأ
- [ ] اختيار System Short Name فقط → يجب السماح
- [ ] اختيار Glossary Name فقط → يجب السماح
- [ ] اختيار Segment فقط → يجب السماح

### ✅ Backend Validation
- [ ] إرسال طلب مع System + Glossary → يجب رفض من الـ backend
- [ ] إرسال طلب مع System + Segment → يجب رفض من الـ backend
- [ ] إرسال طلب مع Glossary + Segment → يجب رفض من الـ backend
- [ ] إرسال طلب مع System فقط → يجب قبول و مسح Glossary و Segment
- [ ] إرسال طلب مع Glossary فقط → يجب قبول و مسح System و Segment
- [ ] إرسال طلب مع Segment فقط → يجب قبول و مسح System و Glossary

### ✅ Database Updates
- [ ] عند تحديث System Short Name → التحقق من أن `MasterSource` تم تحديثه و `glossary` تم مسحه
- [ ] عند تحديث Glossary Name → التحقق من أن `glossary` تم تحديثه و `MasterSource` تم مسحه
- [ ] عند تحديث Segment → التحقق من أن Segment تم تحديثه و `MasterSource` و `glossary` تم مسحهما

## Error Messages

### Frontend
- "Cannot update the System, Glossary, and Segment fields at the same time for a dataset. Please clear the other field first."

### Backend
- "Cannot update the System, Glossary, and Segment fields at the same time for a data set."

## Column Mappings

| Field ID | Display Name | Column Name | Type |
|----------|--------------|-------------|------|
| `system_short_name` | System Short Name | `MasterSource` | REFERENCE |
| `glossary_name` | Glossary Name | `glossary` | REFERENCE |
| `segment` | Segment | (handled via SegmentDAO) | REFERENCE |

## Notes

- الحقول المتعارضة يتم التحقق منها في كل من Frontend و Backend لضمان الأمان
- عند تحديث أحد الحقول المتعارضة، يتم مسح الحقلين الآخرين تلقائياً في الـ database
- Segment يتم التعامل معه بشكل خاص عبر `SegmentDAO.assignObjectToSegment()`

