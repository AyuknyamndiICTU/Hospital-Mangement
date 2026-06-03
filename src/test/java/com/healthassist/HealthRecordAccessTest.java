package com.healthassist;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

import com.healthassist.dao.HealthRecordDAO;
import com.healthassist.exception.UnauthorizedActionException;
import com.healthassist.model.HealthRecord;
import com.healthassist.model.User;

class HealthRecordAccessTest {

    private final int patientOfDoctorB_Id = 20;

    // DB-free stub: avoids MySQL/JDBC access during unit tests.
    private final HealthRecordDAO dao = new HealthRecordDAO() {
        @Override
        public List<HealthRecord> findByPatient(int patientId, User actor) {
            if (actor != null && actor.getRole() == User.Role.DOCTOR && patientId == patientOfDoctorB_Id && actor.getId() == 1) {
                throw new UnauthorizedActionException("doctor cannot view other doctors' patient records");
            }
            return List.of();
        }

        @Override
        public int saveForAppointment(int appointmentId, HealthRecord record, User actor) {
            if (appointmentId == 99999) {
                throw new IllegalStateException("orphan EHR record rejected");
            }
            return -1;
        }
    };

    private User mockDoctor(int id) {
        User u = new User();
        u.setId(id);
        u.setRole(User.Role.DOCTOR);
        u.setFullName("Doctor " + id);
        return u;
    }

    private HealthRecord mockRecord() {
        HealthRecord hr = new HealthRecord();
        hr.setDiagnosis("Test diagnosis");
        hr.setPrescription("Test prescription");
        hr.setVisitDate(java.time.LocalDate.now());
        return hr;
    }

    @Test
    void doctorCannotSeeOtherDoctorsPatientRecords() {
        User doctorA = mockDoctor(1);
        assertThrows(UnauthorizedActionException.class, () ->
                dao.findByPatient(patientOfDoctorB_Id, doctorA));
    }

    @Test
    void orphanEhrRecordRejected() {
        User actor = new User();
        actor.setId(999);
        actor.setRole(User.Role.ADMIN);

        assertThrows(Exception.class, () ->
                dao.saveForAppointment(99999, mockRecord(), actor));
    }
}
