package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.BookLoan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface BookLoanRepository extends JpaRepository<BookLoan, UUID> {
    List<BookLoan> findByTenantId(UUID tenantId);
    List<BookLoan> findByStudentId(UUID studentId);
}
