# Role Notifications Implementation - Dataset Only

## ✅ ما تم تنفيذه

تم تطبيق نظام إشعارات الأدوار على **Dataset فقط** كدليل مفهوم.

### 1. Database Migration
- **ملف**: `database/migrations/add_role_notification_fields.sql`
- **الوظيفة**: إضافة عمودين جديدين لجدول `workflow_notification`:
  - `object_id` - ID الخاص بالـ object (dataset)
  - `facet_type` - نوع الـ facet (مثل "Data Set")

**ملاحظة**: يجب تشغيل هذا الـ migration script على قاعدة البيانات قبل استخدام النظام.

### 2. Backend Implementation

#### أ. RoleNotificationService
- **الملف**: `src/main/java/com/example/budg_v2/service/RoleNotificationService.java`
- **الوظيفة**: 
  - إنشاء إشعارات UI عند تعيين stakeholder
  - إرسال بريد إلكتروني تلقائياً
  - الحصول على أسماء الأدوار والـ facets

#### ب. Integration Points
تم دمج الإشعارات في نقطتين:

1. **DatasetServlet.assignCreatorRole** (عند إنشاء dataset جديد)
   - عند إنشاء dataset، يتم تعيين creator role تلقائياً
   - يتم إنشاء إشعار للمستخدم المعين

2. **DatasetStakeholderServlet.addStakeholder** (عند إضافة stakeholder يدوياً)
   - عند إضافة stakeholder جديد عبر واجهة التعديل
   - يتم إنشاء إشعار للمستخدم المضاف

#### ج. API Endpoints
- **الملف**: `src/main/java/com/example/budg_v2/RoleNotificationServlet.java`
- **Endpoints**:
  - `PUT /api/role-notifications/accept-all` - الموافقة على جميع الأدوار
  - `GET /api/role-notifications/unaccepted-count` - عدد الأدوار غير المقبولة

### 3. Frontend Implementation

#### Notification Panel Updates
- **الملف**: `src/main/webapp/assets/js/notification-panel.js`
- **التحسينات**:
  - عرض إشعارات الأدوار بتنسيق خاص
  - جعل اسم الـ object رابطاً قابلاً للنقر
  - إضافة أزرار "All Roles" و"Accept and Clear All" في تبويب Roles
  - دالة مساعدة لتوليد روابط الـ objects

### 4. Email Notifications

عند تعيين stakeholder، يتم إرسال بريد إلكتروني يحتوي على:
- اسم الدور
- اسم الـ Facet والـ Object
- رابط لعرض الـ Object
- رابط للموافقة على الدور

## 📋 خطوات التشغيل

1. **تشغيل Migration Script**:
   ```sql
   -- تشغيل الملف: database/migrations/add_role_notification_fields.sql
   ```

2. **إعادة تشغيل التطبيق**:
   - إعادة بناء المشروع (Maven build)
   - إعادة تشغيل الخادم

3. **اختبار النظام**:
   - إنشاء dataset جديد → يجب أن يظهر إشعار للمستخدم المعين
   - إضافة stakeholder يدوياً → يجب أن يظهر إشعار
   - التحقق من البريد الإلكتروني

## 🔍 كيفية الاختبار

### اختبار 1: إنشاء Dataset
1. قم بإنشاء dataset جديد
2. تحقق من:
   - ظهور إشعار في تبويب "Roles" في لوحة الإشعارات
   - وصول بريد إلكتروني للمستخدم المعين
   - صحة محتوى الإشعار (اسم الدور، اسم الـ Dataset)

### اختبار 2: إضافة Stakeholder يدوياً
1. افتح dataset موجود
2. أضف stakeholder جديد
3. تحقق من:
   - ظهور إشعار جديد
   - وصول بريد إلكتروني

### اختبار 3: الموافقة على الأدوار
1. افتح تبويب "Roles" في لوحة الإشعارات
2. اضغط على "Accept and Clear All"
3. تحقق من:
   - تحديث جميع الأدوار إلى "Yes"
   - اختفاء الإشعارات

### اختبار 4: التنقل إلى Responsibilities
1. من تبويب "Roles" في لوحة الإشعارات
2. اضغط على "All Roles"
3. تحقق من:
   - الانتقال إلى صفحة People > Responsibilities

## 📝 ملاحظات مهمة

1. **Dataset فقط**: النظام مطبق حالياً على Dataset فقط. بعد التأكد من عمله بشكل صحيح، يمكن تطبيقه على باقي الـ facets.

2. **Email Links**: الروابط في البريد الإلكتروني نسبية حالياً. إذا كان التطبيق يعمل على domain معين، قد تحتاج لتحديث `getObjectViewLink` في `RoleNotificationService` لاستخدام base URL كامل.

3. **Error Handling**: جميع عمليات إنشاء الإشعارات محمية بـ try-catch ولا تفشل العملية الرئيسية إذا فشل إنشاء الإشعار.

## 🚀 التطبيق على باقي الـ Facets

بعد التأكد من عمل النظام بشكل صحيح على Dataset، يمكن تطبيقه على باقي الـ facets بنفس الطريقة:

1. **System**: 
   - إضافة في `SystemServlet.assignCreatorRole`
   - إضافة في `SystemStakeholderServlet.addStakeholder`

2. **Glossary**:
   - إضافة في `GlossaryServlet` (إذا كان موجود)
   - إضافة في `GlossaryStakeholderServlet.addStakeholder`

3. **Process, Project, Policy, etc.**:
   - نفس النمط لكل facet

**النمط المستخدم**:
```java
// بعد إنشاء stakeholder بنجاح
try {
    String objectName = getObjectName(objectId);
    if (objectName != null) {
        RoleNotificationService notificationService = new RoleNotificationService();
        notificationService.createRoleNotification(
            "Facet Type", objectId, objectName, roleId, userId, objectXPeopleId
        );
    }
} catch (Exception e) {
    // Log error but don't fail main operation
}
```

## ✅ Checklist قبل التطبيق على باقي الـ Facets

- [ ] Migration script تم تشغيله بنجاح
- [ ] إنشاء Dataset يعمل ويظهر الإشعارات
- [ ] إضافة Stakeholder يدوياً يعمل ويظهر الإشعارات
- [ ] البريد الإلكتروني يصل بشكل صحيح
- [ ] أزرار "Accept and Clear All" و "All Roles" تعمل
- [ ] روابط الـ objects تعمل بشكل صحيح
- [ ] لا توجد أخطاء في logs
