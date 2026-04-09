# دليل اختبار Fuzzy Search في Unison Search

## التغييرات المطبقة ✅

### 1. تحسين [`search-fuzzy.js`](src/main/webapp/assets/js/UnisionSearch/search-fuzzy.js)
- ✅ إضافة دعم **Word Order Flexibility** (السطور 127-133)
- ✅ الاحتفاظ بكل الميزات المفيدة (phonetic matching، highlight، sort)
- ✅ تحسين ترتيب التنفيذ للأداء الأمثل

### 2. إصلاح [`search-init.js`](src/main/webapp/assets/js/UnisionSearch/search-init.js)
- ✅ إصلاح مشكلة التوقيت بإضافة `await` (السطر 99)
- ✅ إضافة console logs للتحقق من تحميل الإعدادات

---

## خطوات الاختبار الشاملة

### التحضير الأولي

1. **امسح الـ cache في المتصفح:**
   - اضغط `Ctrl + Shift + Delete`
   - اختر "Cached images and files"
   - انقر Delete

2. **افتح صفحة البحث:**
   ```
   http://[your-domain]/search.html
   ```

3. **افتح Developer Console:**
   - اضغط `F12` أو `Ctrl + Shift + I`
   - انتقل إلى تبويب "Console"

4. **تحقق من رسائل التحميل:**
   يجب أن ترى:
   ```
   [INIT] Loading fuzzy search configuration...
   [Fuzzy Search Config] Fetched from server - enabled: true
   [Fuzzy Search Config] Set window.fuzzySearchEnabled to: true
   [INIT] Fuzzy search config loaded - enabled: true
   [INIT] initSearchSuggestions called
   ```

   ⚠️ **إذا كان `enabled: false`:**
   - تأكد من تفعيل Fuzzy Search من Admin Panel
   - اذهب إلى: Admin Panel > Customize and Configure > App Settings > Search Settings
   - فعّل "Enable Fuzzy Search"

---

## الاختبارات الوظيفية

### اختبار 1: Character Transposition (تبديل الحروف) 🔄

**الهدف:** التأكد من أن البحث يكتشف الحروف المتبادلة

| ما تكتبه | النتيجة المتوقعة | ملاحظات |
|----------|------------------|---------|
| `Cutsomer` | يظهر "Customer" في الاقتراحات | تبديل 't' و 'o' |
| `Sytsem` | يظهر "System" في الاقتراحات | تبديل 'y' و 's' |
| `Prjoect` | يظهر "Project" في الاقتراحات | تبديل 'j' و 'o' |
| `Polciy` | يظهر "Policy" في الاقتراحات | تبديل 'c' و 'i' |

**طريقة الاختبار:**
1. ابدأ الكتابة في مربع البحث
2. انظر إلى الاقتراحات المنسدلة
3. يجب أن تظهر النتيجة الصحيحة بعد ثانية واحدة

✅ **النجاح:** تظهر الاقتراحات الصحيحة رغم تبديل الحروف  
❌ **الفشل:** لا تظهر اقتراحات أو تظهر نتائج غير مطابقة

---

### اختبار 2: Typo Tolerance (تحمل الأخطاء) ⌨️

**الهدف:** التأكد من أن البحث يتحمل 1-2 خطأ إملائي

#### خطأ واحد (1 character error):

| ما تكتبه | النتيجة المتوقعة |
|----------|------------------|
| `logal` | "Legal" أو "Legal Entity" |
| `legl` | "Legal" |
| `dta` | "Data" أو "Dataset" |
| `sytem` | "System" |
| `prodct` | "Product" |

#### خطأين (2 character errors):

| ما تكتبه | النتيجة المتوقعة |
|----------|------------------|
| `logl` | "Legal" |
| `prjct` | "Project" |
| `systm` | "System" |
| `dsaet` | "Dataset" |

**ملاحظة:** الكلمات القصيرة (2-3 حروف) تسمح بخطأ واحد فقط  
**ملاحظة:** الكلمات الطويلة (4+ حروف) تسمح بخطأين

✅ **النجاح:** تظهر الاقتراحات الصحيحة رغم الأخطاء الإملائية  
❌ **الفشل:** لا تظهر اقتراحات

---

### اختبار 3: Word Order Flexibility (مرونة ترتيب الكلمات) 🔀

**الهدف:** التأكد من أن البحث يطابق الكلمات بأي ترتيب

| ما تكتبه | يطابق | مثال من البيانات |
|----------|--------|------------------|
| `Data Party` | `Party Data` | اسم entity أو dataset |
| `legal record` | `legal entity record` | اسم يحتوي على الكلمات |
| `System Main` | `Main System` | اسم نظام |
| `Business Legal` | `Legal Business Entity` | أي entity يحتوي على الكلمتين |
| `Customer Service` | `Service Customer Portal` | أي نص يحتوي على الكلمتين |

**طريقة الاختبار:**
1. اكتب كلمتين أو أكثر بترتيب معكوس
2. يجب أن تظهر النتائج التي تحتوي على نفس الكلمات بأي ترتيب

**مثال عملي:**
```
إذا كان لديك dataset اسمه: "Party Data Records"
وكتبت: "Data Party"
يجب أن يظهر في الاقتراحات ✅
```

✅ **النجاح:** تظهر النتائج التي تحتوي على الكلمات بأي ترتيب  
❌ **الفشل:** تظهر فقط النتائج بالترتيب الدقيق

---

### اختبار 4: Wildcard Support (دعم الرموز البدلة) ⭐

**الهدف:** التأكد من أن `*` يعمل في البداية أو النهاية

#### Wildcard في النهاية:

| ما تكتبه | يجب أن يطابق |
|----------|--------------|
| `leg*` | "Legal", "Legend", "Legacy", "Legislation" |
| `sys*` | "System", "Systematic", "Systems" |
| `data*` | "Data", "Dataset", "Database", "Dataflow" |
| `pol*` | "Policy", "Political", "Police" |

#### Wildcard في البداية:

| ما تكتبه | يجب أن يطابق |
|----------|--------------|
| `*al` | "Legal", "Final", "Equal", "Royal" |
| `*tem` | "System", "Item", "Stem" |
| `*set` | "Dataset", "Asset", "Subset" |

#### Wildcard في البداية والنهاية:

| ما تكتبه | يجب أن يطابق |
|----------|--------------|
| `*ata*` | "Data", "Dataset", "Database", "Metadata" |
| `*sys*` | "System", "Subsystem", "Ecosystem" |

**ملاحظة مهمة:** الـ wildcard يعمل فقط على الكلمة كاملة، ليس على جزء من كلمة في منتصف نص

✅ **النجاح:** تظهر جميع النتائج التي تطابق النمط  
❌ **الفشل:** لا تظهر نتائج أو تظهر نتائج خاطئة

---

## اختبارات متقدمة (اختيارية)

### اختبار مدمج: دمج عدة ميزات

| الاختبار | ما تكتبه | التوقع |
|----------|----------|---------|
| Typo + Word Order | `Logal Entty` | يطابق "Legal Entity" |
| Transposition + Wildcard | `Syt*` (تبديل y,s) | يطابق "System..." |
| Multi-word + Typo | `Busness Ara` | يطابق "Business Area" |

---

## التحقق من نجاح التحديث

### في Console يجب أن ترى:

```javascript
// عند تحميل الصفحة:
[INIT] Loading fuzzy search configuration...
[Fuzzy Search Config] Fetched from server - enabled: true
[INIT] Fuzzy search config loaded - enabled: true

// يمكنك أيضاً اختبار يدوياً:
> window.fuzzySearchEnabled
true

> fuzzyMatch("Customer", "cutsomer")  // transposition
true

> fuzzyMatch("Legal", "logal")  // typo
true

> fuzzyMatch("Party Data", "Data Party")  // word order
true

> fuzzyMatch("Legal Entity", "leg*")  // wildcard
true
```

---

## الإبلاغ عن المشاكل

إذا واجهت أي مشكلة، يرجى تسجيل:

1. **الخطأ المحدد:**
   - ما كتبته في البحث
   - ما توقعت أن يظهر
   - ما ظهر بالفعل

2. **رسائل Console:**
   - انسخ جميع الرسائل من Console
   - خاصة أي رسائل error باللون الأحمر

3. **إعدادات المتصفح:**
   - اسم المتصفح والإصدار
   - هل cache ممسوح؟

4. **بيئة الاختبار:**
   - هل أنت على local server أم production؟
   - هل fuzzy search مفعل في Admin Panel؟

---

## ملاحظات مهمة

- ⏱️ **السرعة:** الاقتراحات تظهر بعد 200ms من التوقف عن الكتابة
- 🔢 **الحد الأقصى:** يظهر حتى 10 اقتراحات كحد أقصى
- 🔤 **حساسية الحروف:** البحث غير حساس للحروف الكبيرة/الصغيرة
- 🌐 **اللغات:** يدعم العربي والإنجليزي (phonetic matching)
- ⚡ **الأداء:** البحث محسّن للأداء - يتحقق من الحالات الأسرع أولاً

---

## ملخص الميزات المدعومة

| الميزة | مدعومة؟ | ملاحظات |
|-------|---------|---------|
| ✅ Exact Match | نعم | أسرع طريقة |
| ✅ Wildcard (*) | نعم | في البداية أو النهاية |
| ✅ Word Order Flexibility | نعم | **جديد!** |
| ✅ Phonetic Matching | نعم | للأسماء متعددة اللغات |
| ✅ Character Transposition | نعم | تبديل حرفين متجاورين |
| ✅ Typo Tolerance (1-2 errors) | نعم | حسب طول الكلمة |
| ✅ Multi-language Support | نعم | عربي/إنجليزي |

---

## التحديث التالي (اختياري)

إذا أردت تحسينات إضافية في المستقبل:

- [ ] دعم wildcards في منتصف الكلمة (`le*al`)
- [ ] تحسين ترتيب النتائج (أكثر صلة أولاً)
- [ ] highlighting للكلمات المطابقة في الاقتراحات
- [ ] إحصائيات عن استخدام fuzzy search

---

تم إنشاء هذا الدليل: يناير 2026
