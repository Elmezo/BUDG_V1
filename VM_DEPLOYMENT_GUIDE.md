# 🚀 دليل نشر BUDG Platform على VM

هذا الدليل يشرح خطوة بخطوة كيفية نشر مشروع BUDG Platform على خادم VM جديد.

---

## 📋 نظرة عامة على المشروع

| العنصر | القيمة |
|--------|--------|
| **نوع المشروع** | Java Maven WAR Application |
| **إصدار Java** | 17 |
| **خادم التطبيق** | Apache Tomcat 11.x |
| **قاعدة البيانات** | MySQL 8.0+ |
| **محرك البحث** | Elasticsearch 8.14.3 |
| **المصادقة** | JWT + LDAP (اختياري) |

---

## 🛠️ الخطوة 1: تجهيز الـ VM (المتطلبات الأساسية)

### 1.1 تثبيت Java 17 (JDK)

**على Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install openjdk-17-jdk -y
java -version
```

**على CentOS/RHEL:**
```bash
sudo yum install java-17-openjdk-devel -y
java -version
```

**على Windows Server:**
- حمل JDK 17 من [Oracle](https://www.oracle.com/java/technologies/downloads/) أو [Adoptium](https://adoptium.net/)
- ثبته وأضف `JAVA_HOME` إلى متغيرات النظام

### 1.2 تثبيت Apache Tomcat 11

```bash
# إنشاء مجلد Tomcat
sudo mkdir -p /opt/tomcat

# تحميل Tomcat 11 (تحقق من أحدث إصدار)
wget https://dlcdn.apache.org/tomcat/tomcat-11/v11.0.2/bin/apache-tomcat-11.0.2.tar.gz

# فك الضغط
sudo tar xzf apache-tomcat-11.0.2.tar.gz -C /opt/tomcat --strip-components=1

# إعداد الصلاحيات
sudo chmod +x /opt/tomcat/bin/*.sh
```

**على Windows:**
- حمل Tomcat 11 من [Apache Tomcat](https://tomcat.apache.org/download-11.cgi)
- استخدم النسخة `.zip` أو `.msi` installer

### 1.3 تثبيت MySQL 8.0

**على Ubuntu/Debian:**
```bash
sudo apt install mysql-server -y
sudo systemctl start mysql
sudo systemctl enable mysql
sudo mysql_secure_installation
```

**على CentOS/RHEL:**
```bash
sudo yum install mysql-server -y
sudo systemctl start mysqld
sudo systemctl enable mysqld
```

### 1.4 تثبيت Elasticsearch 8.14.3

```bash
# إضافة مفتاح وrepository
wget -qO - https://artifacts.elastic.co/GPG-KEY-elasticsearch | sudo gpg --dearmor -o /usr/share/keyrings/elasticsearch-keyring.gpg

echo "deb [signed-by=/usr/share/keyrings/elasticsearch-keyring.gpg] https://artifacts.elastic.co/packages/8.x/apt stable main" | sudo tee /etc/apt/sources.list.d/elastic-8.x.list

# تثبيت
sudo apt update
sudo apt install elasticsearch -y

# تشغيل
sudo systemctl start elasticsearch
sudo systemctl enable elasticsearch
```

> [!IMPORTANT]
> احفظ كلمة المرور التي تظهر عند التثبيت الأول لـ Elasticsearch!

---

## 🗄️ الخطوة 2: إعداد قاعدة البيانات

### 2.1 إنشاء قاعدة البيانات والمستخدم

```sql
-- اتصل بـ MySQL
mysql -u root -p

-- إنشاء قاعدة البيانات
CREATE DATABASE project CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- إنشاء مستخدم (غير كلمة المرور)
CREATE USER 'budg_user'@'localhost' IDENTIFIED BY 'YOUR_STRONG_PASSWORD';

-- منح الصلاحيات
GRANT ALL PRIVILEGES ON project.* TO 'budg_user'@'localhost';
FLUSH PRIVILEGES;
```

### 2.2 استيراد قاعدة البيانات

```bash
# استيراد النسخة الاحتياطية الكاملة
mysql -u budg_user -p project < projectbackup.sql
```

### 2.3 تشغيل ملفات الـ Migrations

يجب تشغيل ملفات SQL التالية بالترتيب:

| # | الملف | الوصف |
|---|-------|-------|
| 1 | `database/workflow_schema.sql` | جداول Workflow |
| 2 | `database/values_schema.sql` | جداول القيم |
| 3 | `database/static_pages.sql` | الصفحات الثابتة |
| 4 | `database/email_notification_migration.sql` | إعدادات البريد |
| 5 | `database/workflow_notification_rule.sql` | قواعد الإشعارات |
| 6 | `database/workflow_sla_migration.sql` | SLA للـ Workflow |
| 7 | `database/export_objects_setup.sql` | إعداد التصدير |
| 8 | `database_performance_indexes.sql` | فهارس الأداء |

```bash
# مثال تشغيل الـ migrations
cd /path/to/BUDG_V2
mysql -u budg_user -p project < database/workflow_schema.sql
mysql -u budg_user -p project < database/values_schema.sql
# ... وهكذا
```

---

## ⚙️ الخطوة 3: تجهيز ملف .env (Environment Variables)

### 3.1 إنشاء ملف .env

أنشئ ملف `.env` في المسار التالي على الـ VM:
- **Linux:** `/opt/tomcat/bin/.env`
- **Windows:** `C:\Tomcat\bin\.env`

### 3.2 محتوى ملف .env

```env
# ============================================
# BUDG Platform Environment Configuration
# ============================================

# JWT Secret Key (مهم جداً - لا تشاركه مع أحد)
# استخدم: openssl rand -base64 64 لتوليد مفتاح جديد
JWT_SECRET_KEY=YOUR_STRONG_64_CHAR_SECRET_KEY_HERE

# ============================================
# Database Configuration
# ============================================
DB_URL=jdbc:mysql://localhost:3306/project
DB_USERNAME=budg_user
DB_PASSWORD=YOUR_DATABASE_PASSWORD

# ============================================
# Security Settings
# ============================================
# اضبط على true في Production
HTTPS_ONLY=true

# CORS - عناوين مسموح بها
ALLOWED_ORIGINS=https://your-domain.com,https://www.your-domain.com

# ============================================
# Rate Limiting
# ============================================
MAX_LOGIN_ATTEMPTS=5
LOCKOUT_TIME_MINUTES=15
RESET_TIME_HOURS=1

# ============================================
# Logging
# ============================================
LOG_LEVEL=INFO
# مسار مجلد السجلات (prod_audit.log, prod_app.log, prod_errors.log). في الإنتاج استخدم مساراً مطلقاً.
LOG_DIR=/var/log/budg_v2

# ============================================
# LDAP Configuration (اختياري)
# ============================================
LDAP_ENABLED=false
LDAP_HOST=ldap.your-domain.com
LDAP_PORT=389
LDAP_BASE_DN=dc=your-domain,dc=com
LDAP_USER_DN=ou=people,dc=your-domain,dc=com
LDAP_GROUP_DN=ou=groups,dc=your-domain,dc=com
LDAP_USE_SSL=true
```

> [!CAUTION]
> **أمان:** تأكد من:
> - تغيير `JWT_SECRET_KEY` لقيمة فريدة وقوية
> - عدم استخدام كلمات مرور ضعيفة
> - ضبط `HTTPS_ONLY=true` في Production
> - عدم رفع ملف `.env` على Git

### 3.3 تحميل متغيرات البيئة

**على Linux (systemd):** أنشئ ملف service:
```bash
sudo nano /etc/systemd/system/tomcat.service
```

```ini
[Unit]
Description=Apache Tomcat
After=network.target

[Service]
Type=forking
User=tomcat
Group=tomcat

Environment="JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64"
Environment="CATALINA_HOME=/opt/tomcat"
Environment="CATALINA_OPTS=-Ddocument.upload.dir=/opt/budg-data/uploads/documents -DLOG_DIR=/var/log/budg_v2"
EnvironmentFile=/opt/tomcat/bin/.env

ExecStart=/opt/tomcat/bin/startup.sh
ExecStop=/opt/tomcat/bin/shutdown.sh

[Install]
WantedBy=multi-user.target
```

**على Windows:** أضف المتغيرات في `setenv.bat`:
```batch
@echo off
set JAVA_HOME=C:\Program Files\Java\jdk-17
set JWT_SECRET_KEY=your_key_here
set DB_URL=jdbc:mysql://localhost:3306/project
set DB_USERNAME=budg_user
set DB_PASSWORD=your_password
rem ... باقي المتغيرات
set CATALINA_OPTS=%CATALINA_OPTS% -DLOG_DIR=C:\logs\budg_v2
```

### 3.4 مجلد سجلات التطبيق (LOG_DIR)

التطبيق يكتب السجلات (audit, app, errors) في مجلد يُحدد عبر الخاصية `LOG_DIR`. في الإنتاج يجب استخدام **مسار مطلق** حتى لا يعتمد المسار على مجلد العمل عند تشغيل Tomcat.

**على Linux:**

1. إنشاء المجلد ومنح صلاحيات لمستخدم Tomcat (عدّل اسم المستخدم إن لزم، مثلاً `tomcat9`):

```bash
sudo mkdir -p /var/log/budg_v2
sudo chown -R tomcat:tomcat /var/log/budg_v2
sudo chmod 755 /var/log/budg_v2
```

2. تعيين `LOG_DIR` عند تشغيل Tomcat (مثلاً في systemd كما في 3.3):  
   `-DLOG_DIR=/var/log/budg_v2` داخل `CATALINA_OPTS`.

**سكربت جاهز (اختياري):** يمكن تشغيل `scripts/setup-log-dir.sh` على السيرفر بعد ضبط المسار والمستخدم فيه.

بدون تعيين `LOG_DIR`، القيمة الافتراضية هي `logs` (مسار نسبي) وقد يسبب خطأ مثل `Failed to create parent directories for [/logs/prod_audit.log]` إذا كان مجلد العمل عند التشغيل هو `/`.

---

## 🏗️ الخطوة 4: بناء المشروع (Build)

### 4.1 البناء على جهاز التطوير (مُوصى به)

```bash
cd D:\BUDG_V2

# تنظيف وبناء
mvn clean package -DskipTests

# الملف الناتج
# target/ROOT.war
```

### 4.2 أو البناء على الـ VM مباشرة

```bash
# تثبيت Maven
sudo apt install maven -y

# نقل المشروع للـ VM وبناؤه
cd /path/to/BUDG_V2
mvn clean package -DskipTests
```

---

## 📦 الخطوة 5: الملفات التي يجب نقلها للـ VM

### 5.1 الملفات الأساسية

| الملف/المجلد | الوجهة على VM | الوصف |
|-------------|---------------|-------|
| `target/ROOT.war` | `/opt/tomcat/webapps/ROOT.war` | ملف التطبيق المضغوط |
| `.env` | `/opt/tomcat/bin/.env` | متغيرات البيئة |
| `database/` | أي مكان مؤقت | ملفات SQL للـ migrations |
| `projectbackup.sql` | أي مكان مؤقت | النسخة الاحتياطية للـ DB |
| `uploads/` | `/opt/tomcat/webapps/ROOT/uploads/` | مجلد الملفات المرفوعة |

### 5.2 نقل الملفات

**باستخدام SCP (Linux):**
```bash
# نقل ملف WAR
scp target/ROOT.war user@vm-ip:/opt/tomcat/webapps/

# نقل ملفات قاعدة البيانات
scp -r database/ user@vm-ip:/tmp/
scp projectbackup.sql user@vm-ip:/tmp/
```

**باستخدام FileZilla/WinSCP (Windows):**
- اتصل بالـ VM عبر SFTP
- انقل الملفات للمسارات المحددة

---

## 🚀 الخطوة 6: نشر التطبيق على Tomcat

### 6.1 إيقاف Tomcat

```bash
sudo systemctl stop tomcat
# أو
/opt/tomcat/bin/shutdown.sh
```

### 6.2 حذف التطبيق القديم (إن وجد)

```bash
rm -rf /opt/tomcat/webapps/ROOT
rm -f /opt/tomcat/webapps/ROOT.war
```

### 6.3 نسخ ملف WAR الجديد

```bash
cp /path/to/ROOT.war /opt/tomcat/webapps/
```

### 6.4 إنشاء مجلد uploads (هام)

```bash
mkdir -p /opt/tomcat/webapps/ROOT/uploads
chown -R tomcat:tomcat /opt/tomcat/webapps/ROOT/uploads
chmod 755 /opt/tomcat/webapps/ROOT/uploads
```

### 6.5 تشغيل Tomcat

```bash
sudo systemctl start tomcat
# أو
/opt/tomcat/bin/startup.sh
```

### 6.6 مراقبة السجلات

```bash
tail -f /opt/tomcat/logs/catalina.out
```

---

## ✅ الخطوة 7: التحقق من النشر

### 7.1 فحص الصحة

```bash
# فحص أن Tomcat يعمل
curl -I http://localhost:8080

# فحص صفحة تسجيل الدخول
curl -I http://localhost:8080/login.html
```

### 7.2 فحص الخدمات

```bash
# MySQL
sudo systemctl status mysql

# Elasticsearch
sudo systemctl status elasticsearch
curl -X GET "localhost:9200/_cluster/health?pretty"

# Tomcat
sudo systemctl status tomcat
```

### 7.3 الدخول للتطبيق

افتح المتصفح وادخل على:
```
http://YOUR_VM_IP:8080
```

---

## 🔒 الخطوة 8: إعدادات Production الإضافية

### 8.1 إعداد HTTPS (مطلوب)

```bash
# تثبيت Certbot
sudo apt install certbot -y

# الحصول على شهادة SSL
sudo certbot certonly --standalone -d your-domain.com
```

### 8.2 إعداد Firewall

```bash
# فتح المنافذ المطلوبة فقط
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 80/tcp   # HTTP
sudo ufw allow 443/tcp  # HTTPS
sudo ufw enable
```

### 8.3 إعداد Nginx كـ Reverse Proxy (مُوصى به)

```nginx
server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$server_name$request_uri;
}

server {
    listen 443 ssl;
    server_name your-domain.com;

    ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 📊 ملخص تنفيذي

### قائمة مختصرة للنشر السريع:

- [ ] تثبيت Java 17
- [ ] تثبيت Tomcat 11
- [ ] تثبيت MySQL 8
- [ ] تثبيت Elasticsearch 8.14.3
- [ ] إنشاء قاعدة البيانات `project`
- [ ] استيراد `projectbackup.sql`
- [ ] تشغيل ملفات migrations
- [ ] إنشاء ملف `.env` مع القيم الصحيحة
- [ ] بناء المشروع: `mvn clean package -DskipTests`
- [ ] نسخ `ROOT.war` إلى `/opt/tomcat/webapps/`
- [ ] إنشاء مجلد `uploads`
- [ ] تشغيل Tomcat
- [ ] فحص التطبيق

---

## 🆘 حل المشاكل الشائعة

| المشكلة | الحل |
|---------|------|
| `OutOfMemoryError` | زيادة `JAVA_OPTS` في Tomcat |
| لا يتصل بقاعدة البيانات | تحقق من `DB_URL` و الصلاحيات |
| خطأ في JWT | تأكد من `JWT_SECRET_KEY` صحيح |
| Elasticsearch لا يعمل | `sudo systemctl restart elasticsearch` |
| ملفات لا ترفع | تحقق من صلاحيات مجلد `uploads` |

---

> [!TIP]
> **نصيحة:** احتفظ بنسخة احتياطية من ملف `.env` في مكان آمن منفصل!

---

*آخر تحديث: 2026-02-02*
