package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, UUID> {

    List<TimetableSlot> findByTenantIdAndAcademicYearIdOrderByDayOfWeekAscStartTimeAsc(UUID tenantId, UUID academicYearId);

    List<TimetableSlot> findByTenantIdAndAcademicYearIdAndClassroomIdOrderByDayOfWeekAscStartTimeAsc(UUID tenantId, UUID academicYearId, UUID classroomId);

    List<TimetableSlot> findByTenantIdAndClassroomIdOrderByDayOfWeekAscStartTimeAsc(UUID tenantId, UUID classroomId);

    List<TimetableSlot> findByTenantIdAndAcademicYearIdAndTeacherNameIgnoreCaseOrderByDayOfWeekAscStartTimeAsc(UUID tenantId, UUID academicYearId, String teacherName);
}
