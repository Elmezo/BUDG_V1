# Scripts Directory

هذا المجلد يحتوي على جميع سكريبتات الإعداد والبناء للمشروع.

---

## Setup Scripts

### `setup.ps1`
**الوصف**: سكريبت PowerShell للإعداد التلقائي للمشروع

**الاستخدام**:
```powershell
# Windows - من جذر المشروع
.\scripts\setup.ps1 -Auto
```

**المهام**:
- إنشاء ملف `.env`
- توليد JWT Secret Key
- إعداد إعدادات قاعدة البيانات

### `setup.bat`
**الوصف**: ملف Batch لتشغيل `setup.ps1` بسهولة

**الاستخدام**:
```bash
# Windows - من جذر المشروع
.\scripts\setup.bat
```

### `setup-jwt-secret.ps1`
**الوصف**: توليد JWT Secret Key آمن

**الاستخدام**:
```powershell
.\scripts\setup-jwt-secret.ps1
```

### `env.example`
**الوصف**: قالب لملف `.env`

**الاستخدام**:
```bash
# انسخ القالب إلى الجذر
cp scripts/env.example .env
# ثم عدّل القيم في .env
```

---

## Build Scripts

### `build-wizard.bat`
**الوصف**: بناء React Frontend على Windows

**الاستخدام**:
```bash
# Windows - من جذر المشروع
.\scripts\build-wizard.bat
```

**المهام**:
1. التحقق من تثبيت Node.js و npm
2. تثبيت Dependencies (إذا لم تكن موجودة)
3. بناء React Application
4. نسخ الملفات إلى `src/main/webapp/assets/js/bulk-upload/`

### `build-wizard.sh`
**الوصف**: بناء React Frontend على Linux/Mac

**الاستخدام**:
```bash
# Linux/Mac - من جذر المشروع
./scripts/build-wizard.sh
```

**ملاحظة**: تأكد من إعطاء صلاحية التنفيذ:
```bash
chmod +x scripts/build-wizard.sh
```

---

## الترتيب الموصى به | Recommended Order

عند إعداد المشروع لأول مرة:

1. **Setup** (مرة واحدة):
   ```powershell
   .\scripts\setup.ps1 -Auto
   ```

2. **Build Frontend** (عند تعديل React):
   ```bash
   .\scripts\build-wizard.bat
   ```

3. **Build Backend** (بناء المشروع):
   ```bash
   .\mvnw.cmd clean package
   ```

4. **Start Python Service**:
   ```bash
   cd python
   python regulator_bulk_processor.py
   ```

5. **Deploy to Tomcat**:
   - انسخ `target/ROOT.war` إلى `tomcat/webapps/`
   - شغّل Tomcat

---

## ملاحظات مهمة | Important Notes

### Windows
- تأكد من تشغيل PowerShell كـ Administrator إذا لزم الأمر
- إذا ظهرت رسالة "Execution Policy"، استخدم:
  ```powershell
  Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
  ```

### Linux/Mac
- تأكد من إعطاء صلاحيات التنفيذ للسكريبتات:
  ```bash
  chmod +x scripts/*.sh
  ```

### Build Scripts
- تعمل السكريبتات من **جذر المشروع** فقط
- السكريبتات تدخل تلقائياً إلى مجلد `frontend/` لتنفيذ الأوامر
- بعد البناء، الملفات تُنسخ تلقائياً إلى المكان الصحيح

---

## استكشاف الأخطاء | Troubleshooting

### خطأ: "Node.js is not installed"
**الحل**: قم بتثبيت Node.js من https://nodejs.org/

### خطأ: "PowerShell script is not digitally signed"
**الحل**:
```powershell
Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process
```

### خطأ: "npm install failed"
**الحل**:
```bash
cd frontend
rm -rf node_modules package-lock.json
npm install
```

---

للمزيد من المعلومات، راجع: [`DEPLOYMENT_GUIDE.md`](../DEPLOYMENT_GUIDE.md)

