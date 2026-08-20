package com.demo.upimesh.service;

import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private OtpService otpService;

    // Session token -> Account VPA
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    public String hashMpin(String mpin) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(mpin.getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("Could not hash MPIN", e);
        }
    }

    public Account register(String vpa, String holderName, String phoneNumber, String mpin, String email, String bankName, String bankAccountNumber, String cardNumber, String expiryDate) {
        if (accountRepository.existsById(vpa)) {
            throw new IllegalArgumentException("UPI ID already registered");
        }
        String hashedMpin = hashMpin(mpin);
        // Create account with initial 5000.00 wallet balance
        Account account = new Account(vpa, holderName, new BigDecimal("5000.00"), phoneNumber, hashedMpin);
        account.setEmail(email);
        account.setBankName(bankName);
        account.setBankAccountNumber(bankAccountNumber);
        if (cardNumber != null && cardNumber.length() >= 4) {
            String last4 = cardNumber.substring(cardNumber.length() - 4);
            account.setMaskedCardNumber("XXXX-XXXX-XXXX-" + last4);
        } else {
            account.setMaskedCardNumber("XXXX-XXXX-XXXX-XXXX");
        }
        account.setExpiryDate(expiryDate);
        return accountRepository.save(account);
    }

    public String login(String vpa, String mpin) {
        Account account = accountRepository.findById(vpa)
                .orElseThrow(() -> new IllegalArgumentException("Invalid UPI ID or MPIN"));
        
        if (account.isFrozen()) {
            throw new IllegalArgumentException("Account is frozen. Please contact admin.");
        }

        String hashed = hashMpin(mpin);
        if (!hashed.equals(account.getHashedMpin())) {
            throw new IllegalArgumentException("Invalid UPI ID or MPIN");
        }

        // Generate session token
        String sessionToken = UUID.randomUUID().toString();
        activeSessions.put(sessionToken, vpa);
        return sessionToken;
    }

    public void logout(String sessionToken) {
        activeSessions.remove(sessionToken);
    }

    public Account getSessionAccount(String sessionToken) {
        String vpa = activeSessions.get(sessionToken);
        if (vpa == null) return null;
        return accountRepository.findById(vpa).orElse(null);
    }

    public void changeMpin(String vpa, String oldMpin, String newMpin) {
        Account account = accountRepository.findById(vpa)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (!hashMpin(oldMpin).equals(account.getHashedMpin())) {
            throw new IllegalArgumentException("Incorrect old MPIN");
        }
        account.setHashedMpin(hashMpin(newMpin));
        accountRepository.save(account);
    }

    public void resetMpin(String vpa, String newMpin) {
        Account account = accountRepository.findById(vpa)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        account.setHashedMpin(hashMpin(newMpin));
        accountRepository.save(account);
    }

    public void deleteAccount(String vpa) {
        if (!accountRepository.existsById(vpa)) {
            throw new IllegalArgumentException("Account not found");
        }
        accountRepository.deleteById(vpa);
    }
}
