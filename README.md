# Project Name: BUDG_V2

## Description:
Business Data Governance (BUDG) is a comprehensive data governance and management application designed to centralize and streamline the organization of business-critical data assets across the enterprise. It provides a unified platform that enables teams to define, manage, and govern business glossaries, datasets, attributes, systems, policies, and processes with clarity and consistency.

BUDG integrates governance workflows, role-based stewardship, and change management capabilities to ensure accountability, traceability, and data quality across all domains. By bridging the gap between business and technical stakeholders, it empowers users to easily discover, understand, and trust the data they rely on—without heavy dependency on IT.

Built with flexibility and scalability in mind, BUDG supports customization, automation, and seamless integration with modern data ecosystems. It enhances collaboration, improves decision-making, and drives data governance maturity by enabling organizations to standardize definitions, enforce policies, and maintain a single source of truth for all governed data assets.

## المتطلبات (Requirements)
- Java 17
- Node.js 18
- MariaDB / MySQL
- Maven

## طريقة تشغيل المشروع (How to Run)

1. **Import database** from `database.sql`
2. **Configure environment variables**
3. **Run backend using:**
   ```bash
   mvn spring-boot:run
   ```
4. **Run frontend using:**
   ```bash
   npm install
   npm start
   ```

## 3️⃣ ملف قاعدة البيانات
- يرجى التأكد من استيراد ملف قاعدة البيانات المرفق مع المشروع لضمان عمل النظام بشكل صحيح.

## 4️⃣ توثيق الـ API (API Documentation)
- **Swagger UI:** متاح عبر الرابط التفاعلي (مثال: `http://localhost:8080/swagger-ui.html`)
- **Postman Collection:** يمكنك استيراد الملف الجاهز لاختبار الواجهات البرمجية من المسار التالي:
  ```text
  docs/postman_collection.json
  ```
