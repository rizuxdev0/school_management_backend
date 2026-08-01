package com.schoolmanager.config.security;

import com.schoolmanager.config.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

/**
 * Intercepteur AOP Spring qui capture automatiquement les actions de modification
 * (POST/PUT/DELETE) effectuées sur tous les contrôleurs REST du package {@code controller}.
 *
 * <p>Fonctionnement :
 * <ol>
 *   <li>Intercepte les méthodes annotées {@code @PostMapping}, {@code @PutMapping},
 *       ou {@code @DeleteMapping} dans le package {@code controller}.</li>
 *   <li>Exclut automatiquement les endpoints d'audit (pour éviter la récursion).</li>
 *   <li>Détermine le type d'action (CREATE/UPDATE/DELETE) et le type d'entité
 *       à partir du nom du contrôleur et de la méthode.</li>
 *   <li>Délègue l'écriture du log à {@link AuditLogService} (asynchrone).</li>
 * </ol>
 * </p>
 *
 * <p><strong>Aucune modification des contrôleurs existants n'est nécessaire.</strong></p>
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogService auditLogService;

    /**
     * Pointcut ciblant toutes les méthodes publiques dans le package controller
     * du system-config-service.
     */
    @Pointcut("execution(* com.schoolmanager.config.controller..*(..))")
    public void controllerMethods() {}

    /**
     * Intercepte les méthodes POST (création) après leur exécution réussie.
     * Exclut le contrôleur d'audit pour éviter la récursion infinie.
     */
    @AfterReturning(pointcut = "controllerMethods() && @annotation(postMapping)", returning = "result")
    public void afterPost(JoinPoint joinPoint, PostMapping postMapping, Object result) {
        if (isAuditController(joinPoint)) return;
        logAction(joinPoint, "CREATE", result);
    }

    /**
     * Intercepte les méthodes PUT (modification) après leur exécution réussie.
     */
    @AfterReturning(pointcut = "controllerMethods() && @annotation(putMapping)", returning = "result")
    public void afterPut(JoinPoint joinPoint, PutMapping putMapping, Object result) {
        if (isAuditController(joinPoint)) return;
        logAction(joinPoint, "UPDATE", result);
    }

    /**
     * Intercepte les méthodes DELETE (suppression) après leur exécution réussie.
     */
    @AfterReturning(pointcut = "controllerMethods() && @annotation(deleteMapping)", returning = "result")
    public void afterDelete(JoinPoint joinPoint, DeleteMapping deleteMapping, Object result) {
        if (isAuditController(joinPoint)) return;
        logAction(joinPoint, "DELETE", result);
    }

    // ------------------------------------------------------------------
    // Logique interne
    // ------------------------------------------------------------------

    /**
     * Construit et enregistre l'entrée d'audit à partir du contexte du JoinPoint.
     */
    private void logAction(JoinPoint joinPoint, String action, Object result) {
        try {
            String controllerName = joinPoint.getTarget().getClass().getSimpleName();
            String methodName = joinPoint.getSignature().getName();

            // Déduire le type d'entité du nom du contrôleur
            // Ex: "StudentAcademicsController" → "StudentAcademics"
            //     "AttendanceAndFinanceController" → "AttendanceAndFinance"
            String entityType = controllerName.replace("Controller", "");

            // Extraire l'ID de l'entité depuis la valeur de retour si possible
            String entityId = extractEntityId(result);

            // Description lisible
            String description = buildDescription(action, entityType, methodName);

            auditLogService.log(action, entityType, entityId, description);
        } catch (Exception e) {
            // Ne jamais propager — l'audit ne doit pas casser la requête
            log.warn("Erreur dans l'intercepteur d'audit : {}", e.getMessage());
        }
    }

    /**
     * Vérifie si le JoinPoint cible le contrôleur d'audit (anti-récursion).
     */
    private boolean isAuditController(JoinPoint joinPoint) {
        return joinPoint.getTarget().getClass().getSimpleName().contains("AuditLog");
    }

    /**
     * Tente d'extraire l'identifiant de l'entité depuis la réponse du contrôleur.
     * Supporte ResponseEntity contenant un objet avec un champ "id" (via réflexion).
     */
    private String extractEntityId(Object result) {
        try {
            Object body = result;
            if (result instanceof ResponseEntity<?> re) {
                body = re.getBody();
            }
            if (body == null) return null;

            // Tenter d'accéder au champ "id" via getter
            Method getId = body.getClass().getMethod("getId");
            Object id = getId.invoke(body);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            // Pas de champ "id" accessible — ce n'est pas grave
            return null;
        }
    }

    /**
     * Construit une description lisible de l'action pour le journal.
     */
    private String buildDescription(String action, String entityType, String methodName) {
        String actionLabel = switch (action) {
            case "CREATE" -> "Création";
            case "UPDATE" -> "Modification";
            case "DELETE" -> "Suppression";
            default -> action;
        };
        // Humaniser le nom de la méthode (ex: "createStudent" → "createStudent")
        return String.format("%s via %s.%s", actionLabel, entityType, methodName);
    }
}
