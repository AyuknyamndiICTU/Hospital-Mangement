package com.healthassist.config;

import com.healthassist.dao.AppointmentDAO;
import com.healthassist.dao.DoctorDAO;
import com.healthassist.dao.HealthRecordDAO;
import com.healthassist.dao.PatientDAO;
import com.healthassist.model.Appointment;
import com.healthassist.model.Doctor;
import com.healthassist.model.HealthRecord;
import com.healthassist.model.Patient;
import com.healthassist.model.User;
import com.healthassist.service.AuthService;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds the database with realistic mock data for testing and demonstration.
 */
public class MockDataSeeder {

    public static void seed() {
        try {
            Connection conn = DatabaseConfig.getInstance().getConnection();
            
            // Check if data already exists to avoid duplicate seeding
            PreparedStatement checkStmt = conn.prepareStatement("SELECT COUNT(*) FROM users WHERE role IN ('DOCTOR', 'PATIENT')");
            ResultSet rs = checkStmt.executeQuery();
            rs.next();
            int count = rs.getInt(1);
            rs.close();
            checkStmt.close();
            DatabaseConfig.getInstance().releaseConnection(conn);

            if (count > 0) {
                System.out.println("Mock data already exists — skipping mock data seed.");
                return;
            }
            
            System.out.println("Seeding realistic mock data...");

            AuthService authService = new AuthService();
            PatientDAO patientDAO = new PatientDAO();
            DoctorDAO doctorDAO = new DoctorDAO();
            AppointmentDAO appointmentDAO = new AppointmentDAO();
            HealthRecordDAO healthRecordDAO = new HealthRecordDAO();

            String defaultPassword = "Password123";

            // ─── 1. Seed Doctors ───
            List<Doctor> doctors = new ArrayList<>();
            
            Doctor d1 = new Doctor();
            d1.setFullName("Dr. Sarah Jenkins");
            d1.setEmail("sarah.jenkins@health.com");
            d1.setRole(User.Role.DOCTOR);
            d1.setSpecialization("Cardiology");
            d1.setHospital("Central City Hospital");
            d1.setRatePerHour(new BigDecimal("150.00"));
            d1.setWorkingHours("08:00-16:00");
            doctors.add(d1);

            Doctor d2 = new Doctor();
            d2.setFullName("Dr. Marcus Chen");
            d2.setEmail("marcus.chen@health.com");
            d2.setRole(User.Role.DOCTOR);
            d2.setSpecialization("Neurology");
            d2.setHospital("MedCare Clinic");
            d2.setRatePerHour(new BigDecimal("200.00"));
            d2.setWorkingHours("09:00-17:00");
            doctors.add(d2);

            Doctor d3 = new Doctor();
            d3.setFullName("Dr. Emily Carter");
            d3.setEmail("emily.carter@health.com");
            d3.setRole(User.Role.DOCTOR);
            d3.setSpecialization("General Practice");
            d3.setHospital("Westside Family Practice");
            d3.setRatePerHour(new BigDecimal("90.00"));
            d3.setWorkingHours("08:00-18:00");
            doctors.add(d3);

            for (Doctor d : doctors) {
                int id = authService.register(d, defaultPassword);
                d.setId(id);
                insertDoctorRaw(d); // Direct insert into doctors table
                
                // Add schedule for Monday to Friday
                List<Map<String, String>> schedule = new ArrayList<>();
                String[] days = {"MON", "TUE", "WED", "THU", "FRI"};
                for (String day : days) {
                    Map<String, String> slot = new HashMap<>();
                    slot.put("day", day);
                    slot.put("start", "08:00");
                    slot.put("end", "17:00");
                    schedule.add(slot);
                }
                doctorDAO.saveSchedule(id, schedule);
            }

            // ─── 2. Seed Patients ───
            List<Patient> patients = new ArrayList<>();
            
            Patient p1 = new Patient();
            p1.setFullName("Michael Ross");
            p1.setEmail("michael.ross@example.com");
            p1.setRole(User.Role.PATIENT);
            p1.setDateOfBirth(LocalDate.of(1985, 4, 12));
            p1.setBloodType("O+");
            p1.setPhone("555-0192");
            p1.setAddress("123 Maple Street, NY");
            p1.setEmergencyContact("Jane Ross (555-0193)");
            patients.add(p1);

            Patient p2 = new Patient();
            p2.setFullName("Sophia Martinez");
            p2.setEmail("sophia.m@example.com");
            p2.setRole(User.Role.PATIENT);
            p2.setDateOfBirth(LocalDate.of(1992, 8, 24));
            p2.setBloodType("A-");
            p2.setPhone("555-8832");
            p2.setAddress("456 Oak Avenue, CA");
            p2.setEmergencyContact("Carlos Martinez (555-8833)");
            patients.add(p2);

            Patient p3 = new Patient();
            p3.setFullName("James Wilson");
            p3.setEmail("j.wilson@example.com");
            p3.setRole(User.Role.PATIENT);
            p3.setDateOfBirth(LocalDate.of(1978, 11, 5));
            p3.setBloodType("B+");
            p3.setPhone("555-4421");
            p3.setAddress("789 Pine Road, TX");
            p3.setEmergencyContact("Mary Wilson (555-4422)");
            patients.add(p3);

            for (Patient p : patients) {
                int id = authService.register(p, defaultPassword);
                p.setId(id);
                insertPatientRaw(p); // Direct insert into patients table
            }

            // ─── 3. Seed Health Records ───
            HealthRecord hr1 = new HealthRecord();
            hr1.setPatientId(patients.get(0).getId());
            hr1.setDoctorId(doctors.get(2).getId()); // Emily Carter
            hr1.setVisitDate(LocalDate.now().minusMonths(2));
            hr1.setDiagnosis("Routine checkup. Mild hypertension observed.");
            hr1.setPrescription("Lisinopril 10mg daily. Recommend low sodium diet.");
            healthRecordDAO.save(hr1);

            HealthRecord hr2 = new HealthRecord();
            hr2.setPatientId(patients.get(1).getId());
            hr2.setDoctorId(doctors.get(1).getId()); // Marcus Chen
            hr2.setVisitDate(LocalDate.now().minusWeeks(3));
            hr2.setDiagnosis("Frequent migraines and aura.");
            hr2.setPrescription("Sumatriptan 50mg as needed. Scheduled for MRI.");
            healthRecordDAO.save(hr2);
            
            HealthRecord hr3 = new HealthRecord();
            hr3.setPatientId(patients.get(2).getId());
            hr3.setDoctorId(doctors.get(0).getId()); // Sarah Jenkins
            hr3.setVisitDate(LocalDate.now().minusDays(10));
            hr3.setDiagnosis("Arrhythmia detected during ECG.");
            hr3.setPrescription("Metoprolol 25mg twice daily. Follow up in 2 weeks.");
            healthRecordDAO.save(hr3);

            // ─── 4. Seed Appointments ───
            // Appointment 1: Past (Completed)
            Appointment a1 = new Appointment();
            a1.setPatientId(patients.get(0).getId());
            a1.setDoctorId(doctors.get(2).getId());
            a1.setAppointmentDatetime(LocalDateTime.now().minusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0));
            a1.setStatus(Appointment.Status.COMPLETED);
            a1.setNotes("Follow-up on blood pressure.");
            appointmentDAO.save(a1);

            // Appointment 2: Future (Confirmed)
            Appointment a2 = new Appointment();
            a2.setPatientId(patients.get(1).getId());
            a2.setDoctorId(doctors.get(1).getId());
            a2.setAppointmentDatetime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(30).withSecond(0).withNano(0));
            a2.setStatus(Appointment.Status.CONFIRMED);
            a2.setNotes("MRI Results review.");
            appointmentDAO.save(a2);

            // Appointment 3: Future (Pending)
            Appointment a3 = new Appointment();
            a3.setPatientId(patients.get(2).getId());
            a3.setDoctorId(doctors.get(0).getId());
            a3.setAppointmentDatetime(LocalDateTime.now().plusDays(5).withHour(11).withMinute(0).withSecond(0).withNano(0));
            a3.setStatus(Appointment.Status.PENDING);
            a3.setNotes("Arrhythmia follow-up check.");
            appointmentDAO.save(a3);

            System.out.println("Mock data seeded successfully.");

        } catch (Exception e) {
            System.err.println("Failed to seed mock data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void insertDoctorRaw(Doctor d) {
        try {
            Connection conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement("INSERT INTO doctors (id, specialization, rate_per_hour, hospital, working_hours) VALUES (?, ?, ?, ?, ?)");
            ps.setInt(1, d.getId());
            ps.setString(2, d.getSpecialization());
            ps.setBigDecimal(3, d.getRatePerHour());
            ps.setString(4, d.getHospital());
            ps.setString(5, d.getWorkingHours());
            ps.executeUpdate();
            ps.close();
            DatabaseConfig.getInstance().releaseConnection(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void insertPatientRaw(Patient p) {
        try {
            Connection conn = DatabaseConfig.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement("INSERT INTO patients (id, date_of_birth, blood_type, address, phone, emergency_contact) VALUES (?, ?, ?, ?, ?, ?)");
            ps.setInt(1, p.getId());
            ps.setDate(2, java.sql.Date.valueOf(p.getDateOfBirth()));
            ps.setString(3, p.getBloodType());
            ps.setString(4, p.getAddress());
            ps.setString(5, p.getPhone());
            ps.setString(6, p.getEmergencyContact());
            ps.executeUpdate();
            ps.close();
            DatabaseConfig.getInstance().releaseConnection(conn);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
