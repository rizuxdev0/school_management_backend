package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Book;
import com.schoolmanager.config.entity.BookLoan;
import com.schoolmanager.config.entity.ExamConvocation;
import com.schoolmanager.config.entity.ExamSession;
import com.schoolmanager.config.entity.Student;
import com.schoolmanager.config.repository.BookLoanRepository;
import com.schoolmanager.config.repository.BookRepository;
import com.schoolmanager.config.repository.ExamConvocationRepository;
import com.schoolmanager.config.repository.ExamSessionRepository;
import com.schoolmanager.config.repository.StudentRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contrôleur REST pour les examens (sessions, convocations) et la bibliothèque (livres, prêts).
 * Supporte le bypass Super Admin et prévient les attaques IDOR.
 */
@RestController
@RequestMapping("/api/v1/system/exams-library")
@RequiredArgsConstructor
public class ExamsAndLibraryController {

    private final ExamSessionRepository examSessionRepository;
    private final ExamConvocationRepository examConvocationRepository;
    private final BookRepository bookRepository;
    private final BookLoanRepository bookLoanRepository;
    private final StudentRepository studentRepository;

    // ==================== 1. SESSIONS D'EXAMENS ====================

    @GetMapping("/exams/sessions/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('EXAMS_VIEW')")
    public ResponseEntity<List<ExamSession>> getExamSessions(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(examSessionRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/exams/sessions")
    @PreAuthorize("hasAuthority('EXAMS_EDIT')")
    public ResponseEntity<ExamSession> saveExamSession(@RequestBody ExamSession session) {
        if (!SecurityUtils.isSuperAdmin()) {
            session.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        return ResponseEntity.ok(examSessionRepository.save(session));
    }

    @DeleteMapping("/exams/sessions/{id}")
    @PreAuthorize("hasAuthority('EXAMS_EDIT')")
    public ResponseEntity<Void> deleteExamSession(@PathVariable UUID id) {
        ExamSession session = examSessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session d'examen introuvable"));
        SecurityUtils.assertOwnership(session.getTenantId());
        examSessionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. CONVOCATIONS AUX EXAMENS ====================

    @GetMapping("/exams/convocations/session/{sessionId}")
    @PreAuthorize("hasAuthority('EXAMS_VIEW')")
    public ResponseEntity<List<ExamConvocation>> getConvocations(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(examConvocationRepository.findByExamSessionId(sessionId));
    }

    @PostMapping("/exams/convocations")
    @PreAuthorize("hasAuthority('EXAMS_EDIT')")
    public ResponseEntity<ExamConvocation> saveConvocation(@RequestBody ExamConvocation convocation) {
        if (!SecurityUtils.isSuperAdmin()) {
            convocation.setTenantId(SecurityUtils.getCurrentTenantId());
        }

        // Resolve transient Student entity to prevent TransientPropertyValueException
        if (convocation.getStudent() != null && convocation.getStudent().getId() != null) {
            Student s = studentRepository.findById(convocation.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            SecurityUtils.assertOwnership(s.getTenantId());
            convocation.setStudent(s);
        }

        // Resolve transient ExamSession entity to prevent TransientPropertyValueException
        if (convocation.getExamSession() != null && convocation.getExamSession().getId() != null) {
            ExamSession session = examSessionRepository.findById(convocation.getExamSession().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session d'examen introuvable"));
            SecurityUtils.assertOwnership(session.getTenantId());
            convocation.setExamSession(session);
        }

        Optional<ExamConvocation> existing = examConvocationRepository.findByStudentIdAndExamSessionId(
                convocation.getStudent().getId(),
                convocation.getExamSession().getId()
        );
        if (existing.isPresent()) {
            ExamConvocation e = existing.get();
            e.setExamRoom(convocation.getExamRoom());
            e.setDeskNumber(convocation.getDeskNumber());
            e.setConvocationDate(convocation.getConvocationDate());
            e.setNotes(convocation.getNotes());
            return ResponseEntity.ok(examConvocationRepository.save(e));
        }
        return ResponseEntity.ok(examConvocationRepository.save(convocation));
    }

    @DeleteMapping("/exams/convocations/{id}")
    @PreAuthorize("hasAuthority('EXAMS_EDIT')")
    public ResponseEntity<Void> deleteConvocation(@PathVariable UUID id) {
        examConvocationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. CATALOGUE DE LA BIBLIOTHÈQUE ====================

    @GetMapping("/library/books/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    public ResponseEntity<List<Book>> getBooks(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(bookRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/library/books")
    @PreAuthorize("hasAuthority('LIBRARY_EDIT')")
    public ResponseEntity<Book> saveBook(@RequestBody Book book) {
        if (!SecurityUtils.isSuperAdmin()) {
            book.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        if (book.getId() == null) {
            book.setCopiesAvailable(book.getCopiesTotal());
        }
        return ResponseEntity.ok(bookRepository.save(book));
    }

    @DeleteMapping("/library/books/{id}")
    @PreAuthorize("hasAuthority('LIBRARY_EDIT')")
    public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livre introuvable"));
        SecurityUtils.assertOwnership(book.getTenantId());
        
        List<BookLoan> loans = bookLoanRepository.findByBookId(id);
        if (!loans.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Impossible de supprimer ce livre car il possède un historique de prêts. Veuillez l'Archiver à la place.");
        }
        
        bookRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 4. PRÊTS & RETOURS DE LIVRES ====================

    @GetMapping("/library/loans/tenant/{tenantId}")
    @PreAuthorize("hasAuthority('LIBRARY_VIEW')")
    public ResponseEntity<List<BookLoan>> getBookLoans(@PathVariable UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        return ResponseEntity.ok(bookLoanRepository.findByTenantId(jwtTenantId));
    }

    @PostMapping("/library/loans")
    @PreAuthorize("hasAuthority('LIBRARY_EDIT')")
    public ResponseEntity<BookLoan> saveBookLoan(@RequestBody BookLoan loan) {
        // En cas de retour de livre (restitution)
        if (loan.getId() != null) {
            BookLoan existing = bookLoanRepository.findById(loan.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable"));
            SecurityUtils.assertOwnership(existing.getTenantId());

            if ("ACTIVE".equals(existing.getStatus()) && "RETURNED".equals(loan.getStatus())) {
                existing.setStatus("RETURNED");
                existing.setReturnDate(LocalDate.now());
                Book book = existing.getBook();
                book.setCopiesAvailable(book.getCopiesAvailable() + 1);
                bookRepository.save(book);
            }
            return ResponseEntity.ok(bookLoanRepository.save(existing));
        }

        // Nouveau prêt : vérifier le livre et décrémenter les exemplaires disponibles
        Book book = bookRepository.findById(loan.getBook().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Livre introuvable"));
        SecurityUtils.assertOwnership(book.getTenantId());

        if (book.getCopiesAvailable() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun exemplaire disponible pour cet ouvrage.");
        }

        book.setCopiesAvailable(book.getCopiesAvailable() - 1);
        bookRepository.save(book);

        if (!SecurityUtils.isSuperAdmin()) {
            loan.setTenantId(SecurityUtils.getCurrentTenantId());
        }

        // Set properly resolved Book reference to prevent TransientPropertyValueException
        loan.setBook(book);

        // Resolve transient Student entity to prevent TransientPropertyValueException
        if (loan.getStudent() != null && loan.getStudent().getId() != null) {
            Student s = studentRepository.findById(loan.getStudent().getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Élève introuvable"));
            SecurityUtils.assertOwnership(s.getTenantId());
            loan.setStudent(s);
        }

        loan.setStatus("ACTIVE");
        return ResponseEntity.ok(bookLoanRepository.save(loan));
    }

    @DeleteMapping("/library/loans/{id}")
    @PreAuthorize("hasAuthority('LIBRARY_EDIT')")
    public ResponseEntity<Void> deleteBookLoan(@PathVariable UUID id) {
        BookLoan loan = bookLoanRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Prêt introuvable"));
        SecurityUtils.assertOwnership(loan.getTenantId());
        bookLoanRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
