# PLAN — Real-time Dashboard + Multithreading Enhancements (+ Health Monitoring)  

_This plan is structured into phases you can follow anytime. Each phase has clear tasks and an “exit point” where you can stop and continue later._  

---  

## Phase 0 — Confirm scope & freeze assumptions  
### Tasks  
- [x] Decide the **real-time dashboard update interval** (default: **30s**)  
- [x] Decide what “**health monitoring updates**” means:  
  - [x] Option A: latest health activity (top N most recent health records)  
  - [ ] Option B: patients due for follow-up (last visit older than threshold days)  
- [x] Decide which dashboard components refresh in real-time:  
  - [x] Stat cards (patients/doctors/today appointments)  
  - [x] Pie chart (today appointment statuses)  
  - [x] Recent appointments list (top 5)  
- [x] Decide stop behavior on logout (must stop all background pollers)  

### Exit criteria  
- [x] Choices are written here (or agreed verbally) and don’t change mid-build  

---  

## Phase 1 — Documentation improvements (Non-Obvious Behaviors + concurrency)  
### Tasks  
- [x] Expand “Non-Obvious Behaviors & Design Decisions”:  
  - [x] connection pool + contention risk  
  - [x] runtime schema init  
  - [x] one-time seeding behavior  
  - [x] appointment slot/conflict assumptions  
  - [x] reminder polling + `reminder_sent` behavior  
  - [x] controllers that bypass DAOs (where/how)  
  - [x] CSS heuristic behavior in `SceneNavigator`  
  - [x] RBAC being mostly UI-level  
- [x] Add new non-obvious note: **poller interactions** (dashboard refresh + reminders)  
- [x] Add a “Thread-safety rule” section:  
  - [x] background threads compute only  
  - [x] UI updates via `Platform.runLater(...)` only  

### Exit criteria  
- [x] Documentation updated and aligned with the final implementation  

---  

## Phase 2 — Real-time dashboard multithreading  
### Tasks  
- [x] Implement a repeating background updater (recommended: `ScheduledExecutorService` or JavaFX `ScheduledService`)  
- [x] Create a dashboard refresh method that:  
  - [x] queries DB (counts + status totals + recent appointments)  
  - [x] prepares a small in-memory “dashboard view model”  
- [x] Update UI safely:  
  - [x] `Platform.runLater(...)` for chart + labels + list/cards  
- [x] Prevent overlapping refresh executions:  
  - [x] lock/flag (e.g., `isRefreshing`) or fixed-delay scheduling  
- [x] Ensure cleanup on logout:  
  - [x] stop the scheduler/thread in `DashboardController#onLogout`  

### Exit criteria  
- [x] Dashboard metrics refresh automatically without freezing/crashing  
- [x] All background tasks stop when logging out  

---  

## Phase 3 — Health monitoring updates (multithreaded)  
### Tasks (pick one)  
- [x] Define the chosen option (A or B) above and document it  
- [x] Add the needed DAO/query path OR reuse existing DAO methods  
- [x] Add UI area on `Dashboard.fxml` (panel/list) to display updates  
- [x] Refresh health monitoring view:  
  - [x] reuse the same real-time scheduler or add a smaller separate one  
- [x] Stop refresh on logout  

### Exit criteria  
- [x] Health monitoring panel updates in real-time  

---  

## Phase 4 — Reliability & performance hardening  
### Tasks  
- [ ] Validate DB connection pool usage under parallel pollers (dashboard + reminders)  
- [ ] Reduce DB load if needed (interval tuning, selective refresh)  
- [ ] Add robust error handling:  
  - [ ] log background errors  
  - [ ] skip UI update if refresh fails  
- [ ] Ensure no UI memory leaks (clear and rebuild list nodes safely)  

### Exit criteria  
- [ ] Stable run for 10–20 minutes  

---  

## Phase 5 — Final verification + documentation sync  
### Tasks  
- [ ] Verify FXML wiring (`fx:id` correctness, controller methods exist)  
- [ ] Verify role-based behavior still works  
- [ ] Run compile/build and fix errors  
- [ ] Update documentation sections to match implementation  
- [ ] Final QA: login → dashboard updates → logout stops updates  

### Exit criteria  
- [ ] All implemented features work and docs match code  

---  

## Phase 6 — Signup (Doctor + Patient) with OTP verification before login  
### Tasks  
- [x] Decide OTP delivery approach: **email or SMS** (or both)
- [x] Add a “verification state” to the system (choose one strategy):  
  - [x] Add `users.is_verified` + `users.verified_at` columns  
  - [x] OR create a dedicated `otp_verifications` table and treat unverified users as “pending”
- [x] Create new database tables/columns for OTP lifecycle (expiration, attempts, verified timestamp).  
- [x] Create `SignUp.fxml` (or `RoleSignUp.fxml`) with:  
  - [x] Role selection (Doctor / Patient)  
  - [x] Required fields (name, email; doctor-specific: specialization/hospital/rate/hours; patient-specific: DOB/blood type/phone/address/emergency contact)  
  - [x] Password fields (with validation rules)  
- [x] Create `SignUpController` to:  
  - [x] Validate inputs  
  - [x] Register user in DB in an **unverified** state  
  - [x] Generate OTP + hash OTP for storage  
  - [x] Persist OTP record (expires at X minutes)  
  - [x] Trigger OTP “send” via a pluggable notifier  
- [x] Create `OTPVerification.fxml` + `OTPVerificationController` to:  
  - [x] Accept OTP input  
  - [x] Verify OTP (match + not expired + attempt limit)  
  - [x] Mark the account as verified  
  - [x] Navigate to Login screen on success  
- [x] Implement an `OTPService` (generation, hashing, verification, expiration/attempt limits).  
- [x] Implement `Notifier` abstraction + a concrete implementation (initially stubbed to console if no SMS/email provider exists yet):  
  - [x] `Notifier.sendOtp(destination, otp)`  
  - [x] Hook the notifier into the signup flow  
- [x] Update `AuthService#login` so users must be **verified** to sign in.  
- [x] Update seeded users (AdminSeeder / MockDataSeeder) so existing demo accounts are marked verified automatically (no OTP needed).  
- [x] Update navigation:  
  - [x] Add “Sign Up” entry from `Login.fxml`  
  - [ ] Ensure Doctor/Patient pages use correct role constraints after verification  

### Exit criteria  
- [x] Patient and Doctor can sign up and receive OTP verification  
- [x] Unverified accounts cannot log in  
- [x] Verified accounts can log in successfully  
- [x] Seeded admin/doctor/patient demo users remain able to log in (auto-verified)  

---  

## Phase 7 — Appointment management workflow (Doctor/Admin): confirm/reject/cancel/complete  
### Tasks  
- [x] Add doctor/admin appointment management UI (separate from booking UI):
  - [x] list of pending appointments for doctor
  - [x] view appointment details + patient concerns/notes
  - [x] buttons for Confirm / Cancel / Complete (and Reject if you represent it)
- [x] Implement controller event handlers that call:
  - [x] `AppointmentService#confirmAppointment`
  - [x] `AppointmentService#cancelAppointment`
  - [x] `AppointmentService#completeAppointment`
- [x] Enforce valid state transitions (service layer):
  - [x] PENDING → CONFIRMED or CANCELLED
  - [x] CONFIRMED → COMPLETED or CANCELLED (depending on policy)
  - [x] Block invalid transitions
- [x] Add audit reason capture (optional for MVP):  
  - [x] add “reason” field when cancelling/rejecting/completing  

### Exit criteria  
- [x] Doctor can manage their own appointments end-to-end
- [x] Appointment statuses update correctly and consistently

---  

## Phase 8 — Security hardening: enforce RBAC in service/DAO layer (not just UI hiding)  
### Tasks  
- [x] Identify every user action entry point (controllers) that performs mutations.
- [x] Add authorization guards in services, e.g.:  
  - [x] Only ADMIN can manage doctors/patients broadly  
  - [x] Only DOCTOR can manage appointments & create EHR entries for their patients (via appointment linkage)  
  - [x] Only PATIENT can view their own EHR/appointments  
- [x] Update `AppointmentService` and `HealthRecordDAO/Service` to accept current user context and verify ownership.  
- [x] Ensure “open page directly” doesn’t grant unauthorized capabilities (UI must not be sole protection).  

### Exit criteria  
- [x] Unauthorized roles cannot create/update/delete records even if they navigate manually  

---  

## Phase 9 — EHR access control: doctor scope + patient scope + appointment linkage constraints  
### Tasks  
- [x] Implement a secure “EHR write” rule:  
  - [x] Doctor can create/edit health records only for visits tied to an appointment they handled (or for their own patients per appointment history).  
  - [x] Patient can view only their own records (no edit/delete).  
- [x] Update `HealthRecordController` editing/deleting authorization (service-guarded).  
- [ ] Add DAO methods like:  
  - [ ] `findByPatientAndDoctor(patientId, doctorId)`  
  - [ ] `saveForAppointment(appointmentId, ...)` (preferred)  

### Exit criteria  
- [x] Doctors cannot edit records for other doctors’ patients (and patients are view-only)  

---  

## Phase 10 — Notifications: replace popup-only reminder with pluggable delivery  
### Tasks  
- [x] Create a `Notifier` interface for OTP + appointment reminders (separate responsibilities ok).  
- [x] Implement at least one concrete notifier for reminders:  
  - [ ] Console logger notifier for local dev  
  - [ ] (Optional later) SMTP email notifier  
  - [ ] (Optional later) SMS provider notifier  
- [ ] Add delivery audit data (success/failure) if needed.  
- [x] Ensure reminders are scheduled with reliable semantics:  
  - [x] prevent duplicate sends even under poller restart  

### Exit criteria  
- [x] ReminderService calls notifier and records reminder_sent only when delivery succeeds (or after “attempt”)  

---  

## Phase 11 — Admin reporting + audit trail (medico-legal + operational gap)  
### Tasks  
- [ ] Add database tables for audit events (choose one design):  
  - [ ] `audit_log` with event_type, actor_user_id, target_type, target_id, timestamp, metadata JSON/text  
  - [ ] OR separate audit tables per domain (appointments/records/users)  
- [ ] Log every mutation:  
  - [ ] appointment status change (+ old/new status + reason)  
  - [ ] health record create/update/delete  
  - [ ] doctor/patient account creation/deletion  
- [ ] Add admin reporting pages:  
  - [ ] appointment utilization per doctor  
  - [ ] monthly counts by status  
  - [ ] EHR activity volume  
- [ ] Add admin page for user account lifecycle management (optional but recommended):  
  - [ ] disable/reset password / set verified state (ties into OTP phase)  

### Exit criteria  
- [ ] Admin can view audit trail and meaningful operational reports  

---  

## Phase 12 — Data integrity: timezone + “appointment in the past” + working-hours enforcement  
### Tasks  
- [ ] Validate appointmentDatetime constraints in `AppointmentService`:  
  - [ ] reject scheduling in the past  
  - [ ] validate against working hours (hard enforce, not only for slot generation)  
- [ ] Make state transitions and conflict checks consistent with scheduling rules.  
- [ ] Add tests (even minimal) for conflict/state transition rules.  

### Exit criteria  
- [ ] System prevents inconsistent appointment states and invalid booking times  

---  

## Resume instructions  
- If you pause mid-build: restart from the **phase exit criteria**.  
- Never jump from Phase 0 → Phase 3 without completing:  
  - [x] Phase 2 (threading + stop-on-logout)
