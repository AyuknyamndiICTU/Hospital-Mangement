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
            // ─── 1. Seed Doctors ───
            List<Doctor> doctors = new ArrayList<>();
            
            Doctor d1 = new Doctor();
            d1.setFullName("Dr. Alain Mbarga");
            d1.setEmail("alain.mbarga@health.cm");
            d1.setRole(User.Role.DOCTOR);
            d1.setSpecialization("Infectious Diseases");
            d1.setHospital("Yaoundé Central Hospital");
            d1.setRatePerHour(new BigDecimal("15000.00"));
            d1.setWorkingHours("08:00-16:00");
            doctors.add(d1);

            Doctor d2 = new Doctor();
            d2.setFullName("Dr. Chantal Biya");
            d2.setEmail("chantal.biya@health.cm");
            d2.setRole(User.Role.DOCTOR);
            d2.setSpecialization("Pediatrics");
            d2.setHospital("Chantal Biya Foundation");
            d2.setRatePerHour(new BigDecimal("20000.00"));
            d2.setWorkingHours("09:00-17:00");
            doctors.add(d2);

            Doctor d3 = new Doctor();
            d3.setFullName("Dr. Samuel Eto'o");
            d3.setEmail("samuel.etoo@health.cm");
            d3.setRole(User.Role.DOCTOR);
            d3.setSpecialization("General Practice");
            d3.setHospital("Douala Laquintinie Hospital");
            d3.setRatePerHour(new BigDecimal("10000.00"));
            d3.setWorkingHours("08:00-18:00");
            doctors.add(d3);

            Doctor d4 = new Doctor();
            d4.setFullName("Dr. Marie-Claire Ndongo");
            d4.setEmail("marie.ndongo@health.cm");
            d4.setRole(User.Role.DOCTOR);
            d4.setSpecialization("Obstetrics & Gynecology");
            d4.setHospital("Buea Regional Hospital");
            d4.setRatePerHour(new BigDecimal("18000.00"));
            d4.setWorkingHours("08:00-15:00");
            doctors.add(d4);

            Doctor d5 = new Doctor();
            d5.setFullName("Dr. Jean-Pierre Ndi");
            d5.setEmail("jp.ndi@health.cm");
            d5.setRole(User.Role.DOCTOR);
            d5.setSpecialization("Orthopedics");
            d5.setHospital("Bamenda Regional Hospital");
            d5.setRatePerHour(new BigDecimal("25000.00"));
            d5.setWorkingHours("10:00-18:00");
            doctors.add(d5);

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
            p1.setFullName("Amadou Ahidjo");
            p1.setEmail("amadou.a@example.cm");
            p1.setRole(User.Role.PATIENT);
            p1.setDateOfBirth(LocalDate.of(1985, 4, 12));
            p1.setBloodType("O+");
            p1.setPhone("677-123-456");
            p1.setAddress("Bastos, Yaoundé");
            p1.setEmergencyContact("Fatou Ahidjo (677-123-457)");
            patients.add(p1);

            Patient p2 = new Patient();
            p2.setFullName("Josephine Ngono");
            p2.setEmail("josephine.n@example.cm");
            p2.setRole(User.Role.PATIENT);
            p2.setDateOfBirth(LocalDate.of(1992, 8, 24));
            p2.setBloodType("A-");
            p2.setPhone("699-883-211");
            p2.setAddress("Bonapriso, Douala");
            p2.setEmergencyContact("Pierre Ngono (699-883-212)");
            patients.add(p2);

            Patient p3 = new Patient();
            p3.setFullName("Emmanuel Fon");
            p3.setEmail("e.fon@example.cm");
            p3.setRole(User.Role.PATIENT);
            p3.setDateOfBirth(LocalDate.of(1978, 11, 5));
            p3.setBloodType("B+");
            p3.setPhone("650-442-122");
            p3.setAddress("Molyko, Buea");
            p3.setEmergencyContact("Mary Fon (650-442-123)");
            patients.add(p3);

            Patient p4 = new Patient();
            p4.setFullName("Amina Buba");
            p4.setEmail("amina.b@example.cm");
            p4.setRole(User.Role.PATIENT);
            p4.setDateOfBirth(LocalDate.of(2001, 2, 18));
            p4.setBloodType("AB+");
            p4.setPhone("680-112-334");
            p4.setAddress("Ngaoundéré");
            p4.setEmergencyContact("Buba Ali (680-112-335)");
            patients.add(p4);

            Patient p5 = new Patient();
            p5.setFullName("Victor Kamga");
            p5.setEmail("v.kamga@example.cm");
            p5.setRole(User.Role.PATIENT);
            p5.setDateOfBirth(LocalDate.of(1965, 7, 30));
            p5.setBloodType("O-");
            p5.setPhone("675-998-877");
            p5.setAddress("Bafoussam");
            p5.setEmergencyContact("Sylvie Kamga (675-998-878)");
            patients.add(p5);

            Patient p6 = new Patient();
            p6.setFullName("Grace Nfor");
            p6.setEmail("grace.nfor@example.cm");
            p6.setRole(User.Role.PATIENT);
            p6.setDateOfBirth(LocalDate.of(1990, 5, 20));
            p6.setBloodType("A+");
            p6.setPhone("662-334-556");
            p6.setAddress("Nkwen, Bamenda");
            p6.setEmergencyContact("John Nfor (662-334-557)");
            patients.add(p6);

            for (Patient p : patients) {
                int id = authService.register(p, defaultPassword);
                p.setId(id);
                insertPatientRaw(p); // Direct insert into patients table
            }

            // ─── 3. Seed Health Records ───
            HealthRecord hr1 = new HealthRecord();
            hr1.setPatientId(patients.get(0).getId());
            hr1.setDoctorId(doctors.get(0).getId()); // Alain Mbarga (Infectious Diseases)
            hr1.setVisitDate(LocalDate.now().minusMonths(1));
            hr1.setDiagnosis("Severe Malaria (Plasmodium falciparum positive). Patient presented with high fever, chills, and fatigue.");
            hr1.setPrescription("Artemether-Lumefantrine 80/480mg twice daily for 3 days. Paracetamol 1000mg for fever.");
            healthRecordDAO.save(hr1);

            HealthRecord hr2 = new HealthRecord();
            hr2.setPatientId(patients.get(1).getId());
            hr2.setDoctorId(doctors.get(2).getId()); // Samuel Eto'o (General)
            hr2.setVisitDate(LocalDate.now().minusWeeks(2));
            hr2.setDiagnosis("Typhoid Fever (Widal test positive). Abdominal pain and sustained fever.");
            hr2.setPrescription("Ciprofloxacin 500mg twice daily for 7 days. Advised on clean water consumption.");
            healthRecordDAO.save(hr2);
            
            HealthRecord hr3 = new HealthRecord();
            hr3.setPatientId(patients.get(2).getId());
            hr3.setDoctorId(doctors.get(4).getId()); // Jean-Pierre Ndi (Orthopedics)
            hr3.setVisitDate(LocalDate.now().minusDays(10));
            hr3.setDiagnosis("Fractured tibia following a motorcycle (Okada) accident. X-ray confirms clean break.");
            hr3.setPrescription("Cast applied. Ibuprofen 400mg for pain. Scheduled for follow-up in 4 weeks.");
            healthRecordDAO.save(hr3);

            HealthRecord hr4 = new HealthRecord();
            hr4.setPatientId(patients.get(3).getId());
            hr4.setDoctorId(doctors.get(1).getId()); // Chantal Biya (Pediatrics)
            hr4.setVisitDate(LocalDate.now().minusDays(3));
            hr4.setDiagnosis("Acute Respiratory Infection. Mild cough and congestion.");
            hr4.setPrescription("Amoxicillin 250mg. Vitamin C syrup. Plenty of fluids.");
            healthRecordDAO.save(hr4);

            HealthRecord hr5 = new HealthRecord();
            hr5.setPatientId(patients.get(4).getId());
            hr5.setDoctorId(doctors.get(2).getId()); // Samuel Eto'o (General)
            hr5.setVisitDate(LocalDate.now().minusMonths(3));
            hr5.setDiagnosis("Cholera suspected due to severe dehydration and diarrhea. Confirmed by stool culture.");
            hr5.setPrescription("Oral Rehydration Salts (ORS) and IV fluids (Ringer's Lactate). Doxycycline 300mg single dose.");
            healthRecordDAO.save(hr5);

            HealthRecord hr6 = new HealthRecord();
            hr6.setPatientId(patients.get(5).getId());
            hr6.setDoctorId(doctors.get(3).getId()); // Marie-Claire Ndongo (ObGyn)
            hr6.setVisitDate(LocalDate.now().minusWeeks(1));
            hr6.setDiagnosis("Routine Antenatal Care (ANC) visit. 24 weeks pregnant. Fetal heartbeat normal.");
            hr6.setPrescription("Folic acid and Iron supplements. Tetanus toxoid vaccine administered.");
            healthRecordDAO.save(hr6);

            // ─── 4. Seed Appointments ───
            // Appointment 1: Past (Completed)
            Appointment a1 = new Appointment();
            a1.setPatientId(patients.get(0).getId());
            a1.setDoctorId(doctors.get(0).getId());
            a1.setAppointmentDatetime(LocalDateTime.now().minusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0));
            a1.setStatus(Appointment.Status.COMPLETED);
            a1.setNotes("Follow-up on Malaria treatment.");
            appointmentDAO.save(a1);

            // Appointment 2: Future (Confirmed)
            Appointment a2 = new Appointment();
            a2.setPatientId(patients.get(1).getId());
            a2.setDoctorId(doctors.get(2).getId());
            a2.setAppointmentDatetime(LocalDateTime.now().plusDays(2).withHour(14).withMinute(30).withSecond(0).withNano(0));
            a2.setStatus(Appointment.Status.CONFIRMED);
            a2.setNotes("Typhoid recovery check.");
            appointmentDAO.save(a2);

            // Appointment 3: Future (Pending)
            Appointment a3 = new Appointment();
            a3.setPatientId(patients.get(2).getId());
            a3.setDoctorId(doctors.get(4).getId());
            a3.setAppointmentDatetime(LocalDateTime.now().plusDays(5).withHour(11).withMinute(0).withSecond(0).withNano(0));
            a3.setStatus(Appointment.Status.PENDING);
            a3.setNotes("Orthopedic cast check.");
            appointmentDAO.save(a3);

            // Appointment 4: Today (Confirmed)
            Appointment a4 = new Appointment();
            a4.setPatientId(patients.get(3).getId());
            a4.setDoctorId(doctors.get(1).getId());
            a4.setAppointmentDatetime(LocalDateTime.now().withHour(15).withMinute(0).withSecond(0).withNano(0));
            a4.setStatus(Appointment.Status.CONFIRMED);
            a4.setNotes("Pediatric follow-up.");
            appointmentDAO.save(a4);

            // Appointment 5: Future (Cancelled)
            Appointment a5 = new Appointment();
            a5.setPatientId(patients.get(4).getId());
            a5.setDoctorId(doctors.get(2).getId());
            a5.setAppointmentDatetime(LocalDateTime.now().plusDays(10).withHour(9).withMinute(0).withSecond(0).withNano(0));
            a5.setStatus(Appointment.Status.CANCELLED);
            a5.setNotes("Patient travelling, will reschedule.");
            appointmentDAO.save(a5);

            // Appointment 6: Future (Pending)
            Appointment a6 = new Appointment();
            a6.setPatientId(patients.get(5).getId());
            a6.setDoctorId(doctors.get(3).getId());
            a6.setAppointmentDatetime(LocalDateTime.now().plusDays(1).withHour(10).withMinute(30).withSecond(0).withNano(0));
            a6.setStatus(Appointment.Status.PENDING);
            a6.setNotes("ANC follow-up.");
            appointmentDAO.save(a6);

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
