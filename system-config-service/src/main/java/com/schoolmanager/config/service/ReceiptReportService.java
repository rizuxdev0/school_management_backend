package com.schoolmanager.config.service;

import com.schoolmanager.config.controller.AttendanceAndFinanceController.AmortizationInstallmentDto;
import com.schoolmanager.config.entity.*;
import com.schoolmanager.config.repository.*;
import com.schoolmanager.config.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * Service de generation des recus de paiement de scolarite en PDF avec JasperReports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptReportService {

    private final SystemSettingRepository systemSettingRepository;
    private final StudentPaymentRepository studentPaymentRepository;
    private final StudentEnrollmentRepository studentEnrollmentRepository;
    private final TuitionFeeRepository tuitionFeeRepository;
    private final AcademicYearRepository academicYearRepository;

    private static final String RECEIPT_TEMPLATE = "reports/payment_receipt.jrxml";

    /**
     * Genere le PDF du recu de paiement pour un versement specifique.
     * @param paymentId UUID du paiement pour lequel generer le recu
     * @return tableau de bytes du fichier PDF
     */
    public byte[] generatePaymentReceipt(UUID paymentId) {
        UUID tenantId = SecurityUtils.getCurrentTenantId();

        StudentPayment payment = studentPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Paiement introuvable avec l'identifiant : " + paymentId));
        SecurityUtils.assertOwnership(payment.getTenantId());

        SystemSetting setting = systemSettingRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Parametres de l'etablissement introuvables."));

        Student student = payment.getStudent();
        String className = "N/A";
        UUID yearId = payment.getAcademicYearId();

        Optional<StudentEnrollment> enrollmentOpt = studentEnrollmentRepository
                .findByStudentIdAndAcademicYearId(student.getId(), yearId);
        if (enrollmentOpt.isPresent() && enrollmentOpt.get().getClassroom() != null) {
            className = enrollmentOpt.get().getClassroom().getName();
        }

        String yearLabel = yearId.toString();
        Optional<AcademicYear> yearOpt = academicYearRepository.findById(yearId);
        if (yearOpt.isPresent()) {
            AcademicYear year = yearOpt.get();
            yearLabel = (year.getStartDate() != null && year.getEndDate() != null)
                    ? year.getStartDate().getYear() + "-" + year.getEndDate().getYear()
                    : (year.getCode() != null ? year.getCode() : yearId.toString());
        }

        List<AmortizationInstallmentDto> amortTable = new ArrayList<>();
        BigDecimal totalExigible = BigDecimal.ZERO;

        if (enrollmentOpt.isPresent() && enrollmentOpt.get().getClassroom() != null && enrollmentOpt.get().getClassroom().getAcademicLevel() != null) {
            UUID levelId = enrollmentOpt.get().getClassroom().getAcademicLevel().getId();
            List<TuitionFee> fees = tuitionFeeRepository.findByAcademicLevelIdAndAcademicYearId(levelId, yearId);
            totalExigible = fees.stream().map(TuitionFee::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            amortTable = buildAmortizationTable(fees);
        }

        List<StudentPayment> allPayments = studentPaymentRepository.findByStudentIdAndAcademicYearId(student.getId(), yearId);
        BigDecimal totalPaid = allPayments.stream().map(StudentPayment::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = totalExigible.subtract(totalPaid);

        applyFifoPayments(amortTable, totalPaid);

        Map<String, Object> params = buildReceiptParameters(setting, payment, student, className, yearLabel, totalExigible, totalPaid, balance);
        List<Map<String, Object>> rows = buildInstallmentRows(amortTable, setting);
        JRMapCollectionDataSource dataSource = new JRMapCollectionDataSource((Collection) rows);

        try {
            InputStream jrxmlStream = new ClassPathResource(RECEIPT_TEMPLATE).getInputStream();
            JasperReport jasperReport = JasperCompileManager.compileReport(jrxmlStream);
            JasperPrint print = JasperFillManager.fillReport(jasperReport, params, dataSource);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JasperExportManager.exportReportToPdfStream(print, outputStream);
            return outputStream.toByteArray();
        } catch (JRException e) {
            log.error("Erreur JasperReports recu PDF : {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur generation recu PDF : " + e.getMessage());
        } catch (Exception e) {
            log.error("Erreur inattendue recu PDF : {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur inattendue generation recu.");
        }
    }

    private Map<String, Object> buildReceiptParameters(SystemSetting setting, StudentPayment payment, Student student,
            String className, String yearLabel, BigDecimal totalExigible, BigDecimal totalPaid, BigDecimal balance) {
        Map<String, Object> params = new HashMap<>();
        params.put("institutionName",    nullSafe(setting.getInstitutionName()));
        params.put("institutionType",    resolveSchoolType(setting.getInstitutionType()));
        params.put("institutionAddress", nullSafe(setting.getAddress()));
        params.put("institutionPhone",   nullSafe(setting.getPhone()));
        params.put("institutionEmail",   nullSafe(setting.getContactEmail()));
        params.put("motto",              nullSafe(setting.getMotto()));
        params.put("primaryColorImage",  generateColorImage(setting.getPrimaryColor() != null ? setting.getPrimaryColor() : "#1A237E"));
        params.put("logoBase64",         normalizeLogoBase64(setting.getLogoBase64()));
        params.put("receiptNumber",      nullSafe(payment.getReceiptNumber()));
        params.put("receiptDate",        payment.getPaymentDate() != null
                ? payment.getPaymentDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        params.put("academicYear",       yearLabel);
        params.put("studentName",        (student.getLastName() + " " + student.getFirstName()).toUpperCase());
        params.put("registrationNumber", nullSafe(student.getRegistrationNumber()));
        params.put("className",          className);
        params.put("paymentMethod",      resolvePaymentMethod(payment.getPaymentMethod()));
        params.put("amountPaid",         formatAmount(payment.getAmountPaid()));
        params.put("amountPaidWords",    amountToWords(payment.getAmountPaid()));
        params.put("notes",              payment.getNotes() != null ? payment.getNotes() : "");
        String currency = (setting.getMainCurrency() != null && setting.getMainCurrency().getCode() != null)
                ? setting.getMainCurrency().getCode() : "FCFA";
        params.put("currencySymbol",     currency);
        params.put("totalExigible",      formatAmount(totalExigible));
        params.put("totalPaid",          formatAmount(totalPaid));
        params.put("balance",            formatAmount(balance));
        return params;
    }

    private List<Map<String, Object>> buildInstallmentRows(List<AmortizationInstallmentDto> amortTable, SystemSetting setting) {
        String currency = (setting.getMainCurrency() != null && setting.getMainCurrency().getCode() != null)
                ? setting.getMainCurrency().getCode() : "FCFA";
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AmortizationInstallmentDto inst : amortTable) {
            Map<String, Object> row = new HashMap<>();
            row.put("feeName",     nullSafe(inst.getFeeName()));
            row.put("installment", inst.getTotalInstallments() > 1 ? inst.getInstallmentNumber() + "/" + inst.getTotalInstallments() : "Unique");
            row.put("amountDue",   formatAmount(inst.getAmountDue()) + " " + currency);
            row.put("amountPaidF", formatAmount(inst.getAmountPaid()) + " " + currency);
            row.put("status",      resolveStatus(inst.getStatus()));
            rows.add(row);
        }
        if (rows.isEmpty()) {
            Map<String, Object> emptyRow = new HashMap<>();
            emptyRow.put("feeName", "Aucun frais configure"); emptyRow.put("installment", "-");
            emptyRow.put("amountDue", "0 " + currency); emptyRow.put("amountPaidF", "0 " + currency);
            emptyRow.put("status", "-"); rows.add(emptyRow);
        }
        return rows;
    }

    private List<AmortizationInstallmentDto> buildAmortizationTable(List<TuitionFee> fees) {
        List<AmortizationInstallmentDto> table = new ArrayList<>();
        for (TuitionFee fee : fees) {
            int count = fee.getInstallmentsCount() != null ? fee.getInstallmentsCount() : 1;
            String freq = fee.getPaymentFrequency() != null ? fee.getPaymentFrequency() : "UNIQUE";
            BigDecimal total = fee.getAmount();
            if (count <= 1) {
                table.add(AmortizationInstallmentDto.builder().feeName(fee.getName()).installmentNumber(1).totalInstallments(1)
                        .amountDue(total).amountPaid(BigDecimal.ZERO).amountRemaining(total)
                        .dueDate(LocalDate.now().withMonth(9).withDayOfMonth(15)).status("PENDING").build());
            } else {
                BigDecimal amt = total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
                BigDecimal rem = total.subtract(amt.multiply(BigDecimal.valueOf(count - 1)));
                LocalDate base = LocalDate.now().withMonth(9).withDayOfMonth(15);
                for (int i = 1; i <= count; i++) {
                    BigDecimal due = (i == count) ? rem : amt;
                    LocalDate dd = "MONTHLY".equalsIgnoreCase(freq) ? base.plusMonths(i-1)
                            : "TRIMESTRIEL".equalsIgnoreCase(freq) ? base.plusMonths((i-1)*3) : base;
                    table.add(AmortizationInstallmentDto.builder().feeName(fee.getName()).installmentNumber(i).totalInstallments(count)
                            .amountDue(due).amountPaid(BigDecimal.ZERO).amountRemaining(due).dueDate(dd).status("PENDING").build());
                }
            }
        }
        table.sort(Comparator.comparing(AmortizationInstallmentDto::getDueDate));
        return table;
    }

    private void applyFifoPayments(List<AmortizationInstallmentDto> table, BigDecimal totalPaid) {
        BigDecimal remaining = totalPaid;
        for (AmortizationInstallmentDto inst : table) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) { inst.setStatus("PENDING"); continue; }
            BigDecimal due = inst.getAmountDue();
            if (remaining.compareTo(due) >= 0) {
                inst.setAmountPaid(due); inst.setAmountRemaining(BigDecimal.ZERO);
                inst.setStatus("PAID"); remaining = remaining.subtract(due);
            } else {
                inst.setAmountPaid(remaining); inst.setAmountRemaining(due.subtract(remaining));
                inst.setStatus("PARTIAL"); remaining = BigDecimal.ZERO;
            }
        }
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0";
        DecimalFormatSymbols s = new DecimalFormatSymbols(Locale.FRANCE);
        s.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0", s).format(amount.setScale(0, RoundingMode.HALF_UP));
    }

    private String amountToWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "Zero franc";
        long value = amount.setScale(0, RoundingMode.HALF_UP).longValue();
        return convertToWords(value) + " franc(s)";
    }

    private static final String[] UNITS = {"","un","deux","trois","quatre","cinq","six","sept","huit","neuf","dix","onze","douze","treize","quatorze","quinze","seize","dix-sept","dix-huit","dix-neuf"};
    private static final String[] TENS  = {"","","vingt","trente","quarante","cinquante","soixante","soixante-dix","quatre-vingt","quatre-vingt-dix"};

    private String convertToWords(long n) {
        if (n < 0)  return "moins " + convertToWords(-n);
        if (n == 0) return "zero";
        if (n < 20) return UNITS[(int)n];
        if (n < 100) { int t=(int)(n/10),u=(int)(n%10); if(t==7||t==9) return TENS[t]+"-"+UNITS[(int)(n-t*10+10)]; return TENS[t]+(u==0?"":" "+UNITS[u]); }
        if (n < 1000) return (n/100==1?"":UNITS[(int)(n/100)]+" ")+"cent"+(n%100==0?"":" "+convertToWords(n%100));
        if (n < 1_000_000) return (n/1000==1?"":convertToWords(n/1000)+" ")+"mille"+(n%1000==0?"":" "+convertToWords(n%1000));
        if (n < 1_000_000_000) return convertToWords(n/1_000_000)+" million"+(n/1_000_000>1?"s":"")+(n%1_000_000==0?"":" "+convertToWords(n%1_000_000));
        return convertToWords(n/1_000_000_000)+" milliard"+(n/1_000_000_000>1?"s":"")+(n%1_000_000_000==0?"":" "+convertToWords(n%1_000_000_000));
    }

    private String resolveStatus(String s) { if(s==null)return"-"; return switch(s.toUpperCase()){case"PAID"->"REGLE";case"PARTIAL"->"PARTIEL";case"PENDING"->"EN ATTENTE";default->s;}; }
    private String resolvePaymentMethod(String m) { if(m==null)return""; return switch(m.toUpperCase()){case"CASH"->"Especes";case"MOBILE_MONEY"->"Mobile Money";case"BANK_TRANSFER"->"Virement Bancaire";case"CARD"->"Carte Bancaire";default->m;}; }
    private String resolveSchoolType(String t) { if(t==null)return""; return switch(t.toUpperCase()){case"PUBLIC"->"Public";case"PRIVATE"->"Prive";case"SEMI_PRIVATE"->"Semi-Prive";default->t;}; }
    private String nullSafe(String v) {
        if (v == null) return "";
        if (v.contains("Ã")) {
            try {
                return new String(v.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) {
                return v;
            }
        }
        return v;
    }

    private BufferedImage generateColorImage(String hexColor) {
        try {
            Color c = Color.decode(hexColor != null && hexColor.startsWith("#") ? hexColor : "#1A237E");
            BufferedImage img = new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);
            img.setRGB(0,0,c.getRGB()); return img;
        } catch(Exception e){ log.warn("Couleur HEX invalide: {}",hexColor); return null; }
    }

    private String normalizeLogoBase64(String base64Input) {
        if(base64Input==null||base64Input.trim().isEmpty()) return "";
        try {
            String clean = base64Input.contains(",")?base64Input.split(",")[1]:base64Input;
            byte[] decoded = Base64.getDecoder().decode(clean.trim());
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(decoded));
            if(image==null) return base64Input;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if(!ImageIO.write(image,"png",baos)) return base64Input;
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch(Exception e){ log.warn("Erreur normalisation logo: {}",e.getMessage()); return base64Input; }
    }
}
