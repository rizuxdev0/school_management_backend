package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.repository.*;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service métier dédié à la scolarité :
 * Gestion des classes, élèves, inscriptions annuelles, emplois du temps, salles et liens parents-élèves.
 */
@Service
@RequiredArgsConstructor
public class StudentAcademicsService {

    private final ClassroomRepository classroomRepository;
    private final StudentRepository studentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TimetableSlotRepository timetableSlotRepository;
    private final RoomRepository roomRepository;
    private final AcademicReportService academicReportService;
    private final TimetableReportService timetableReportService;

    // ==================== 1. CLASSES / CLASSROOMS ====================

    @Transactional(readOnly = true)
    public List<Classroom> getClassroomsByTenant(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return classroomRepository.findByTenantId(jwtTenantId);
    }

    @Transactional
    public Classroom saveClassroom(Classroom classroom) {
        if (!SecurityUtils.isSuperAdmin()) {
            classroom.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return classroomRepository.save(classroom);
    }

    @Transactional
    public void deleteClassroom(UUID id) {
        Classroom classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
        SecurityUtils.assertOwnership(classroom.getTenantId());
        classroomRepository.deleteById(id);
    }

    // ==================== 2. ÉLÈVES / STUDENTS ====================

    @Transactional(readOnly = true)
    public List<Student> getStudentsByTenant(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return studentRepository.findByTenantId(jwtTenantId);
    }

    @Transactional
    public Student saveStudent(Student student) {
        if (!SecurityUtils.isSuperAdmin()) {
            student.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (student.getRegistrationNumber() == null || student.getRegistrationNumber().trim().isEmpty()) {
            String yearCode = String.valueOf(LocalDate.now().getYear());
            long count = studentRepository.count() + 1;
            student.setRegistrationNumber("MAT-" + yearCode + "-" + String.format("%04d", count));
        }
        return studentRepository.save(student);
    }

    @Transactional
    public void deleteStudent(UUID id) {
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
        SecurityUtils.assertOwnership(student.getTenantId());
        studentRepository.deleteById(id);
    }

    // ==================== 3. INSCRIPTIONS / ENROLLMENTS ====================

    @Transactional(readOnly = true)
    public List<StudentEnrollment> getEnrollments(UUID tenantId, UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return studentEnrollmentRepository.findByTenantIdAndAcademicYearId(jwtTenantId, yearId);
    }

    @Transactional(readOnly = true)
    public List<StudentEnrollment> getEnrollmentsByClassroom(UUID classroomId, UUID yearId) {
        Classroom c = classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
        SecurityUtils.assertOwnership(c.getTenantId());
        return studentEnrollmentRepository.findByClassroomIdAndAcademicYearId(classroomId, yearId);
    }

    @Transactional
    public StudentEnrollment enrollStudent(StudentEnrollment enrollment) {
        if (!SecurityUtils.isSuperAdmin()) {
            enrollment.setTenantId(SecurityUtils.getCurrentTenantId());
        }

        if (enrollment.getStudent() != null && enrollment.getStudent().getId() != null) {
            Student s = studentRepository.findById(enrollment.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            SecurityUtils.assertOwnership(s.getTenantId());
            enrollment.setStudent(s);
        }

        if (enrollment.getClassroom() != null && enrollment.getClassroom().getId() != null) {
            Classroom c = classroomRepository.findById(enrollment.getClassroom().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Classe introuvable"));
            SecurityUtils.assertOwnership(c.getTenantId());
            enrollment.setClassroom(c);
        }

        studentEnrollmentRepository.findByStudentIdAndAcademicYearId(
                enrollment.getStudent().getId(),
                enrollment.getAcademicYearId()
        ).ifPresent(existing -> enrollment.setId(existing.getId()));

        return studentEnrollmentRepository.save(enrollment);
    }

    @Transactional
    public void deleteEnrollment(UUID id) {
        StudentEnrollment enrollment = studentEnrollmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inscription introuvable"));
        SecurityUtils.assertOwnership(enrollment.getTenantId());
        studentEnrollmentRepository.deleteById(id);
    }

    // ==================== 4. EMPLOIS DU TEMPS & DÉTECTION CONFLITS ====================

    @Transactional(readOnly = true)
    public List<TimetableSlot> getTimetableByYear(UUID tenantId, UUID yearId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return timetableSlotRepository.findByTenantIdAndAcademicYearIdOrderByDayOfWeekAscStartTimeAsc(jwtTenantId, yearId);
    }

    @Transactional(readOnly = true)
    public List<TimetableSlot> getTimetableByClassroom(UUID tenantId, UUID yearId, UUID classroomId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return timetableSlotRepository.findByTenantIdAndAcademicYearIdAndClassroomIdOrderByDayOfWeekAscStartTimeAsc(jwtTenantId, yearId, classroomId);
    }

    @Transactional
    public TimetableSlot saveTimetableSlot(TimetableSlot slot) {
        UUID tenantId = SecurityUtils.isSuperAdmin() ? slot.getTenantId() : SecurityUtils.getCurrentTenantId();
        if (tenantId == null) {
            tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
        }
        slot.setTenantId(tenantId);

        if (slot.getStartTime() == null || slot.getEndTime() == null || slot.getStartTime().compareTo(slot.getEndTime()) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "L'heure de début doit être antérieure à l'heure de fin.");
        }

        List<TimetableSlot> overlapping = timetableSlotRepository.findOverlappingSlots(
                slot.getTenantId(),
                slot.getAcademicYearId(),
                slot.getDayOfWeek(),
                slot.getStartTime(),
                slot.getEndTime(),
                slot.getId()
        );

        for (TimetableSlot match : overlapping) {
            if (slot.getClassroomId().equals(match.getClassroomId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("La classe a déjà un cours programmé (%s) de %s à %s.",
                                match.getSubjectNameFr(), match.getStartTime(), match.getEndTime()));
            }

            if (slot.getTeacherName() != null && !slot.getTeacherName().trim().isEmpty() &&
                    match.getTeacherName() != null && slot.getTeacherName().equalsIgnoreCase(match.getTeacherName())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("L'enseignant %s est déjà programmé pour un cours de %s à %s.",
                                slot.getTeacherName(), match.getStartTime(), match.getEndTime()));
            }

            if (slot.getRoom() != null && !slot.getRoom().trim().isEmpty() &&
                    match.getRoom() != null && slot.getRoom().equalsIgnoreCase(match.getRoom())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("La salle %s est déjà occupée de %s à %s par un autre cours.",
                                slot.getRoom(), match.getStartTime(), match.getEndTime()));
            }
        }

        return timetableSlotRepository.save(slot);
    }

    @Transactional
    public void deleteTimetableSlot(UUID id) {
        TimetableSlot slot = timetableSlotRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Créneau horaire introuvable"));
        SecurityUtils.assertOwnership(slot.getTenantId());
        timetableSlotRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public byte[] downloadTimetablePdf(UUID tenantId, UUID yearId, UUID classroomId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return timetableReportService.generateClassroomTimetablePdf(jwtTenantId, yearId, classroomId);
    }

    // ==================== 5. SALLES & INFRASTRUCTURES ====================

    @Transactional
    public List<Room> getRoomsByTenant(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        List<Room> rooms = roomRepository.findByTenantId(jwtTenantId);
        if (rooms.isEmpty()) {
            rooms = List.of(
                    Room.builder().tenantId(jwtTenantId).code("S-101").name("Salle 101 (RDC)").capacity(40).roomType("CLASSROOM").building("Bâtiment Principal").isActive(true).build(),
                    Room.builder().tenantId(jwtTenantId).code("S-102").name("Salle 102 (RDC)").capacity(40).roomType("CLASSROOM").building("Bâtiment Principal").isActive(true).build(),
                    Room.builder().tenantId(jwtTenantId).code("AMPHI-A").name("Amphithéâtre A (Central)").capacity(150).roomType("AMPHITHEATER").building("Bâtiment A").isActive(true).build(),
                    Room.builder().tenantId(jwtTenantId).code("LAB-INFO").name("Laboratoire Informatique 1").capacity(30).roomType("LABORATORY").building("Bâtiment des Sciences").isActive(true).build()
            );
            roomRepository.saveAll(rooms);
            rooms = roomRepository.findByTenantId(jwtTenantId);
        }
        return rooms;
    }

    @Transactional
    public Room saveRoom(Room room) {
        if (!SecurityUtils.isSuperAdmin()) {
            room.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return roomRepository.save(room);
    }

    @Transactional
    public void deleteRoom(UUID id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Salle / Amphi introuvable"));
        SecurityUtils.assertOwnership(room.getTenantId());
        roomRepository.deleteById(id);
    }

    // ==================== 6. RAPPORTS PDF ACADÉMIQUES ====================

    @Transactional(readOnly = true)
    public byte[] downloadStudentsPdf(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return academicReportService.generateStudentsListPdf(jwtTenantId);
    }

    @Transactional(readOnly = true)
    public byte[] downloadClassroomsPdf(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return academicReportService.generateClassroomsListPdf(jwtTenantId);
    }

    @Transactional(readOnly = true)
    public byte[] downloadRoomsPdf(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return academicReportService.generateRoomsListPdf(jwtTenantId);
    }

    @Transactional(readOnly = true)
    public byte[] getSchoolCertificate(UUID studentId, String lang, boolean isParent, String parentPhone, String parentEmail) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        if (isParent) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            boolean phoneMatches = student.getParentPhone() != null && student.getParentPhone().equals(parentPhone);
            boolean emailMatches = student.getEmail() != null && student.getEmail().equals(parentEmail);
            if (!phoneMatches && !emailMatches) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès non autorisé au certificat de cet élève");
            }
        }

        return academicReportService.generateSchoolCertificatePdf(tenantId, studentId, lang);
    }

    // ==================== 7. LIAISON PARENT - ÉLÈVES ====================

    @Transactional
    public void linkParentToStudents(String parentPhone, List<UUID> studentIds) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        List<Student> previousLinked = studentRepository.findByTenantIdAndParentPhone(tenantId, parentPhone);
        for (Student s : previousLinked) {
            if (!studentIds.contains(s.getId())) {
                s.setParentPhone(null);
                studentRepository.save(s);
            }
        }

        for (UUID studentId : studentIds) {
            Student student = studentRepository.findById(studentId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable : " + studentId));
            SecurityUtils.assertOwnership(student.getTenantId());
            student.setParentPhone(parentPhone);
            studentRepository.save(student);
        }
    }

    @Transactional(readOnly = true)
    public List<Student> getStudentsByParentPhone(String parentPhone) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();
        return studentRepository.findByTenantIdAndParentPhone(tenantId, parentPhone);
    }
}
