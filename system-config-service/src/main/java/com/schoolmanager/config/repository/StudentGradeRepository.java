package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.StudentGrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentGradeRepository extends JpaRepository<StudentGrade, UUID> {
    List<StudentGrade> findByEvaluationId(UUID evaluationId);
    List<StudentGrade> findByStudentId(UUID studentId);
    Optional<StudentGrade> findByStudentIdAndEvaluationId(UUID studentId, UUID evaluationId);
}
