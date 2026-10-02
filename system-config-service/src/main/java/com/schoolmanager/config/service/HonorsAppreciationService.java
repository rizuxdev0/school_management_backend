package com.schoolmanager.config.service;

import com.schoolmanager.config.entity.HonorsAppreciationRule;
import com.schoolmanager.config.repository.HonorsAppreciationRuleRepository;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service métier pour la gestion des règles de mentions d'honneur et appréciations scolaires.
 * Permet aux directeurs et administrateurs d'établissement de définir et paramétrer
 * librement leurs tranches de moyennes et leurs intitulés personnalisés.
 */
@Service
@RequiredArgsConstructor
public class HonorsAppreciationService {

    private final HonorsAppreciationRuleRepository repository;

    /**
     * Récupère la liste des règles de mentions pour un tenant.
     * Si aucune règle n'a encore été configurée, initialise automatiquement les règles par défaut standard.
     */
    @Transactional
    public List<HonorsAppreciationRule> getOrCreateRulesForTenant(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        List<HonorsAppreciationRule> rules = repository.findByTenantIdOrderByMinScoreDesc(jwtTenantId);

        if (rules.isEmpty()) {
            rules = createDefaultRules(jwtTenantId);
            repository.saveAll(rules);
            rules = repository.findByTenantIdOrderByMinScoreDesc(jwtTenantId);
        }
        return rules;
    }

    /**
     * Enregistre ou met à jour une règle individuelle de mention.
     */
    @Transactional
    public HonorsAppreciationRule saveRule(HonorsAppreciationRule rule) {
        if (!SecurityUtils.isSuperAdmin()) {
            rule.setTenantId(SecurityUtils.getCurrentTenantId());
        }
        validateRuleScores(rule);
        return repository.save(rule);
    }

    /**
     * Enregistre une liste complète de règles (mise à jour en lot).
     */
    @Transactional
    public List<HonorsAppreciationRule> saveAllRules(UUID tenantId, List<HonorsAppreciationRule> rules) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);

        // Validation des règles
        for (int i = 0; i < rules.size(); i++) {
            HonorsAppreciationRule r = rules.get(i);
            r.setTenantId(jwtTenantId);
            r.setDisplayOrder(i + 1);
            validateRuleScores(r);
        }

        // Remplacement complet des anciennes règles
        repository.deleteByTenantId(jwtTenantId);
        return repository.saveAll(rules);
    }

    /**
     * Supprime une règle de mention par son ID.
     */
    @Transactional
    public void deleteRule(UUID id) {
        HonorsAppreciationRule rule = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Règle de mention introuvable"));
        SecurityUtils.assertOwnership(rule.getTenantId());
        repository.deleteById(id);
    }

    /**
     * Réinitialise le barème de mentions d'un établissement avec le barème standard par défaut.
     */
    @Transactional
    public List<HonorsAppreciationRule> resetToDefaults(UUID tenantId) {
        UUID jwtTenantId = SecurityUtils.getTenantIdToUse(tenantId);
        repository.deleteByTenantId(jwtTenantId);
        List<HonorsAppreciationRule> defaultRules = createDefaultRules(jwtTenantId);
        return repository.saveAll(defaultRules);
    }

    /**
     * Évalue dynamiquement l'appréciation ou mention d'honneur correspondant à une moyenne générale donnée
     * en appliquant les règles configurées par l'établissement.
     */
    public String evaluateAppreciation(BigDecimal generalAverage, List<HonorsAppreciationRule> rules) {
        if (generalAverage == null) return "Non évalué";

        if (rules != null && !rules.isEmpty()) {
            for (HonorsAppreciationRule rule : rules) {
                if (rule.getMinScore() != null && rule.getMaxScore() != null) {
                    if (generalAverage.compareTo(rule.getMinScore()) >= 0 && generalAverage.compareTo(rule.getMaxScore()) <= 0) {
                        return rule.getMentionFr();
                    }
                }
            }
        }

        // Fallback standard si aucune tranche spécifique ne match
        double val = generalAverage.doubleValue();
        if (val >= 16.0) return "Félicitations du Conseil";
        if (val >= 14.0) return "Tableau d'Honneur";
        if (val >= 12.0) return "Encouragements";
        if (val >= 10.0) return "Passable / Travail satisfaisant";
        if (val >= 8.0) return "Insuffisant / Doit redoubler d'efforts";
        return "Avertissement Travail";
    }

    private void validateRuleScores(HonorsAppreciationRule rule) {
        if (rule.getMinScore() == null || rule.getMaxScore() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Les notes minimale et maximale sont obligatoires");
        }
        if (rule.getMinScore().compareTo(rule.getMaxScore()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La note minimale (" + rule.getMinScore() + ") ne peut être supérieure à la note maximale (" + rule.getMaxScore() + ")");
        }
    }

    private List<HonorsAppreciationRule> createDefaultRules(UUID tenantId) {
        List<HonorsAppreciationRule> list = new ArrayList<>();
        list.add(HonorsAppreciationRule.builder()
                .tenantId(tenantId)
                .code("EXCELLENT")
                .mentionFr("Félicitations du Conseil")
                .mentionEn("Congratulations of the Board")
                .minScore(new BigDecimal("16.00"))
                .maxScore(new BigDecimal("20.00"))
                .colorCode("#10b981")
                .displayOrder(1)
                .build());

        list.add(HonorsAppreciationRule.builder()
                .tenantId(tenantId)
                .code("VERY_GOOD")
                .mentionFr("Tableau d'Honneur")
                .mentionEn("Honor Roll")
                .minScore(new BigDecimal("14.00"))
                .maxScore(new BigDecimal("15.99"))
                .colorCode("#3b82f6")
                .displayOrder(2)
                .build());

        list.add(HonorsAppreciationRule.builder()
                .tenantId(tenantId)
                .code("GOOD")
                .mentionFr("Encouragements")
                .mentionEn("Encouragements")
                .minScore(new BigDecimal("12.00"))
                .maxScore(new BigDecimal("13.99"))
                .colorCode("#6366f1")
                .displayOrder(3)
                .build());

        list.add(HonorsAppreciationRule.builder()
                .tenantId(tenantId)
                .code("PASS")
                .mentionFr("Passable / Travail satisfaisant")
                .mentionEn("Satisfactory")
                .minScore(new BigDecimal("10.00"))
                .maxScore(new BigDecimal("11.99"))
                .colorCode("#8b5cf6")
                .displayOrder(4)
                .build());

        list.add(HonorsAppreciationRule.builder()
                .tenantId(tenantId)
                .code("POOR")
                .mentionFr("Insuffisant / Doit redoubler d'efforts")
                .mentionEn("Needs Improvement")
                .minScore(new BigDecimal("8.00"))
                .maxScore(new BigDecimal("9.99"))
                .colorCode("#f59e0b")
                .displayOrder(5)
                .build());

        list.add(HonorsAppreciationRule.builder()
                .tenantId(tenantId)
                .code("WARNING")
                .mentionFr("Avertissement Travail")
                .mentionEn("Academic Warning")
                .minScore(new BigDecimal("0.00"))
                .maxScore(new BigDecimal("7.99"))
                .colorCode("#ef4444")
                .displayOrder(6)
                .build());

        return list;
    }
}
