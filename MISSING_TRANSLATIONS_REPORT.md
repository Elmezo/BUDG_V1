# تقرير الترجمات الناقصة في لوحة التحكم (Admin Panel)

## ملخص المشكلة
تم اكتشاف **ترجمات ناقصة** لعناصر واجهة المستخدم ورسائل الخطأ في لوحة التحكم (Admin Panel)، خاصة في القوائم الفرعية وصفحات الإدارة.

---

## 1. ترجمات الـ Submenu الناقصة

### مفاتيح ناقصة تماماً من `ar.json`:

#### 1.1 Notification Rules
- **المفتاح**: `adminPanel.submenu.notificationRules`
- **الإنجليزية**: "Notification Rules"
- **العربية المقترحة**: "قواعد الإشعارات" أو "قواعد الإخطارات"
- **الملف**: `assets/js/admin-panel/main.js` (السطر 481)
- **الحالة**: ❌ ناقصة تماماً في ar.json

#### 1.2 Email Settings
- **المفتاح**: `adminPanel.submenu.emailSettings`
- **الإنجليزية**: "Email Settings"
- **العربية المقترحة**: "إعدادات البريد الإلكتروني" أو "إعدادات رسائل البريد"
- **الملف**: `assets/js/admin-panel/operational-management/email-settings.js`
- **الحالة**: ❌ ناقصة تماماً في ar.json

---

## 2. ترجمات الـ Content الناقصة (Hardcoded في الملفات الـ JavaScript)

### 2.1 في `notification-rules.js`:

| العنصر | الإنجليزية | الحالة |
|--------|----------|-------|
| العنوان الرئيسي | "NOTIFICATION RULES" | ❌ Hardcoded |
| زر الإضافة | "Add Rule" | ❌ Hardcoded |
| وصف المساعدة | رسائل مساعدة متعددة | ❌ Hardcoded |
| رسائل التحميل | "Loading rules..." | ❌ Hardcoded |
| رسائل الأخطاء | رسائل متعددة | ❌ Hardcoded |

### 2.2 في `email-settings.js`:

| العنصر | الإنجليزية | الحالة |
|--------|----------|-------|
| العنوان الرئيسي | "EMAIL SETTINGS" | ❌ Hardcoded |
| الحقول | "SMTP Host", "SMTP Port", إلخ | ❌ Hardcoded |
| رسائل التحقق | رسائل متعددة | ❌ Hardcoded |
| النصوص المساعدة | نصوص توضيحية | ❌ Hardcoded |

### 2.3 ملفات أخرى بها translatable strings:

- `data-onboarding-rules.js` - عناوين وزر وجداول
- `administrators-panel.js` - واجهات المسؤولين
- `manage-locks.js` - رسائل وعناوين إدارة الأقفال
- `locked-users.js` - رسائل المستخدمين المقفلين
- `ownership-transfer.js` - عناوين ورسائل
- `download-logs.js` - عناوين ورسائل التنزيل
- `import-migrated-data.js` - عناوين ورسائل الاستيراد

---

## 3. الترجمات الموجودة في submenu لكنها ناقصة details:

التالية موجودة في `submenu` لكنها قد تحتاج إلى ترجمات إضافية في محتوى الصفحات:

| المفتاح | الحالة الحالية |
|--------|----------|
| `dataOnboardingRules` | ✓ موجود |
| `administratorsPanel` | ✓ موجود |
| `manageLocks` | ✓ موجود |
| `lockedUsers` | ✓ موجود |
| `ownershipTransfer` | ✓ موجود |
| `downloadLogs` | ✓ موجود |
| `importMigratedData` | ✓ موجود |
| `changeLogo` | ✓ موجود |
| `customizeStyles` | ✓ موجود |
| `systemSettings` | ✓ موجود |
| `appSettings` | ✓ موجود |

---

## 4. رسائل الخطأ والتنبيهات الناقصة

الملفات المذكورة أعلاه تحتوي على عدد من رسائل الأخطاء والتنبيهات المحددة بصيغة صعبة (hardcoded):

- رسائل نجح التحفظ
- رسائل الأخطاء
- تحذيرات التحقق
- رسائل التأكيد

**التقدير**: أكثر من **50+ رسالة** غير مترجمة.

---

## الحل المقترح

### الخطوة 1: إضافة مفاتيح الـ Submenu

أضف إلى `ar.json` و `en.json` في قسم `adminPanel.submenu`:

```json
{
  "notificationRules": "قواعس الإشعارات",
  "emailSettings": "إعدادات البريد الإلكتروني"
}
```

### الخطوة 2: نقل الترجمات من Hardcoded إلى JSON

يجب نقل جميع النصوص الثابتة من ملفات `.js` إلى ملفات `.json` للترجمة.

### الخطوة 3: تحديث الملفات الـ JavaScript

تحديث صيغة الاستدعاء من:
```javascript
innerHTML = "NOTIFICATION RULES"
```
إلى:
```javascript
innerHTML = window.I18n ? window.I18n.t('adminPanel.operationalManagement.notificationRules.title') : 'NOTIFICATION RULES'
```

---

## أولويات الإصلاح

### 🔴 **أولوية قطيعة (Critical):**
- `adminPanel.submenu.notificationRules`
- `adminPanel.submenu.emailSettings`

### 🟠 **أولوية عالية (High):**
- جميع عناوين الصفحات الرئيسية
- أزرار رئيسية (Add, Save, Delete, إلخ)
- رسائل الأخطاء الشائعة

### 🟡 **أولوية متوسطة (Medium):**
- النصوص المساعدة (Tooltips)
- رسائل الحالة (Loading, Saving)
- صفحات الجداول الثانوية

---

## الملفات المتأثرة

```
src/main/webapp/assets/i18n/
  ├── en.json ✓ (يحتوي على الترجمات الإنجليزية)
  └── ar.json ❌ (ناقص الترجمات)

src/main/webapp/assets/js/admin-panel/
  ├── main.js (يشير إلى notificationRules لكنها غير معرفة)
  └── operational-management/
      ├── notification-rules.js ❌ (ترجمات hardcoded)
      ├── email-settings.js ❌ (ترجمات hardcoded)
      └── ... (ملفات أخرى بها ترجمات hardcoded)
```

---

## خطوات العمل

- [ ] إضافة مفاتيح submenu الناقصة في JSON
- [ ] استخراج جميع الترجمات من notification-rules.js
- [ ] استخراج جميع الترجمات من email-settings.js
- [ ] تحديث باقي ملفات الإدارة
- [ ] توحيد صيغة استدعاء الترجمات
- [ ] اختبار جميع الترجمات بالعربية

---

**آخر تحديث**: 2026-02-16
**الحالة**: 🔴 يحتاج معالجة فورية
