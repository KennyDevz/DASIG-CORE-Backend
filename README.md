# DASIG-CORE — Backend

**DASIG Consortium Oversight and Reporting Environment**

DASIG-CORE is a web-based KPI performance management platform for the DASIG Consortium (DOST-PCIEERD ReSEED Program). It replaces manual, fragmented KPI reporting across member Technology Business Incubators (TBIs) with one system for assigning KPIs to committees, recording progress, detecting at-risk and overdue KPIs, and generating AI-assisted consolidated reports.

This repository contains the **Spring Boot REST API**. The React frontend is in [DASIG-CORE-Frontend](https://github.com/adamxparx/DASIG-CORE-Frontend).

| | |
|---|---|
| **Live system** | https://www.dasig-core.site/ |
| **Backend API** | https://dasig-core.onrender.com |
| **Team** | 2627-sem2-it411-52 — Cebu Institute of Technology – University |

---

## Features

| Module | Features |
|---|---|
| 1. User Management and Role Access | Local login (JWT) and Microsoft Entra ID single sign-on; roles DASIG Admin, Committee Lead and Member; create users with emailed temporary password and forced password change; update, deactivate and reactivate users; organizations and committees with assigned Committee Leads |
| 2. Configurable KPI and Goal Management | Create KPIs and assign them to a committee; update (with deadline-change handling), archive/unarchive and delete KPIs |
| 3. KPI Submission and Dashboard | Member submissions with evidence files (Supabase Storage) reviewed by the Committee Lead; final values; reference codes; role-based dashboards with KPI status, period history and expected-pace trend |
| 4. Rule-Based KPI Alert | At Risk / Overdue detection with a pace rule; nightly, page-load and post-submission evaluation; acknowledge with remark; automatic resolution; in-app notifications; overdue explanations by Committee Leads; email alerts to Admins |
| 5. AI-Assisted Report Generation | Committee or KPI reports generated with the Groq LLM API from system-computed statuses, with source citations and PDF export |

## Tech stack

- Java 17, Spring Boot 3.5 (Web, Data JPA, Security, Validation, Mail)
- Spring Security with app-issued JWT and OAuth2 resource server (Microsoft Entra ID tokens)
- PostgreSQL and Storage on **Supabase**
- **Groq** LLM API for report generation; iText for PDF export
- **Brevo** SMTP relay for email
- JUnit 5, Mockito and AssertJ for tests
- Docker image deployed on **Render**

## Project structure

```
src/main/java/edu/cit/dasig_core/
├── core/            # security (JWT, Entra ID), SMTP email, global exception handling
└── features/
    ├── auth/        # login
    ├── user/        # users, account (password)
    ├── organization/
    ├── committee/
    ├── kpi/         # KPI definitions (Module 2)
    ├── kpisubmission/  # submissions, documents, status and pace calculation (Module 3)
    ├── dashboard/   # role-based dashboard data (Module 3)
    ├── alert/       # alerts, evaluator, scheduler, overdue explanations (Module 4)
    ├── notification/   # in-app notifications (Module 4)
    └── report/      # AI-assisted reports and PDF export (Module 5)
src/test/java/...    # unit tests per feature
```

## Getting started (local development)

### Prerequisites

- Java 17 (JDK)
- A PostgreSQL database (a Supabase project or a local PostgreSQL)
- Optional for full functionality: Supabase Storage bucket, Groq API key, SMTP account, Microsoft Entra ID app registration

### 1. Clone

```bash
git clone https://github.com/KennyDevz/DASIG-CORE-Backend.git
cd DASIG-CORE-Backend
```

### 2. Configure environment variables

`src/main/resources/application.properties` reads secrets from environment variables. Set them in your shell or in your IDE run configuration (`application.template.properties` lists every property):

| Variable | Purpose |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL, e.g. `jdbc:postgresql://<host>:5432/postgres` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Database credentials |
| `APP_JWT_SECRET` | Secret for signing app JWTs (long random string) |
| `SUPABASE_URL` / `SUPABASE_SERVICE_ROLE_KEY` | Supabase Storage for submission evidence (bucket `kpi-submissions`) |
| `GROQ_API_KEY` / `GROQ_MODEL` | Groq LLM API for report generation |
| `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` | Brevo SMTP credentials (`smtp-relay.brevo.com:587`, STARTTLS) |
| `SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI` | Microsoft Entra ID issuer for SSO tokens |
| `PORT` | HTTP port (default `8080`; set automatically on Render) |

Never commit real secrets.

### 3. Run

```bash
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

The API starts at `http://localhost:8080`. Hibernate creates or updates tables automatically (`ddl-auto=update`).

### 4. Test

```bash
./mvnw test
```

## API overview

| Base path | Purpose |
|---|---|
| `/api/auth` | Login |
| `/api/users`, `/api/account` | Users and password change |
| `/api/organizations`, `/api/committees` | Organizations and committees |
| `/api/kpi-definitions` | KPI create, update, archive, delete |
| `/api/kpi-submissions` | Submissions, review, evidence download |
| `/api/dashboard` | Role-based dashboard and period history |
| `/api/alerts`, `/api/committee-alerts` | Alerts, acknowledgment, overdue explanations |
| `/api/notifications` | In-app notifications |
| `/api/reports` | Report generation, history and PDF export |

All endpoints except `/api/auth/**` require a Bearer token and are restricted by role.

## Deployment

The backend runs on **Render** as a Docker web service built from the `Dockerfile` (multi-stage: Maven build on Temurin 17, then JRE 17 runtime). Render provides HTTPS and the `PORT` variable; all other variables above are set in the Render service's **Environment** settings. The database and file storage are on **Supabase**, and the frontend is on **Vercel** at https://www.dasig-core.site/.

To deploy a new version: merge the reviewed pull request into `main`, then let Render build and deploy it (or trigger a manual deploy from the Render dashboard).

## Demo access

Demo accounts for evaluation are provided separately in the submitted deployment documents.

## Team

| Member | Role |
|---|---|
| John Kenneth Devibar | Project Manager / Team Lead |
| Ronel C. Atillo | Developer |
| Mechole Angelou M. Auditor | QA / Test Lead, Developer |
| Christy Roan N. Reroma | Documentation Lead, Developer |
| Adam Cloyd H. Esparcia | Developer / DevOps |

Capstone Instructor: Sir Ralph P. Laviste · Team Adviser: Dr. Patrick L. Bacalso
