package com.demo.upimesh.service;

import com.demo.upimesh.crypto.HybridCryptoService;
import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.Account;
import com.demo.upimesh.model.AccountRepository;
import com.demo.upimesh.model.Admin;
import com.demo.upimesh.model.AdminRepository;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.PaymentInstruction;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;

@Service
public class DemoService {

    private static final Logger log = LoggerFactory.getLogger(DemoService.class);

    @Autowired private AccountRepository accounts;
    @Autowired private AdminRepository admins;
    @Autowired private HybridCryptoService crypto;
    @Autowired private ServerKeyHolder serverKey;

    @PostConstruct
    public void seedAccounts() {
        if (admins.count() == 0) {
            admins.save(new Admin("admin", "Admin@123"));
            log.info("Seeded default Admin credentials: admin / Admin@123");
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest("1234".getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            String hashed1234 = hex.toString();

            if (!accounts.existsById("tanish@demo")) {
                accounts.save(new Account("tanish@demo", "Tanish", new BigDecimal("5000.00"), "9876543210", hashed1234));
            }
            if (!accounts.existsById("rahul@demo")) {
                accounts.save(new Account("rahul@demo", "Rahul", new BigDecimal("5000.00"), "9876543211", hashed1234));
            }
            if (!accounts.existsById("priya@demo")) {
                accounts.save(new Account("priya@demo", "Priya", new BigDecimal("5000.00"), "9876543212", hashed1234));
            }
            log.info("Seeded demo accounts: tanish@demo, rahul@demo, priya@demo (MPIN: 1234)");
        } catch (Exception e) {
            log.error("Failed to seed demo accounts", e);
        }
    }

    public MeshPacket createPacket(String senderVpa, String receiverVpa,
                                   BigDecimal amount, String mpin, int ttl) throws Exception {
        PaymentInstruction instruction = new PaymentInstruction(
                senderVpa,
                receiverVpa,
                amount,
                sha256Hex(mpin),
                UUID.randomUUID().toString(),
                Instant.now().toEpochMilli()
        );

        String ciphertext = crypto.encrypt(instruction, serverKey.getPublicKey());

        MeshPacket packet = new MeshPacket();
        packet.setPacketId(UUID.randomUUID().toString());
        packet.setTtl(ttl);
        packet.setCreatedAt(Instant.now().toEpochMilli());
        packet.setCiphertext(ciphertext);
        return packet;
    }

    private String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(input.getBytes());
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
