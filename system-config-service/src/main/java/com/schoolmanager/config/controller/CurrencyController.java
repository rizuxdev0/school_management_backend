package com.schoolmanager.config.controller;

import com.schoolmanager.config.entity.Currency;
import com.schoolmanager.config.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system/currencies")
@RequiredArgsConstructor
public class CurrencyController {

    private final CurrencyRepository currencyRepository;

    @GetMapping
    public ResponseEntity<List<Currency>> getAllCurrencies() {
        return ResponseEntity.ok(currencyRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<Currency> createCurrency(@RequestBody Currency currency) {
        return ResponseEntity.ok(currencyRepository.save(currency));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCurrency(@PathVariable java.util.UUID id) {
        currencyRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
    @PostMapping("/{id}/set-default")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Currency> setDefaultCurrency(@PathVariable java.util.UUID id) {
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
