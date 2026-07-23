package com.schoolmanager.config.controller;

import com.schoolmanager.config.service.BulletinReportService;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Contrôleur REST pour la génération et le téléchargement des bulletins scolaires en PDF.
 * Utilise JasperReports pour produire des documents professionnels avec l'identité visuelle
 * de chaque établissement (logo, en-tête, filigrane, pied de page, signatures).
 *
 * Sécurité : @PreAuthorize + isolation multi-tenant via SecurityUtils.
 */
@RestController
@RequestMapping("/api/v1/system/evaluations/bulletins")
@RequiredArgsConstructor
public class ReportCardController {

    private final BulletinReportService bulletinReportService;

    /**
     * Génère et télécharge les bulletins PDF de TOUTE la classe.
     * Produit un PDF multi-pages (1 page = 1 bulletin élève).
     *
     * @param classroomId UUID de la classe
     * @param periodId    UUID de la période
     * @param yearId      UUID de l'année académique
     * @return Réponse HTTP avec le fichier PDF en attachement
     */
    @GetMapping("/classroom/{classroomId}/period/{periodId}/year/{yearId}")
    @PreAuthorize("hasAuthority('EVALUATION_VIEW')")
    public ResponseEntity<byte[]> downloadClassBulletins(
            @PathVariable UUID classroomId,
            @PathVariable UUID periodId,
            @PathVariable UUID yearId) {

        byte[] pdfBytes = bulletinReportService.generateClassBulletins(classroomId, periodId, yearId);

        String filename = "bulletins_classe_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                .body(pdfBytes);
    }

    /**
     * Génère et télécharge le bulletin PDF d'UN SEUL élève.
     *
     * @param studentId UUID de l'élève
     * @param periodId  UUID de la période
     * @param yearId    UUID de l'année académique
     * @return Réponse HTTP avec le fichier PDF en attachement
     */
    @GetMapping("/student/{studentId}/period/{periodId}/year/{yearId}")
    @PreAuthorize("hasAuthority('EVALUATION_VIEW')")
    public ResponseEntity<byte[]> downloadStudentBulletin(
            @PathVariable UUID studentId,
            @PathVariable UUID periodId,
            @PathVariable UUID yearId) {

        byte[] pdfBytes = bulletinReportService.generateStudentBulletin(studentId, periodId, yearId);

        String filename = "bulletin_" + studentId + "_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                .body(pdfBytes);
    }

    /**
     * Génère un aperçu PDF pour un template de bulletin spécifique.
     * Accessible à tous les utilisateurs ayant EVALUATION_VIEW ou ACADEMIC_VIEW.
     *
     * @param templateCode Le code du design (CLASSIC, STRUCTURED, PROFESSIONAL, MINIMAL)
     * @return Réponse HTTP avec le fichier PDF d'aperçu
     */
    @GetMapping("/preview/{templateCode}")
    @PreAuthorize("hasAuthority('EVALUATION_VIEW') or hasAuthority('ACADEMIC_VIEW') or hasAuthority('SETTINGS_VIEW')")
    public ResponseEntity<byte[]> downloadTemplatePreview(@PathVariable String templateCode) {
        byte[] pdfBytes = bulletinReportService.generateTemplatePreview(templateCode);

        String filename = "apercu_bulletin_" + templateCode.toLowerCase() + ".pdf";

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdfBytes.length))
                .body(pdfBytes);
    }
}
