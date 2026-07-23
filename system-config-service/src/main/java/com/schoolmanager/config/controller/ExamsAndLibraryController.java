package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Book;
import com.schoolmanager.config.entity.BookLoan;
import com.schoolmanager.config.entity.ExamConvocation;
import com.schoolmanager.config.entity.ExamSession;
import com.schoolmanager.config.repository.BookLoanRepository;
import com.schoolmanager.config.repository.BookRepository;
import com.schoolmanager.config.repository.ExamConvocationRepository;
import com.schoolmanager.config.repository.ExamSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/system/exams-library")
@RequiredArgsConstructor
public class ExamsAndLibraryController {

    private final ExamSessionRepository examSessionRepository;
    private final ExamConvocationRepository examConvocationRepository;
    private final BookRepository bookRepository;
    private final BookLoanRepository bookLoanRepository;

    // ==================== 1. SESSIONS D'EXAMENS ====================

    @GetMapping("/exams/sessions/tenant/{tenantId}")
    public ResponseEntity<List<ExamSession>> getExamSessions(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(examSessionRepository.findByTenantId(tenantId));
    }

    @PostMapping("/exams/sessions")
    public ResponseEntity<ExamSession> saveExamSession(@RequestBody ExamSession session) {
        return ResponseEntity.ok(examSessionRepository.save(session));
    }

    @DeleteMapping("/exams/sessions/{id}")
    public ResponseEntity<Void> deleteExamSession(@PathVariable UUID id) {
        examSessionRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 2. CONVOCATIONS AUX EXAMENS ====================

    @GetMapping("/exams/convocations/session/{sessionId}")
    public ResponseEntity<List<ExamConvocation>> getConvocations(@PathVariable UUID sessionId) {
        return ResponseEntity.ok(examConvocationRepository.findByExamSessionId(sessionId));
    }

    @PostMapping("/exams/convocations")
    public ResponseEntity<ExamConvocation> saveConvocation(@RequestBody ExamConvocation convocation) {
        // Unique check to update if already exists
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
    public ResponseEntity<Void> deleteConvocation(@PathVariable UUID id) {
        examConvocationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 3. CATALOGUE DE LA BIBLIOTHÈQUE ====================

    @GetMapping("/library/books/tenant/{tenantId}")
    public ResponseEntity<List<Book>> getBooks(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(bookRepository.findByTenantId(tenantId));
    }

    @PostMapping("/library/books")
    public ResponseEntity<Book> saveBook(@RequestBody Book book) {
        if (book.getId() == null) {
            book.setCopiesAvailable(book.getCopiesTotal());
        }
        return ResponseEntity.ok(bookRepository.save(book));
    }

    @DeleteMapping("/library/books/{id}")
    public ResponseEntity<Void> deleteBook(@PathVariable UUID id) {
        bookRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ==================== 4. PRÊTS & RETOURS DE LIVRES ====================

    @GetMapping("/library/loans/tenant/{tenantId}")
    public ResponseEntity<List<BookLoan>> getBookLoans(@PathVariable UUID tenantId) {
        return ResponseEntity.ok(bookLoanRepository.findByTenantId(tenantId));
    }

    @PostMapping("/library/loans")
    public ResponseEntity<BookLoan> saveBookLoan(@RequestBody BookLoan loan) {
        // En cas de retour de livre (restitution)
        if (loan.getId() != null) {
            BookLoan existing = bookLoanRepository.findById(loan.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Prêt introuvable"));

            if ("ACTIVE".equals(existing.getStatus()) && "RETURNED".equals(loan.getStatus())) {
                existing.setStatus("RETURNED");
                existing.setReturnDate(LocalDate.now());

                // Increment available copies of the book
                Book book = existing.getBook();
                book.setCopiesAvailable(book.getCopiesAvailable() + 1);
                bookRepository.save(book);
            }
            return ResponseEntity.ok(bookLoanRepository.save(existing));
        }

        // Nouveau prêt : décrémenter le nombre d'exemplaires disponibles
        Book book = bookRepository.findById(loan.getBook().getId())
                .orElseThrow(() -> new IllegalArgumentException("Livre introuvable"));

        if (book.getCopiesAvailable() <= 0) {
            throw new IllegalStateException("Aucun exemplaire disponible pour cet ouvrage.");
        }

        book.setCopiesAvailable(book.getCopiesAvailable() - 1);
        bookRepository.save(book);

        loan.setStatus("ACTIVE");
        return ResponseEntity.ok(bookLoanRepository.save(loan));
    }

    @DeleteMapping("/library/loans/{id}")
    public ResponseEntity<Void> deleteBookLoan(@PathVariable UUID id) {
        bookLoanRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
