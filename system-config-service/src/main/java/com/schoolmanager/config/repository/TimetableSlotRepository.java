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

    @org.springframework.data.jpa.repository.Query("SELECT t FROM TimetableSlot t WHERE t.tenantId = :tenantId AND t.academicYearId = :academicYearId " +
            "AND t.dayOfWeek = :dayOfWeek AND t.startTime < :endTime AND t.endTime > :startTime " +
            "AND (:id IS NULL OR t.id <> :id)")
    List<TimetableSlot> findOverlappingSlots(
            @org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("academicYearId") UUID academicYearId,
            @org.springframework.data.repository.query.Param("dayOfWeek") String dayOfWeek,
            @org.springframework.data.repository.query.Param("startTime") String startTime,
            @org.springframework.data.repository.query.Param("endTime") String endTime,
            @org.springframework.data.repository.query.Param("id") UUID id);
}
