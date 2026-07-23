package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Currency;
import com.schoolmanager.config.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Contrôleur REST pour la gestion des devises.
 * La lecture est accessible à tous les utilisateurs authentifiés ayant FINANCE_VIEW ou ACADEMIC_VIEW,
 * y compris le Super Admin SaaS.
 */
@RestController
@RequestMapping("/api/v1/system/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyRepository currencyRepository;

    /**
     * Liste toutes les devises. Accessible au Super Admin (qui reçoit FINANCE_VIEW via JwtFilter)
     * et aux utilisateurs normaux ayant FINANCE_VIEW ou ACADEMIC_VIEW.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_VIEW') or hasAuthority('ACADEMIC_VIEW')")
    public ResponseEntity<List<Currency>> getAllCurrencies() {
        return ResponseEntity.ok(currencyRepository.findAll());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Currency> createCurrency(@RequestBody Currency currency) {
        return ResponseEntity.ok(currencyRepository.save(currency));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Void> deleteCurrency(@PathVariable UUID id) {
        currencyRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/set-default")
    @org.springframework.transaction.annotation.Transactional
    @PreAuthorize("hasAuthority('FINANCE_EDIT')")
    public ResponseEntity<Currency> setDefaultCurrency(@PathVariable UUID id) {
        List<Currency> allCurrencies = currencyRepository.findAll();
        Currency target = null;
        for (Currency c : allCurrencies) {
            boolean isTarget = c.getId().equals(id);
            c.setIsSystemDefault(isTarget);
            if (isTarget) {
                target = c;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("Devise introuvable");
        }
        currencyRepository.saveAll(allCurrencies);
        return ResponseEntity.ok(target);
    }
}
