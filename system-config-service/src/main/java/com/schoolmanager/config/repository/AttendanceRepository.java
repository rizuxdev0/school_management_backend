package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.Attendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, UUID> {
    List<Attendance> findByClassroomIdAndAttendanceDate(UUID classroomId, LocalDate attendanceDate);
    List<Attendance> findByStudentId(UUID studentId);
    Optional<Attendance> findByStudentIdAndAttendanceDate(UUID studentId, LocalDate attendanceDate);
}
