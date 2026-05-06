# 🏥 Health Assistance System

A production-grade, desktop-based Healthcare Management System built specifically for modern medical facilities (with customized adaptations for Cameroonian healthcare systems). The application provides a comprehensive suite of tools for Patients, Doctors, and Administrators to manage medical records, book appointments, and handle scheduling.

The application features a premium, MedCare-inspired user interface built with JavaFX, leveraging modern design principles like soft drop shadows, glassmorphism, and a highly responsive custom CSS design system.

---

## 🚀 Tech Stack & Architecture

- **Language**: Java 17+
- **GUI Framework**: JavaFX 21
- **Build Tool**: Maven (via `mvnw` wrapper)
- **Database**: MySQL 8.0+
- **Database Connectivity**: JDBC (with custom Connection Pooling)
- **Security**: BCrypt (for cryptographic password hashing)
- **Icons**: FontAwesomeFX

### Architectural Pattern
The application strictly follows the **MVC (Model-View-Controller)** pattern combined with a **DAO (Data Access Object)** and **Service Layer** approach:
- **Models**: Plain Old Java Objects (POJOs) representing database entities.
- **DAOs**: Handle all raw SQL queries and database interactions.
- **Services**: Contain business logic (e.g., `AuthService` for hashing passwords, `ReminderService` for background tasks).
- **Controllers**: Handle JavaFX UI events and bind data to the FXML views.
- **Views**: FXML files styled by a centralized CSS design system.

---

## ✨ Key Features

### 1. Role-Based Access Control (RBAC)
- **Patients**: Can view their health records, browse available doctors, and book/cancel appointments.
- **Doctors**: Can view their schedule, accept/reject appointments, and create medical diagnoses/prescriptions for patients.
- **Admins**: Can oversee the entire system, manage user accounts, and view overarching hospital statistics.

### 2. Intelligent Appointment System
- Real-time booking engine preventing scheduling conflicts.
- **ReminderService**: A background daemon thread that automatically scans the database every minute to find appointments happening within the next 24 hours, simulating an SMS/Email reminder dispatch system.

### 3. Electronic Health Records (EHR)
- Doctors can append diagnoses and prescriptions to a patient's permanent medical timeline.
- Fully integrated with the patient dashboard for instant historical medical context.

### 4. Automated Database Initialization & Mock Seeding
- On the very first launch, the system automatically runs a `CREATE DATABASE IF NOT EXISTS` script.
- **MockDataSeeder**: If the database is empty, it automatically populates the system with highly realistic, localized Cameroonian data (Yaoundé, Douala, Buea hospitals) covering conditions like Severe Malaria, Typhoid, and routine ANC.

---

## 📂 Project Structure

```text
src/main/
├── java/com/healthassist/
│   ├── MainApp.java                 # Main entry point and initialization
│   ├── config/                      
│   │   ├── DatabaseConfig.java      # Singleton JDBC Connection Pool
│   │   ├── DatabaseInitializer.java # Auto-creates schema/tables
│   │   ├── AdminSeeder.java         # Auto-generates default admin
│   │   └── MockDataSeeder.java      # Auto-generates realistic test data
│   ├── controller/                  # UI Event Handlers (Dashboard, Login, etc.)
│   ├── dao/                         # Data Access Objects (PatientDAO, DoctorDAO, etc.)
│   ├── model/                       # Entities (User, Patient, Appointment, etc.)
│   ├── service/                     # Business Logic (AuthService, ReminderService)
│   └── util/                        # Helpers (SceneNavigator, DateUtil)
└── resources/
    ├── config.properties            # Environment variables (DB credentials, ports)
    ├── com/healthassist/fxml/       # UI Layouts
    └── com/healthassist/styles/     # Global CSS design system
```

---

## 🎨 UI & Design System

The application breaks away from standard JavaFX styling by utilizing a **Global CSS Token System** (`global.css` & `dashboard.css`).
- **Color Palette**: Royal Blue (`#2D5BE3`), Soft Lavender (`#F0F4FF`), Slate Gray text.
- **Typography**: Designed to render smoothly with sans-serif system fonts.
- **Components**: Floating cards with 16px border-radius, subtle drop shadows, and hover micro-animations for high interactivity.
- **Currency**: Localized to FCFA for local implementation.

---

## 🛠️ Setup & Installation

### Prerequisites
1. **Java Development Kit (JDK) 17** or higher.
2. **MySQL Server 8.0+** running locally.

### Configuration
1. Open `src/main/resources/config.properties`.
2. Update the `db.password` field to match your local MySQL root password.
3. Update the `db.url` port (e.g., `3306` or `3307`) if your MySQL runs on a custom port.

```properties
db.url=jdbc:mysql://localhost:3307/health_assist?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=YOUR_MYSQL_PASSWORD
```

### Running the Application

You do **not** need to have Maven globally installed. The project includes the Maven Wrapper (`mvnw`).

**From the Terminal (Root Directory):**
```bash
# Compile the application
.\mvnw.cmd compile

# Launch the application
.\mvnw.cmd javafx:run
```

**Using an IDE (IntelliJ IDEA):**
Do *not* use the default green "Run" button, as IntelliJ may incorrectly place older libraries (like JBCrypt) on the classpath instead of the module path.
Instead:
1. Open the **Maven** tool window on the right sidebar.
2. Navigate to `health-assist` -> `Plugins` -> `javafx`.
3. Double-click `javafx:run`.

---

## 🔐 Default Login Credentials

Upon the first successful run, the `MockDataSeeder` and `AdminSeeder` will generate the following accounts for testing. All passwords are automatically hashed using BCrypt.

**Admin:**
- Email: `admin@health.com`
- Password: `Admin@123`

**Sample Doctor:**
- Email: `alain.mbarga@health.cm`
- Password: `Password123`

**Sample Patient:**
- Email: `amadou.a@example.cm`
- Password: `Password123`
