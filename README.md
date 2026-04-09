# BUDG_V6 + V7 — Business Data Governance Platform 

---

## English

### Description

**BUDG** is a business data governance platform for glossaries, datasets, attributes, systems, policies, and processes, with governance workflows, change management, and audit traceability.

This codebase is a **Java WAR** deployed on **Apache Tomcat 11**, backed by **MySQL** and **Elasticsearch** for search/indexing. The main web UI lives under `src/main/webapp`. A **Bulk Upload** experience is built with **React + Vite** in `frontend/`. An optional **Python (FastAPI)** service validates Excel files before bulk insert.

### Requirements

| Component | Version / notes |
|-----------|-----------------|
| **Java** | JDK 17 |
| **Maven** | 3.6+ (build) |
| **Apache Tomcat** | 11.x (deploy `ROOT.war`) |
| **Database** | MySQL 8.0+ or compatible MariaDB |
| **Elasticsearch** | 8.14.x (aligned with `elasticsearch-java` in `pom.xml`) |
| **Node.js** | 18+ (for developing/building the Bulk Upload Wizard only) |
| **Python** | 3.10+ (optional — bulk validation service) |

### Project layout (summary)

- **`src/main/java`** — Server layer (Jakarta Servlet 6, REST, JWT, LDAP integration, etc.).
- **`src/main/webapp`** — Main UI (HTML/CSS/JS); **Swagger UI** at `/swagger-ui.html`.
- **`frontend/`** — React (Vite) Bulk Upload Wizard; production build outputs into `src/main/webapp`.
- **`python/`** — `bulk_validation_service.py` (FastAPI) and per-entity processors.
- **`pom.xml`** — Builds the WAR as **`ROOT.war`** (root context `/`).

### Quick start (development)

**1) Database and environment**

- Create a database and configure connectivity (documentation often uses DB name `project`).
- Copy the template from `src/main/resources/.env` to **`[Tomcat]/bin/.env`** (or set the same variable names in your environment).  
  **Do not commit real `.env` files to Git.**

> Backup files, SQL migrations, and execution order are described in **[`VM_DEPLOYMENT_GUIDE.md`](VM_DEPLOYMENT_GUIDE.md)**. Files such as `projectbackup.sql` and a `database/` folder may ship with deployment packages and are not always present in the repository.

**2) Backend build**

From the repository root:

```bash
mvn clean package -DskipTests
```

Output: **`target/ROOT.war`**. Copy it to Tomcat 11 `webapps/`, or use your deployment setup (e.g. Cargo Maven with `CATALINA_HOME`).

> This is **not** Spring Boot; do **not** use `spring-boot:run`.

**3) Bulk Upload UI (React) — local dev**

```bash
cd frontend
npm install
npm run dev
```

The dev server typically runs at **`http://localhost:5173`**. See **`frontend/README.md`** for production build steps.

**4) Python validation service (optional — when using bulk upload)**

```bash
cd python
pip install -r requirements.txt
# Run as documented for the FastAPI app (e.g. uvicorn on the configured port)
```

### Root-level tooling

- **`npm run i18n:check`** (after `npm install` at the repo root if needed) — checks i18n keys between `en.json` and `ar.json`.

### API documentation (Swagger)

After the app is running on Tomcat:

**`http://localhost:<port>/swagger-ui.html`**

`<port>` is your Tomcat HTTP port (often `8080` locally).

### Further reading

| File | Contents |
|------|----------|
| [`VM_DEPLOYMENT_GUIDE.md`](VM_DEPLOYMENT_GUIDE.md) | Full VM deployment: Tomcat, MySQL, Elasticsearch, `.env`, logging |
| [`frontend/README.md`](frontend/README.md) | Bulk Upload Wizard, Vite, Tailwind, build workflow |

### Postman

If a Postman collection is provided with the project or internal docs, import it to exercise REST APIs. A path like `docs/postman_collection.json` may not exist in every clone.

---

## Arabic — العربية

### الوصف

**BUDG** منصة لحوكمة بيانات الأعمال: المصطلحات، مجموعات البيانات، السمات، الأنظمة، السياسات، والعمليات، مع مسارات عمل للحوكمة وإدارة التغيير وتتبع التدقيق.

الإصدار الحالي عبارة عن **تطبيق Java (WAR)** يُنشر على **Apache Tomcat 11** مع **MySQL** و**Elasticsearch** للبحث والفهرسة، وواجهة ويب في `src/main/webapp`، و**معالج رفع مجمع (Bulk Upload)** بـ **React + Vite** في `frontend/`، وخدمة اختيارية بـ **Python (FastAPI)** للتحقق المسبق من ملفات Excel قبل الإدراج.

### المتطلبات

| المكوّن | الإصدار / الملاحظات |
|--------|----------------------|
| **Java** | JDK 17 |
| **Maven** | 3.6+ (للبناء) |
| **Apache Tomcat** | 11.x (نشر ملف `ROOT.war`) |
| **قاعدة البيانات** | MySQL 8.0+ أو MariaDB متوافقة |
| **Elasticsearch** | 8.14.x (متوافق مع `elasticsearch-java` في `pom.xml`) |
| **Node.js** | 18+ (لتطوير وبناء واجهة Bulk Upload Wizard فقط) |
| **Python** | 3.10+ (اختياري — خدمة التحقق من الرفع المجمع) |

### هيكل المشروع (ملخص)

- **`src/main/java`** — طبقة الخادم (Jakarta Servlet 6، REST، JWT، تكامل LDAP، إلخ).
- **`src/main/webapp`** — الواجهة الرئيسية (HTML/CSS/JS)، و**Swagger UI** على `/swagger-ui.html`.
- **`frontend/`** — تطبيق React (Vite) لمعالج الرفع المجمع؛ يُبنى إلى أصول داخل `src/main/webapp` عند الإنتاج.
- **`python/`** — خدمة `bulk_validation_service.py` (FastAPI) والمعالجات حسب نوع الكيان.
- **`pom.xml`** — بناء WAR باسم **`ROOT.war`** (سياق الجذر `/`).

### التشغيل السريع للتطوير

**1) قاعدة البيانات والبيئة**

- أنشئ قاعدة بيانات واضبط الاتصال (في التوثيق غالباً اسم القاعدة `project`).
- انسخ القالب من `src/main/resources/.env` إلى ملف `.env` في **`[Tomcat]/bin/.env`** (أو عيّن المتغيرات بنفس الأسماء).  
  **لا ترفع ملف `.env` الحقيقي إلى Git.**

> تفاصيل النسخ الاحتياطي وملفات الـ migrations وترتيب SQL موثّقة في **[`VM_DEPLOYMENT_GUIDE.md`](VM_DEPLOYMENT_GUIDE.md)** (قد تُرفق ملفات مثل `projectbackup.sql` ومجلد `database/` مع النشر وليست دائماً في المستودع).

**2) بناء الخادم**

من جذر المشروع:

```bash
mvn clean package -DskipTests
```

النتيجة: **`target/ROOT.war`**. انسخها إلى `webapps/` على Tomcat 11 أو استخدم إعداد النشر (مثلاً Cargo Maven مع `CATALINA_HOME`).

> التطبيق **ليس** Spring Boot؛ لا يُستخدم `spring-boot:run`.

**3) واجهة Bulk Upload (React) — تطوير محلي**

```bash
cd frontend
npm install
npm run dev
```

عادةً يعمل الخادم على **`http://localhost:5173`** (تفاصيل البناء للإنتاج في **`frontend/README.md`**).

**4) خدمة التحقق Python (اختياري)**

```bash
cd python
pip install -r requirements.txt
# التشغيل حسب توثيق FastAPI (مثلاً uvicorn على المنفذ المحدد)
```

### أدوات في جذر المشروع

- **`npm run i18n:check`** (بعد `npm install` في الجذر إن لزم) — للتحقق من مفاتيح الترجمة بين `en.json` و `ar.json`.

### توثيق API (Swagger)

بعد تشغيل التطبيق على Tomcat:

**`http://localhost:<port>/swagger-ui.html`**

`<port>` هو منفذ Tomcat (غالباً `8080` محلياً).

### مراجع

| الملف | المحتوى |
|-------|---------|
| [`VM_DEPLOYMENT_GUIDE.md`](VM_DEPLOYMENT_GUIDE.md) | نشر كامل على VM، Tomcat، MySQL، Elasticsearch، `.env`، السجلات |
| [`frontend/README.md`](frontend/README.md) | Bulk Upload Wizard، Vite، Tailwind، البناء |

### Postman

إن وُجدت مجموعة Postman مع المشروع أو التوثيق الداخلي يمكن استيرادها؛ مسار مثل `docs/postman_collection.json` قد لا يكون موجوداً في كل نسخة.
