package com.schoolmanager.config.controller;

import com.schoolmanager.config.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/maintenance")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
public class DatabaseMaintenanceController {

    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final ClassroomRepository classroomRepository;
    private final AcademicCycleRepository academicCycleRepository;
    private final AcademicLevelRepository academicLevelRepository;
    private final AcademicYearRepository academicYearRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final GradingSystemRepository gradingSystemRepository;
    private final SubjectRepository subjectRepository;
    private final TuitionFeeRepository tuitionFeeRepository;
    private final StudentPaymentRepository studentPaymentRepository;
    private final AttendanceRepository attendanceRepository;
    private final BookRepository bookRepository;
    private final BookLoanRepository bookLoanRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final DisciplinaryIncidentRepository disciplinaryIncidentRepository;
    private final EvaluationRepository evaluationRepository;
    private final StudentGradeRepository studentGradeRepository;
    private final SystemSettingRepository systemSettingRepository;

    @GetMapping("/export/global")
    public ResponseEntity<Map<String, Object>> exportGlobalData() {
        Map<String, Object> data = new HashMap<>();
        data.put("students", studentRepository.findAll());
        data.put("enrollments", studentEnrollmentRepository.findAll());
        data.put("classrooms", classroomRepository.findAll());
        data.put("cycles", academicCycleRepository.findAll());
        data.put("levels", academicLevelRepository.findAll());
        data.put("years", academicYearRepository.findAll());
        data.put("periods", academicPeriodRepository.findAll());
        data.put("gradingSystems", gradingSystemRepository.findAll());
        data.put("subjects", subjectRepository.findAll());
        data.put("tuitionFees", tuitionFeeRepository.findAll());
        data.put("payments", studentPaymentRepository.findAll());
        data.put("attendances", attendanceRepository.findAll());
        data.put("books", bookRepository.findAll());
        data.put("bookLoans", bookLoanRepository.findAll());
        data.put("medicalRecords", medicalRecordRepository.findAll());
        data.put("disciplinaryIncidents", disciplinaryIncidentRepository.findAll());
        data.put("evaluations", evaluationRepository.findAll());
        data.put("studentGrades", studentGradeRepository.findAll());
        data.put("systemSettings", systemSettingRepository.findAll());
        return ResponseEntity.ok(data);
    }

    @GetMapping("/export/tenant/{tenantId}")
    public ResponseEntity<Map<String, Object>> exportTenantData(@PathVariable UUID tenantId) {
        Map<String, Object> data = new HashMap<>();
        data.put("students", studentRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("enrollments", studentEnrollmentRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("classrooms", classroomRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("cycles", academicCycleRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("levels", academicLevelRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("years", academicYearRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("periods", academicPeriodRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("gradingSystems", gradingSystemRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("subjects", subjectRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("tuitionFees", tuitionFeeRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("payments", studentPaymentRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("attendances", attendanceRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("books", bookRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("bookLoans", bookLoanRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("medicalRecords", medicalRecordRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("disciplinaryIncidents", disciplinaryIncidentRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("evaluations", evaluationRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("studentGrades", studentGradeRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        data.put("systemSettings", systemSettingRepository.findAll().stream().filter(x -> tenantId.equals(x.getTenantId())).toList());
        return ResponseEntity.ok(data);
    }

    @PostMapping("/reset")
    @Transactional
    public ResponseEntity<Void> resetDatabase() {
        // Suppression ordonnée pour respecter l'intégrité référentielle
        studentGradeRepository.deleteAll();
        evaluationRepository.deleteAll();
        attendanceRepository.deleteAll();
        disciplinaryIncidentRepository.deleteAll();
        medicalRecordRepository.deleteAll();
        bookLoanRepository.deleteAll();
        bookRepository.deleteAll();
        studentPaymentRepository.deleteAll();
        tuitionFeeRepository.deleteAll();
        studentEnrollmentRepository.deleteAll();
        studentRepository.deleteAll();
        subjectRepository.deleteAll();
        classroomRepository.deleteAll();
        academicPeriodRepository.deleteAll();
        academicLevelRepository.deleteAll();
        academicCycleRepository.deleteAll();
        academicYearRepository.deleteAll();
        gradingSystemRepository.deleteAll();
        systemSettingRepository.deleteAll();

        return ResponseEntity.ok().build();
    }
}
