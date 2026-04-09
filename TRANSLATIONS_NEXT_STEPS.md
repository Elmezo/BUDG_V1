# توصيات لإكمال عملية الترجمة

## 📋 الخطوات المقترحة (Priority Order)

### المرحلة 1: تحديث الملفات العالية الأولوية (HIGH PRIORITY)

#### 1.1 تحديث `notification-rules.js`
استبدل جميع النصوص الثابتة بمفاتيح ترجمة:

```javascript
// قبل:
<h5 style="margin: 0; color: white; font-weight: bold;">NOTIFICATION RULES</h5>

// بعد:
const header = window.I18n ? window.I18n.t('adminPanel.operationalManagement.notificationRules.header') : 'NOTIFICATION RULES';
<h5 style="margin: 0; color: white; font-weight: bold;">${header}</h5>
```

**الترجمات المطلوبة**:
- `adminPanel.operationalManagement.notificationRules.header` → "NOTIFICATION RULES"
- `adminPanel.operationalManagement.notificationRules.addRule` → "Add Rule"
- `adminPanel.operationalManagement.notificationRules.module` → "Module"
- `adminPanel.operationalManagement.notificationRules.eventType` → "Event Type"
- ... و غيرها (تم إضافتها في JSON بالفعل ✅)

#### 1.2 تحديث `email-settings.js`
استبدل جميع النصوص الثابتة بمفاتيح ترجمة:

```javascript
// قبل:
<h5 style="margin: 0; color: white; font-weight: bold;">EMAIL SETTINGS</h5>

// بعد:
const header = window.I18n ? window.I18n.t('adminPanel.operationalManagement.emailSettings.header') : 'EMAIL SETTINGS';
<h5 style="margin: 0; color: white; font-weight: bold;">${header}</h5>
```

---

### المرحلة 2: تحديث الملفات المتوسطة الأولوية

#### 2.1 `administrators-panel.js`
- تحديث عناوين LDAP Sync
- تحديث رسائل المزامنة
- تحديث أزرار إضافة/إزالة المسؤولين

#### 2.2 `manage-locks.js`
- تحديث عناوين الجداول
- تحديث رسائل التأكيد
- تحديث رسائل النجاح/الأخطاء

#### 2.3 `locked-users.js`
- تحديث عناوين الجداول
- تحديث رسائل فتح القفل
- تحديث رسائل التنبيه

---

### المرحلة 3: تحديث الملفات المنخفضة الأولوية

#### 3.1 `change-logo.js`
#### 3.2 `customize-styles.js`
#### 3.3 `app-settings.js`
#### 3.4 ملفات الإدارة الأخرى

---

## 🔍 اختبار الترجمات

### خطوات الاختبار:

1. **اختبار الإنجليزية**:
   - افتح لوحة التحكم بلغة إنجليزية
   - تحقق من أن جميع النصوص تظهر بشكل صحيح

2. **اختبار العربية**:
   - غيّر لغة التطبيق للعربية
   - اضحك على لوحة التحكم
   - تحقق من:
     - ✅ ظهور جميع القوائم بالعربية
     - ✅ الترجمات يمينية إلى يسار (RTL)
     - ✅ رسائل الخطأ بالعربية
     - ✅ رسائل النجاح بالعربية
     - ✅ الرسائل المساعدة بالعربية

3. **اختبار التبديل بين اللغات**:
   - تبديل من الإنجليزية إلى العربية والعكس
   - تحقق من التحديث الفوري دون إعادة تحميل (إذا كان المتوقع)

---

## 📐 نمط موحد للترجمات

جميع الترجمات الجديدة تتبع النمط التالي:

```
adminPanel
├── submenu (أسماء عناصر القائمة الجانبية)
├── navigation (أسماء الأقسام الرئيسية)
├── common (نصوص متكررة)
├── messages (رسائل عامة)
├── operatingModel (قسم النموذج التشغيلي)
├── metaModel (قسم النموذج الفوقي)
├── operational (قسم الإدارة التشغيلية)
│   ├── notificationRules
│   ├── emailSettings
│   ├── administratorsPanel
│   └── ... (عناصر أخرى)
└── customize (قسم التخصيص)
    ├── changeLogo
    ├── customizeStyles
    └── appSettings
```

---

## 📚 المراجع والموارد

### ملفات التكوين الحالية:
- [en.json](src/main/webapp/assets/i18n/en.json)
- [ar.json](src/main/webapp/assets/i18n/ar.json)
- [i18n.js](src/main/webapp/assets/i18n/i18n.js)

### موارد الترجمة:
- [MISSING_TRANSLATIONS_REPORT.md](MISSING_TRANSLATIONS_REPORT.md) - تفاصيل الترجمات الناقصة
- [TRANSLATIONS_FIX_SUMMARY.md](TRANSLATIONS_FIX_SUMMARY.md) - ملخص الإصلاحات

---

## 🎯 معايير النجاح

- [ ] جميع عناصر menu تظهر بالعربية
- [ ] جميع عناوين الصفحات مترجمة
- [ ] جميع الأزرار والتسميات مترجمة
- [ ] جميع رسائل الخطأ بالعربية
- [ ] جميع رسائل النجاح بالعربية
- [ ] الواجهة RTL صحيحة عند تبديل اللغة
- [ ] لا توجد نصوص hardcoded في واجهات المسؤولين

---

## 💡 نصائح مهمة

### 1. استخدام المفترض مع النصوص الثابتة:
```javascript
const text = window.I18n ? window.I18n.t('key.path') : 'Fallback English Text';
```

### 2. التعامل مع المتغيرات في الترجمات:
```json
{
  "messages": {
    "error": "Error: {errorMessage}"
  }
}
```

```javascript
window.I18n.t('messages.error', { errorMessage: error.message })
```

### 3. اختبار الترجمات المفقودة:
- افتح console وتحقق من التحذيرات
- ابحث عن: `[i18n] Translation key not found`

---

## ⚠️ ملاحظات هامة

1. **التحديثات الحالية**: تم تحديث ملفات JSON فقط
2. **ملفات JS**: تحتوي على نصوص hardcoded لكنها تحتوي على fallback بالإنجليزية
3. **المفاتيح الجديدة**: جميع مفاتيح الترجمة الجديدة موجودة في JSON والآن جاهزة للاستخدام

---

**آخر تحديث**: 2026-02-16  
**المرحلة التالية**: تحديث ملفات JavaScript لاستخدام مفاتيح الترجمة
