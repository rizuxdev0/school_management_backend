package com.schoolmanager.config;

import com.schoolmanager.config.entity.Currency;
import com.schoolmanager.config.repository.CurrencyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Seeder automatique pour insérer les devises mondiales par défaut si la table est vide.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CurrencyDataSeeder implements CommandLineRunner {

    private final CurrencyRepository currencyRepository;

    @Override
    public void run(String... args) throws Exception {
        if (currencyRepository.count() == 0) {
            log.info("Aucune devise trouvée en base de données. Lancement du seeding par défaut...");
            
            List<Currency> defaultCurrencies = Arrays.asList(
                Currency.builder().code("USD").symbol("$").nameFr("Dollar américain").nameEn("US Dollar").decimalDigits(2).exchangeRate(new java.math.BigDecimal("1.08")).isActive(true).build(),
                Currency.builder().code("EUR").symbol("€").nameFr("Euro").nameEn("Euro").decimalDigits(2).exchangeRate(new java.math.BigDecimal("1.0")).isActive(true).build(),
                Currency.builder().code("XOF").symbol("F CFA").nameFr("Franc CFA (BCEAO)").nameEn("CFA Franc (BCEAO)").decimalDigits(0).exchangeRate(new java.math.BigDecimal("655.957")).isActive(true).isSystemDefault(true).build(),
                Currency.builder().code("CAD").symbol("C$").nameFr("Dollar canadien").nameEn("Canadian Dollar").decimalDigits(2).exchangeRate(new java.math.BigDecimal("1.48")).isActive(true).build(),
                Currency.builder().code("GBP").symbol("£").nameFr("Livre sterling").nameEn("British Pound").decimalDigits(2).exchangeRate(new java.math.BigDecimal("0.84")).isActive(true).build(),
                Currency.builder().code("CHF").symbol("CHF").nameFr("Franc suisse").nameEn("Swiss Franc").decimalDigits(2).exchangeRate(new java.math.BigDecimal("0.96")).isActive(true).build(),
                Currency.builder().code("JPY").symbol("¥").nameFr("Yen japonais").nameEn("Japanese Yen").decimalDigits(0).exchangeRate(new java.math.BigDecimal("170.0")).isActive(true).build(),
                Currency.builder().code("CNY").symbol("¥").nameFr("Yuan chinois").nameEn("Chinese Yuan").decimalDigits(2).exchangeRate(new java.math.BigDecimal("7.85")).isActive(true).build(),
                Currency.builder().code("ZAR").symbol("R").nameFr("Rand sud-africain").nameEn("South African Rand").decimalDigits(2).exchangeRate(new java.math.BigDecimal("19.5")).isActive(true).build(),
                Currency.builder().code("MAD").symbol("DH").nameFr("Dirham marocain").nameEn("Moroccan Dirham").decimalDigits(2).exchangeRate(new java.math.BigDecimal("10.8")).isActive(true).build()
            );
            
            currencyRepository.saveAll(defaultCurrencies);
            log.info("Seeding des devises terminé avec succès : {} devises ajoutées.", defaultCurrencies.size());
        } else {
            log.info("Des devises existent déjà en base de données ({}) . Seeding ignoré.", currencyRepository.count());
        }
    }
}
