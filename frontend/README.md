# Frontend - React Application

هذا المجلد يحتوي على تطبيق React للواجهة الأمامية (Bulk Upload Wizard).

---

## 📦 المحتويات | Contents

```
frontend/
├── src/                      # Source code
│   ├── components/          # React components
│   │   ├── BulkUploadWizard.jsx
│   │   ├── BulkJobsDashboard.jsx
│   │   ├── StepChooseFile.jsx
│   │   ├── StepMapColumns.jsx
│   │   ├── StepUploadProgress.jsx
│   │   └── ui/              # UI components
│   ├── services/            # API services
│   ├── utils/               # Utility functions
│   └── config/              # Configuration
├── package.json             # NPM dependencies
├── vite.config.js          # Vite build configuration
├── tailwind.config.js      # TailwindCSS configuration
├── postcss.config.js       # PostCSS configuration
└── index.html              # HTML template
```

---

## 🚀 التطوير | Development

### تثبيت Dependencies

```bash
cd frontend
npm install
```

### تشغيل Development Server

```bash
npm run dev
```

سيعمل على: `http://localhost:5173`

### بناء للإنتاج

```bash
npm run build
```

الملفات المُنتجة ستكون في: `../src/main/webapp/assets/js/bulk-upload/`

---

## 🛠️ البناء السريع | Quick Build

من **جذر المشروع**، استخدم:

### Windows:
```bash
.\scripts\build-wizard.bat
```

### Linux/Mac:
```bash
./scripts/build-wizard.sh
```

---

## 📝 المكونات الرئيسية | Main Components

### `BulkUploadWizard.jsx`
- المكون الرئيسي لـ Wizard
- إدارة الخطوات (Choose File → Map Columns → Upload Progress)
- إدارة البيانات بين الخطوات

### `BulkJobsDashboard.jsx`
- لوحة تحكم لعرض جميع الوظائف (Jobs)
- عرض الحالة والتقدم
- تحميل التقارير

### `StepChooseFile.jsx`
- اختيار الملف وتحميله
- اختيار نوع العملية (Add/Update/Delete)
- اختيار معالجة الأخطاء

### `StepUploadProgress.jsx`
- عرض تقدم الرفع في الوقت الفعلي
- WebSocket للتحديثات الفورية
- عرض الإحصائيات والأخطاء

---

## 🔧 التكوين | Configuration

### `vite.config.js`
```javascript
{
  outDir: '../src/main/webapp/assets/js',  // مجلد المخرجات
  input: {
    wizard: './src/main.jsx',              // Bulk Upload Wizard
    dashboard: './src/dashboard.jsx'       // Jobs Dashboard
  }
}
```

### `tailwind.config.js`
- تكوين TailwindCSS
- الألوان والأنماط المخصصة
- Responsive breakpoints

---

## 🎨 التصميم | Styling

التطبيق يستخدم:
- **TailwindCSS** - Utility-first CSS framework
- **Custom CSS** - في `src/index.css`
- **Dark Mode** - دعم كامل للوضع الداكن

---

## 🔌 API Integration

### Services (`src/services/apiService.js`)

```javascript
// رفع ملف
uploadBulkFile({ file, uploadOption, entity, userId })

// الحصول على حالة الوظيفة
getJobStatus(entity, jobId)

// تحميل التقرير
downloadReport(entity, jobId)
```

### WebSocket (`src/utils/websocket.js`)

```javascript
// الاتصال بـ WebSocket للتحديثات الفورية
connectToBulkUploadJob(jobId, callbacks)
```

### Auth (`src/utils/auth.js`)

```javascript
// الحصول على معرف المستخدم الحالي
getCurrentUserId()
ensureCurrentUserId()
```

---

## 📦 Dependencies

### Production
- `react` - React library
- `react-dom` - React DOM

### Development
- `vite` - Build tool
- `@vitejs/plugin-react` - React plugin for Vite
- `tailwindcss` - CSS framework
- `postcss` - CSS processor
- `autoprefixer` - CSS vendor prefixes

---

## 🏗️ سير العمل | Build Workflow

1. **Development**:
   ```bash
   npm run dev
   ```
   - Hot Module Replacement (HMR)
   - Fast refresh
   - Proxy to backend API

2. **Production Build**:
   ```bash
   npm run build
   ```
   - Minification
   - Tree shaking
   - Code splitting
   - CSS optimization

3. **Integration**:
   - الملفات المُنتجة تُنسخ إلى `src/main/webapp/assets/js/`
   - Tomcat يخدم الملفات الثابتة
   - صفحات HTML تحمّل JavaScript bundles

---

## 🐛 استكشاف الأخطاء | Troubleshooting

### خطأ: "Cannot find module"
```bash
rm -rf node_modules package-lock.json
npm install
```

### خطأ: "Port 5173 is already in use"
```bash
# غيّر المنفذ في vite.config.js
server: {
  port: 5174
}
```

### خطأ: "Failed to resolve entry"
```bash
# تأكد من وجود ملفات entry
ls src/main.jsx src/dashboard.jsx
```

---

## 📚 الموارد | Resources

- [React Documentation](https://react.dev/)
- [Vite Documentation](https://vitejs.dev/)
- [TailwindCSS Documentation](https://tailwindcss.com/)

---

## ⚠️ ملاحظات مهمة | Important Notes

1. **لا تعدّل الملفات في** `src/main/webapp/assets/js/bulk-upload/` مباشرة
   - هذه ملفات مُنتجة تلقائياً
   - عدّل في `frontend/src/` ثم اعمل build

2. **User Session**
   - التطبيق يحصل على User ID من `sessionStorage.currentUser`
   - تأكد من تسجيل الدخول قبل استخدام Bulk Upload

3. **WebSocket**
   - يعمل على نفس Host:Port للتطبيق
   - Fallback تلقائي إلى Polling إذا فشل WebSocket

---

للمزيد من المعلومات، راجع: [`../DEPLOYMENT_GUIDE.md`](../DEPLOYMENT_GUIDE.md)

