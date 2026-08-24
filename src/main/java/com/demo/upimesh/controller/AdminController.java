package com.demo.upimesh.controller;

import com.demo.upimesh.model.*;
import com.demo.upimesh.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestParam String username, @RequestParam String password) {
        Optional<Admin> adminOpt = adminRepository.findById(username);
        if (adminOpt.isPresent() && adminOpt.get().getPassword().equals(password)) {
            return ResponseEntity.ok(Map.of("message", "Login successful", "admin", username));
        }
        return ResponseEntity.status(401).body(Map.of("error", "Invalid admin credentials"));
    }

    @PostMapping("/accounts/create")
    public ResponseEntity<?> createUser(@RequestBody ApiController.AuthRegisterRequest req) {
        try {
            Account account = authService.register(
                req.vpa,
                req.holderName,
                req.phoneNumber,
                req.mpin,
                req.email != null ? req.email : "",
                req.bankName != null ? req.bankName : "Demo Bank",
                req.bankAccountNumber != null ? req.bankAccountNumber : "000000000000",
                req.cardNumber != null ? req.cardNumber : "0000000000000000",
                req.expiryDate != null ? req.expiryDate : "12/99"
            );
            return ResponseEntity.ok(account);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/accounts/delete")
    public ResponseEntity<?> deleteUser(@RequestParam String vpa) {
        try {
            authService.deleteAccount(vpa);
            return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/accounts/freeze")
    public ResponseEntity<?> freezeUser(@RequestParam String vpa, @RequestParam boolean freeze) {
        Optional<Account> accOpt = accountRepository.findById(vpa);
        if (accOpt.isPresent()) {
            Account acc = accOpt.get();
            acc.setFrozen(freeze);
            accountRepository.save(acc);
            return ResponseEntity.ok(acc);
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Account not found"));
    }

    @PostMapping("/accounts/credit")
    public ResponseEntity<?> creditUser(@RequestParam String vpa, @RequestParam BigDecimal amount) {
        Optional<Account> accOpt = accountRepository.findById(vpa);
        if (accOpt.isPresent()) {
            Account acc = accOpt.get();
            acc.setBalance(acc.getBalance().add(amount));
            accountRepository.save(acc);
            return ResponseEntity.ok(acc);
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Account not found"));
    }

    @PostMapping("/accounts/debit")
    public ResponseEntity<?> debitUser(@RequestParam String vpa, @RequestParam BigDecimal amount) {
        Optional<Account> accOpt = accountRepository.findById(vpa);
        if (accOpt.isPresent()) {
            Account acc = accOpt.get();
            if (acc.getBalance().compareTo(amount) < 0) {
                return ResponseEntity.badRequest().body(Map.of("error", "Insufficient balance"));
            }
            acc.setBalance(acc.getBalance().subtract(amount));
            accountRepository.save(acc);
            return ResponseEntity.ok(acc);
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Account not found"));
    }

    @PostMapping("/accounts/reset-mpin")
    public ResponseEntity<?> resetMpin(@RequestParam String vpa, @RequestParam String mpin) {
        try {
            authService.resetMpin(vpa, mpin);
            return ResponseEntity.ok(Map.of("message", "MPIN reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics() {
        List<Transaction> transactions = transactionRepository.findAll();
        BigDecimal totalVolume = BigDecimal.ZERO;
        long settledCount = 0;
        long rejectedCount = 0;

        for (Transaction tx : transactions) {
            if (tx.getStatus() == Transaction.Status.SETTLED) {
                totalVolume = totalVolume.add(tx.getAmount());
                settledCount++;
            } else if (tx.getStatus() == Transaction.Status.REJECTED) {
                rejectedCount++;
            }
        }

        long activeUsers = accountRepository.count();

        return ResponseEntity.ok(Map.of(
                "totalVolume", totalVolume,
                "settledCount", settledCount,
                "rejectedCount", rejectedCount,
                "activeUsers", activeUsers,
                "totalTransactions", transactions.size()
        ));
    }
}
