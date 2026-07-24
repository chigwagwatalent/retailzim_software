package com.retailzw.service;

import com.retailzw.enums.CurrencyCode;
import com.retailzw.model.*;
import com.retailzw.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditAndChangeService {
    private final BorrowerRepository borrowers;
    private final BorrowerTransactionRepository borrowerTransactions;
    private final HeldChangeRepository heldChange;
    private final CashSessionRepository cashSessions;

    public List<Borrower> activeBorrowers(Long tenantId) {
        return borrowers.findByTenantIdAndIsActiveTrueOrderByFullNameAsc(tenantId);
    }

    public Page<Borrower> borrowers(Long tenantId, String search, int page, int size) {
        return borrowers.search(tenantId, clean(search), PageRequest.of(page, size));
    }

    public Page<HeldChange> changeRecords(Long tenantId, HeldChange.Status status, String search, int page, int size) {
        return heldChange.search(tenantId, status, clean(search), PageRequest.of(page, size));
    }

    public Page<HeldChange> changeRecords(Long tenantId, HeldChange.Status status, String search,
                                          LocalDateTime fromDate, LocalDateTime toDate, int page, int size) {
        return heldChange.searchWithDates(tenantId, status, clean(search), fromDate, toDate, PageRequest.of(page, size));
    }

    public long changeRecordCount(Long tenantId, HeldChange.Status status) {
        return heldChange.countByTenantIdAndStatus(tenantId, status);
    }

    public BigDecimal sumChangeRecords(Long tenantId, HeldChange.Status status, String search,
                                       LocalDateTime fromDate, LocalDateTime toDate, CurrencyCode currency) {
        return nvl(heldChange.sumSearchWithDates(tenantId, status, clean(search), fromDate, toDate, currency));
    }

    public List<BorrowerTransaction> borrowerTransactions(Long tenantId, Long borrowerId) {
        return borrowerTransactions.findByTenantIdAndBorrowerIdOrderByCreatedAtDesc(
                tenantId, borrowerId, PageRequest.of(0, 100));
    }

    @Transactional
    public Borrower saveBorrower(Long tenantId, Long borrowerId, String accountNumber, String fullName,
                                 String phone, String nationalId, CurrencyCode currency,
                                 BigDecimal creditLimit, String notes, Long userId) {
        Borrower borrower = borrowerId == null
                ? Borrower.builder().tenantId(tenantId).createdBy(userId).currentBalance(BigDecimal.ZERO).build()
                : borrowers.lockById(tenantId, borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower account not found."));
        String account = clean(accountNumber);
        if (account == null) {
            account = "BRW-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        }
        String finalAccount = account;
        borrowers.findByTenantIdAndAccountNumber(tenantId, account)
                .filter(existing -> !existing.getId().equals(borrower.getId()))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Borrower account number already exists.");
                });
        borrower.setAccountNumber(finalAccount);
        borrower.setFullName(required(fullName, "Borrower name is required."));
        borrower.setPhone(required(phone, "Borrower phone is required."));
        borrower.setNationalId(clean(nationalId));
        borrower.setCurrency(currency == null ? CurrencyCode.USD : currency);
        borrower.setCreditLimit(nonNegative(creditLimit, "Credit limit cannot be negative."));
        borrower.setNotes(clean(notes));
        if (borrower.getIsActive() == null) borrower.setIsActive(true);
        if (nvl(borrower.getCurrentBalance()).compareTo(borrower.getCreditLimit()) > 0) {
            throw new IllegalArgumentException("Credit limit cannot be below the current borrower balance.");
        }
        return borrowers.save(borrower);
    }

    @Transactional
    public Borrower setBorrowerActive(Long tenantId, Long borrowerId, boolean active) {
        Borrower borrower = borrowers.lockById(tenantId, borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower account not found."));
        borrower.setIsActive(active);
        return borrowers.save(borrower);
    }

    @Transactional
    public BorrowerTransaction postBorrowerTransaction(Long tenantId, Long branchId, Long borrowerId,
                                                        BorrowerTransaction.TransactionType type,
                                                        BigDecimal amount, String notes, Long userId) {
        Borrower borrower = borrowers.lockById(tenantId, borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower account not found."));
        BigDecimal cleanAmount = positive(amount, "Amount must be greater than zero.");
        BigDecimal before = nvl(borrower.getCurrentBalance());
        BigDecimal after = type == BorrowerTransaction.TransactionType.REPAYMENT
                ? before.subtract(cleanAmount)
                : before.add(cleanAmount);
        if (after.compareTo(BigDecimal.ZERO) < 0) after = BigDecimal.ZERO;
        if (after.compareTo(nvl(borrower.getCreditLimit())) > 0) {
            throw new IllegalArgumentException("Transaction exceeds the borrower's credit limit.");
        }
        borrower.setCurrentBalance(after);
        borrowers.save(borrower);
        return borrowerTransactions.save(BorrowerTransaction.builder()
                .tenantId(tenantId).branchId(branchId).borrowerId(borrowerId)
                .transactionType(type).currency(borrower.getCurrency()).amount(cleanAmount)
                .balanceBefore(before).balanceAfter(after).notes(clean(notes)).createdBy(userId).build());
    }

    @Transactional
    public BorrowerTransaction chargeSale(Long tenantId, Long branchId, Long cashierId,
                                           CashSession session, Sale sale, Long borrowerId,
                                           String offlineReference) {
        if (borrowerId == null) throw new IllegalArgumentException("Select an active borrower for a credit sale.");
        String reference = clean(offlineReference);
        if (reference != null) {
            var existing = borrowerTransactions.findByTenantIdAndOfflineReference(tenantId, reference);
            if (existing.isPresent()) return existing.get();
        }
        Borrower borrower = borrowers.lockById(tenantId, borrowerId)
                .orElseThrow(() -> new IllegalArgumentException("Borrower account not found."));
        if (!Boolean.TRUE.equals(borrower.getIsActive())) {
            throw new IllegalArgumentException("Borrower account is inactive.");
        }
        if (!borrower.getCurrency().equals(sale.getCurrency())) {
            throw new IllegalArgumentException("Borrower account is in " + borrower.getCurrency() + "; change the sale currency.");
        }
        BigDecimal before = nvl(borrower.getCurrentBalance());
        BigDecimal after = before.add(nvl(sale.getGrandTotal()));
        if (after.compareTo(nvl(borrower.getCreditLimit())) > 0) {
            throw new IllegalArgumentException("Borrower has only " + borrower.getAvailableCredit()
                    + " " + borrower.getCurrency() + " available.");
        }
        borrower.setCurrentBalance(after);
        borrowers.save(borrower);
        sale.setBorrowerId(borrowerId);
        sale.setSaleType(Sale.SaleType.BORROWER_CREDIT);
        return borrowerTransactions.save(BorrowerTransaction.builder()
                .tenantId(tenantId).branchId(branchId).borrowerId(borrowerId)
                .saleId(sale.getId()).cashSessionId(session.getId())
                .transactionType(BorrowerTransaction.TransactionType.BORROW)
                .currency(sale.getCurrency()).amount(sale.getGrandTotal())
                .balanceBefore(before).balanceAfter(after)
                .offlineReference(reference).notes("Credit sale " + sale.getReceiptNumber())
                .createdBy(cashierId).build());
    }

    @Transactional
    public HeldChange holdChange(Long tenantId, Long branchId, Long cashierId, CashSession session,
                                 Sale sale, String name, String phone, BigDecimal amount,
                                 String offlineReference) {
        BigDecimal cleanAmount = positive(amount, "Held change amount must be greater than zero.");
        String reference = clean(offlineReference);
        if (reference != null) {
            var existing = heldChange.findByTenantIdAndOfflineReference(tenantId, reference);
            if (existing.isPresent()) return existing.get();
        }
        boolean cashPayment = sale.getPayments().stream()
                .anyMatch(payment -> SalePayment.PaymentMethod.CASH.equals(payment.getPaymentMethod()));
        if (!cashPayment) throw new IllegalArgumentException("Change can only be held for a cash payment.");
        HeldChange saved = heldChange.save(HeldChange.builder()
                .tenantId(tenantId).branchId(branchId).cashSessionId(session.getId()).saleId(sale.getId())
                .referenceNumber(changeReference()).customerName(required(name, "Customer name is required."))
                .phone(required(phone, "Customer phone is required.")).currency(sale.getCurrency())
                .amount(cleanAmount).offlineReference(reference).createdBy(cashierId).status(HeldChange.Status.OPEN)
                .notes("Change held from " + sale.getReceiptNumber()).build());
        addExpectedCash(session, saved.getCurrency(), cleanAmount);
        return saved;
    }

    @Transactional
    public HeldChange collectChange(Long tenantId, Long branchId, Long userId, Long changeId,
                                    Long cashSessionId) {
        HeldChange record = heldChange.lockById(tenantId, changeId)
                .orElseThrow(() -> new IllegalArgumentException("Held change record not found."));
        return collectLockedChange(tenantId, branchId, userId, cashSessionId, record);
    }

    @Transactional
    public HeldChange collectChangeByReference(Long tenantId, Long branchId, Long userId, String offlineReference,
                                               Long cashSessionId) {
        HeldChange record = heldChange.lockByOfflineReference(tenantId, required(offlineReference, "Change reference is required."))
                .orElseThrow(() -> new IllegalArgumentException("Held change record has not synced yet."));
        return collectLockedChange(tenantId, branchId, userId, cashSessionId, record);
    }

    private HeldChange collectLockedChange(Long tenantId, Long branchId, Long userId, Long cashSessionId, HeldChange record) {
        if (!record.getBranchId().equals(branchId)) throw new IllegalArgumentException("Held change belongs to another branch.");
        if (record.getStatus() == HeldChange.Status.COLLECTED) return record;
        if (record.getStatus() != HeldChange.Status.OPEN) throw new IllegalStateException("Held change is not available.");
        CashSession session = cashSessionId == null
                ? cashSessions.findActiveSession(tenantId, branchId, userId)
                    .or(() -> cashSessions.findFirstByTenantIdAndBranchIdAndStatusOrderByOpenedAtDesc(
                            tenantId, branchId, CashSession.SessionStatus.OPEN))
                    .orElseThrow(() -> new IllegalStateException("Open a shift before paying held change."))
                : cashSessions.findById(cashSessionId)
                    .filter(s -> s.getTenantId().equals(tenantId) && s.getBranchId().equals(branchId))
                    .filter(s -> s.getStatus() == CashSession.SessionStatus.OPEN)
                    .orElseThrow(() -> new IllegalStateException("Cash session is not open."));
        addExpectedCash(session, record.getCurrency(), record.getAmount().negate());
        record.setStatus(HeldChange.Status.COLLECTED);
        record.setCollectedBy(userId);
        record.setCollectedCashSessionId(session.getId());
        record.setCollectedAt(LocalDateTime.now());
        return heldChange.save(record);
    }

    @Transactional
    public HeldChange cancelChange(Long tenantId, Long changeId, Long userId) {
        HeldChange record = heldChange.lockById(tenantId, changeId)
                .orElseThrow(() -> new IllegalArgumentException("Held change record not found."));
        if (record.getStatus() != HeldChange.Status.OPEN) throw new IllegalStateException("Only open change can be cancelled.");
        record.setStatus(HeldChange.Status.CANCELLED);
        record.setCancelledBy(userId);
        record.setCancelledAt(LocalDateTime.now());
        return heldChange.save(record);
    }

    private void addExpectedCash(CashSession session, CurrencyCode currency, BigDecimal amount) {
        if (currency == CurrencyCode.USD) {
            session.setExpectedCashUsd(nvl(session.getExpectedCashUsd()).add(amount));
        } else {
            session.setExpectedCashZwg(nvl(session.getExpectedCashZwg()).add(amount));
        }
        cashSessions.save(session);
    }

    private String changeReference() {
        return "CHG-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 5).toUpperCase(Locale.ROOT);
    }

    private BigDecimal nvl(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal positive(BigDecimal value, String message) {
        BigDecimal result = nvl(value);
        if (result.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException(message);
        return result;
    }

    private BigDecimal nonNegative(BigDecimal value, String message) {
        BigDecimal result = nvl(value);
        if (result.compareTo(BigDecimal.ZERO) < 0) throw new IllegalArgumentException(message);
        return result;
    }

    private String required(String value, String message) {
        String result = clean(value);
        if (result == null) throw new IllegalArgumentException(message);
        return result;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
