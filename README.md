<div align="center">

# ⚡ Joud ERP

### نظام تخطيط موارد المؤسسات — Enterprise Resource Planning System

[![Java](https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk)](https://openjdk.org/projects/jdk/21/)
[![Jakarta EE](https://img.shields.io/badge/Jakarta%20EE-10-blue?style=for-the-badge&logo=eclipseide)](https://jakarta.ee/)
[![Hibernate](https://img.shields.io/badge/Hibernate-6.4-59666C?style=for-the-badge&logo=hibernate)](https://hibernate.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14+-316192?style=for-the-badge&logo=postgresql)](https://www.postgresql.org/)
[![Maven](https://img.shields.io/badge/Maven-3.9+-C71A36?style=for-the-badge&logo=apachemaven)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=for-the-badge)](LICENSE)

<br/>

> **Joud ERP** is a modular, plugin-based Enterprise Resource Planning system built with **Jakarta EE 10** and **Java 21**.  
> يجمع النظام إدارة المحاسبة، الموارد البشرية، المخزون، التجارة، واللوحة التحليلية في منظومة واحدة قابلة للتوسع.

</div>

---

## 📋 Table of Contents

- [About the Project](#-about-the-project)
- [Architecture](#-architecture)
- [Modules](#-modules)
- [Tech Stack](#-tech-stack)
- [Database Schema](#-database-schema)
- [Prerequisites](#-prerequisites)
- [Getting Started](#-getting-started)
- [Deployment](#-deployment-tomcat)
- [Project Structure](#-project-structure)
- [Contributing](#-contributing)
- [License](#-license)

---

## 🌟 About the Project

**Joud ERP** is a full-featured, enterprise-grade ERP system designed for small and medium businesses. Built on the battle-tested **Jakarta EE 10** platform, it provides:

- 📊 **Complete Financial Management** — Double-entry accounting, ledger groups, trial balance, and financial reporting
- 🛒 **Trade & Commerce** — Sales and purchase invoices, customer/supplier management, payment tracking
- 📦 **Inventory Control** — Warehouse management, stock movement tracking, product catalog
- 👥 **Human Resources** — Employee records, attendance, payroll processing
- 🔔 **Notifications** — System-wide alert and notification engine
- 📈 **Analytics Dashboard** — Real-time business intelligence and KPIs

---

## 🏗️ Architecture

Joud ERP follows a **Plugin/Module Architecture** powered by the **Java ServiceLoader (SPI)** mechanism.

```
                    ┌─────────────────────────┐
                    │      Joud Core (Host)   │
                    │   PluginRegistry (SPI)  │
                    └────────────┬────────────┘
                                 │ Auto-Discovery
          ┌──────────┬───────────┼───────────┬──────────┐
          │          │           │           │          │
   ┌──────▼───┐ ┌────▼─────┐ ┌──▼───┐ ┌─────▼────┐ ┌──▼───────┐
   │Accounting│ │    HR    │ │Trade │ │Inventory │ │Dashboard │
   │  Plugin  │ │  Plugin  │ │Plugin│ │  Plugin  │ │  Plugin  │
   └──────────┘ └──────────┘ └──────┘ └──────────┘ └──────────┘
```

**Key architectural concepts:**

| Concept | Description |
|---------|-------------|
| **Auto-Discovery** | `PluginRegistry` automatically discovers all modules at startup via SPI — no hardcoded dependencies |
| **Dependency Validation** | Each module declares its requirements; the core validates `canActivate()` before enabling |
| **Menu Integration** | Modules contribute their menu items to the main navigation via `getAllMenuItems()` |
| **Permission Management** | Module-level permissions are centrally managed by the core security layer |
| **Report Engine** | All module reports are registered to a central reporting engine |

---

## 📦 Modules

| Module | artifactId | Depends On | Responsibilities |
|--------|------------|------------|-----------------|
| **Core** | `joud-core` | — | Plugin registry, security, session management, shared models |
| **Accounting** | `joud-accounting` | joud-core | General ledger, chart of accounts, journal entries, financial reports |
| **HR** | `joud-hr` | joud-core | Employee management, attendance, payroll |
| **Trade** | `joud-trade` | joud-core, joud-accounting | Sales & purchase invoices, parties (customers/suppliers), payments |
| **Inventory** | `joud-inventory` | joud-core, joud-trade | Products, warehouses, stock movements |
| **Dashboard** | `joud-dashboard` | joud-core | Analytics, KPIs, charts |
| **Notification** | `joud-notification` | joud-core | System alerts and notifications |
| **App** | `joud-app` | All modules | WAR packaging, web entry point, CDI wiring |

---

## 🛠️ Tech Stack

### Backend
| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 (LTS) | Core language |
| **Jakarta EE** | 10 | Enterprise platform (CDI, JSF, JPA) |
| **Hibernate ORM** | 6.4.4 | JPA implementation & ORM |
| **Jakarta Faces (JSF)** | 4.0 | Server-side UI framework |
| **PrimeFaces** | 13.0 | UI component library |
| **Jersey (JAX-RS)** | 3.1.5 | REST API layer |
| **Weld** | 5.x | CDI implementation |

### Frontend
| Technology | Purpose |
|------------|---------|
| **Jakarta Faces (XHTML/Facelets)** | Page templating and UI rendering |
| **PrimeFaces** | Rich UI components (DataTable, Charts, etc.) |

### Data & Reporting
| Technology | Version | Purpose |
|------------|---------|---------|
| **PostgreSQL** | 14+ | Primary relational database |
| **JasperReports** | 6.21 | Report generation (PDF, Excel) |
| **Apache POI** | 5.2.5 | Excel export/import |

### Security & Utilities
| Technology | Version | Purpose |
|------------|---------|---------|
| **BCrypt** | 0.10.2 | Password hashing |
| **Lombok** | 1.18.32 | Boilerplate reduction |
| **JUnit 5** | 5.10.2 | Unit testing |
| **Mockito** | 5.11.0 | Mocking framework |

### Build & Deployment
| Technology | Purpose |
|------------|---------|
| **Maven** 3.9+ | Multi-module build system |
| **Apache Tomcat** 10.1.x | Application server |
| **WAR packaging** | Deployment artifact |

---

## 🗄️ Database Schema

The system uses **PostgreSQL** with a rich normalized schema organized into functional groups:

| Group | Tables | Description |
|-------|--------|-------------|
| **User & Admin** | `users`, `role`, `user_roles`, `employee_mast`, `agent_mast` | Authentication and user management |
| **Security** | `sessions`, `login_attempts`, `reset_tokens` | Session and access control |
| **Audit** | `audit_log`, `security_events` | Immutable audit trails |
| **Accounting** | `ledger_group`, `mainldg_mast`, `subldg_mast`, `account_mast`, `sbbal_mast`, `day_ledg`, `batch` | Complete double-entry bookkeeping |
| **Trade** | `parties`, `invoices`, `invoice_items`, `payments`, `treasury_transactions` | Sales, purchases, and payments |
| **Inventory** | `products`, `warehouses`, `categories`, `units`, `stock_movements` | Inventory and warehouse management |
| **System** | `agent_brofile`, `currency_mast`, `key_generation`, `backup_history` | Configuration and maintenance |

> 📄 Full schema details available in [`docs/erp_database_schema.txt`](docs/erp_database_schema.txt)

---

## ✅ Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK | **21** (LTS) |
| Apache Maven | **3.9+** |
| Apache Tomcat | **10.1.x** |
| PostgreSQL | **14+** |

---

## 🚀 Getting Started

### 1. Clone the Repository

```bash
git clone https://github.com/your-username/joud-erp.git
cd joud-erp
```

### 2. Set Up the Database

```sql
-- Create the database
CREATE DATABASE joud_erp
  ENCODING 'UTF8'
  LC_COLLATE 'en_US.UTF-8'
  LC_CTYPE 'en_US.UTF-8';

-- Create a dedicated user
CREATE USER joud_user WITH PASSWORD 'your_secure_password';
GRANT ALL PRIVILEGES ON DATABASE joud_erp TO joud_user;

-- Connect and run schema scripts in order
\c joud_erp
\i docs/erp_database_schema.sql
```

### 3. Configure Database Connection

Edit `joud-app/src/main/resources/META-INF/persistence.xml`:

```xml
<property name="jakarta.persistence.jdbc.url"
          value="jdbc:postgresql://localhost:5432/joud_erp"/>
<property name="jakarta.persistence.jdbc.user"     value="joud_user"/>
<property name="jakarta.persistence.jdbc.password" value="your_secure_password"/>
```

### 4. Build the Project

```bash
# Full build (produces joud-app/target/joud-erp.war)
mvn clean package

# Or build only the WAR after installing modules
mvn install -pl joud-core,joud-accounting,joud-hr,joud-trade,joud-inventory,joud-dashboard,joud-notification
mvn package -pl joud-app
```

### 5. Install Tomcat Dependencies

Copy these JARs to your Tomcat `lib/` directory:

```
postgresql-42.7.3.jar
weld-servlet-shaded-5.1.2.Final.jar
```

### 6. Deploy

```bash
# Copy WAR to Tomcat
cp joud-app/target/joud-erp.war /path/to/tomcat/webapps/

# Start Tomcat
bin/startup.sh        # Linux/macOS
bin\startup.bat       # Windows
```

Open your browser at: **http://localhost:8080/joud-erp/**

### 7. Create Initial Admin User

```sql
-- BCrypt hash for password "admin123" (cost=12)
INSERT INTO users (user_i_id, user_c_name, password_c_hash, full_name, users_c_status, email)
VALUES (1, 'SA',
  '$2a$12$LQv3c1yqBWVHxkd0LHAkCOYz6TtxMQJqhN8/LwTqa6hG5B8DmHqe',
  'System Administrator', 'A', 'admin@example.com');

INSERT INTO role (role_i_id, role_c_name, role_c_desc)
VALUES (1, 'ADMIN', 'Full system access');

INSERT INTO user_roles (user_i_id, role_i_id) VALUES (1, 1);
```

> ⚠️ **Change the default password immediately after first login!**

---

## 🖥️ Deployment (Tomcat)

For detailed production deployment instructions, see **[DEPLOYMENT-README.md](DEPLOYMENT-README.md)**, which covers:

- JNDI DataSource configuration (recommended for production)
- Tomcat `context.xml` setup
- Verifying module loading in logs
- Common troubleshooting scenarios

**Expected log output on successful startup:**
```
INFO  PluginRegistry - Module loaded: accounting v1.0.0
INFO  PluginRegistry - Module loaded: hr v1.0.0
INFO  PluginRegistry - Module loaded: trade v1.0.0
INFO  PluginRegistry - Module loaded: inventory v1.0.0
INFO  PluginRegistry - Module loaded: dashboard v1.0.0
```

---

## 📁 Project Structure

```
joud-erp/
├── pom.xml                    ← Parent POM (multi-module build)
│
├── joud-core/                 ← Core shell: PluginRegistry, JoudModule API, security
│   └── src/main/java/com/joud/core/
│       ├── module/            ← JoudModule.java, PluginRegistry.java
│       ├── menu/              ← MenuItem.java
│       ├── report/            ← ReportDefinition.java, ReportParameter.java
│       └── repository/        ← JoudRepository.java (generic DB layer)
│
├── joud-accounting/           ← Accounting plugin (ledger, accounts, reports)
├── joud-hr/                   ← HR plugin (employees, payroll, attendance)
├── joud-trade/                ← Trade plugin (invoices, parties, payments)
├── joud-inventory/            ← Inventory plugin (products, warehouses, stock)
├── joud-dashboard/            ← Dashboard plugin (KPIs, charts, analytics)
├── joud-notification/         ← Notification plugin (alerts, messages)
│
├── joud-app/                  ← WAR assembly module (web.xml, CDI, entry point)
│   └── src/main/
│       ├── webapp/            ← XHTML pages (JSF/Facelets)
│       └── resources/META-INF/persistence.xml
│
├── docs/                      ← Project documentation
│   ├── erp_database_schema.txt
│   ├── plugin-architecture.md
│   └── i18n-architecture-plan.md
│
└── schema/                    ← SQL schema scripts
```

---

## 🌍 Internationalization (i18n)

Joud ERP is designed with multilingual support in mind. The system supports **Arabic** and **English** interfaces. The i18n architecture uses standard Java resource bundles integrated with Jakarta Faces.

> See [`docs/i18n-architecture-plan.md`](docs/i18n-architecture-plan.md) for full details.

---

## 🔒 Security

- **Session Management**: Custom cookie-based session (`JOUD_SESSION`) with server-side validation
- **Password Hashing**: BCrypt with cost factor 12
- **Role-Based Access Control (RBAC)**: Permissions declared per module, managed centrally
- **Audit Logging**: All data mutations recorded in `audit_log` table
- **Login Attempt Tracking**: Brute-force protection via `login_attempts` table
- **Session Invalidation**: Automatic token revocation on logout or expiry

---

## 🧪 Testing

```bash
# Run all unit tests
mvn test

# Run tests for a specific module
mvn test -pl joud-core

# Skip tests during build
mvn clean package -DskipTests
```

---

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature-name`
3. Commit your changes: `git commit -m 'feat: add some feature'`
4. Push to the branch: `git push origin feature/your-feature-name`
5. Open a Pull Request

Please ensure your code:
- Follows the existing module pattern (implements `JoudModule`)
- Includes unit tests for new functionality
- Does not break existing modules

---

## 🐛 Troubleshooting

| Error | Cause | Solution |
|-------|-------|----------|
| `ClassNotFoundException: org.postgresql.Driver` | Missing PostgreSQL JAR | Copy `postgresql-42.7.3.jar` to `Tomcat/lib/` |
| `WELD-001408: Unsatisfied dependencies` | Missing `beans.xml` | Ensure `beans.xml` exists in `WEB-INF/` |
| `Missing table` / HibernateException | Schema not applied | Run SQL scripts in order |
| `javax.faces.FacesException` | Faces Servlet not loaded | Check `web.xml` servlet mappings |
| Blank page after login | Session cookie issue | Verify `JOUD_SESSION` cookie is set |

---

## 📄 License

This project is licensed under the **Apache License 2.0** — see the [LICENSE](LICENSE) file for details.

You are free to use, modify, distribute, and sublicense this software under the terms of the Apache 2.0 License.

---

## 👨‍💻 Authors

- **Joud Development Team** — System architecture and implementation

---

## 🙏 Acknowledgments

- [Jakarta EE](https://jakarta.ee/) — Enterprise Java platform
- [Hibernate ORM](https://hibernate.org/) — Object-relational mapping
- [PrimeFaces](https://www.primefaces.org/) — JSF component library
- [Apache Tomcat](https://tomcat.apache.org/) — Application server
- [JasperReports](https://community.jaspersoft.com/) — Report engine
- [Project Lombok](https://projectlombok.org/) — Java productivity library

---

<div align="center">

**Built with ❤️ using Jakarta EE 10 & Java 21**

⭐ Star this repo if you find it useful!

</div>
