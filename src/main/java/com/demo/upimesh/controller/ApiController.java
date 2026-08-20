package com.demo.upimesh.controller;

import com.demo.upimesh.crypto.ServerKeyHolder;
import com.demo.upimesh.model.*;
import com.demo.upimesh.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired private ServerKeyHolder serverKey;
    @Autowired private DemoService demo;
    @Autowired private MeshSimulatorService mesh;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private IdempotencyService idempotency;
    @Autowired private AuthService authService;
    @Autowired private OtpService otpService;

    // ------------------------------------------------------------------ key

    @GetMapping("/server-key")
    public Map<String, String> getServerPublicKey() {
        return Map.of(
                "publicKey", serverKey.getPublicKeyBase64(),
                "algorithm", "RSA-2048 / OAEP-SHA256",
                "hybridScheme", "RSA-OAEP encrypts an AES-256-GCM session key"
        );
    }

    // ---------------------------------------------------------------- auth

    @PostMapping("/auth/otp/send")
    public ResponseEntity<?> sendOtp(@RequestParam String phoneNumber) {
        String otp = otpService.generateOtp(phoneNumber);
        return ResponseEntity.ok(Map.of("message", "OTP sent successfully (Simulated)", "otp", otp));
    }

    @PostMapping("/auth/otp/verify")
    public ResponseEntity<?> verifyOtp(@RequestParam String phoneNumber, @RequestParam String otp) {
        boolean verified = otpService.verifyOtp(phoneNumber, otp);
        if (verified) {
            return ResponseEntity.ok(Map.of("message", "OTP verified successfully"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid or expired OTP"));
        }
    }

    @PostMapping("/auth/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRegisterRequest req) {
        try {
            Account account = authService.register(req.vpa, req.holderName, req.phoneNumber, req.mpin);
            return ResponseEntity.ok(account);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/login")
    public ResponseEntity<?> loginUser(@RequestBody AuthLoginRequest req) {
        try {
            String token = authService.login(req.vpa, req.mpin);
            Account account = accountRepo.findById(req.vpa).orElse(null);
            return ResponseEntity.ok(Map.of("token", token, "vpa", req.vpa, "account", account));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<?> logoutUser(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return ResponseEntity.ok(Map.of("message", "Logout successful"));
    }

    @GetMapping("/auth/session")
    public ResponseEntity<?> getSession(@RequestHeader("Authorization") String token) {
        Account account = authService.getSessionAccount(token);
        if (account == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid session token"));
        }
        return ResponseEntity.ok(account);
    }

    @PostMapping("/auth/change-mpin")
    public ResponseEntity<?> changeMpin(@RequestParam String vpa, @RequestParam String oldMpin, @RequestParam String newMpin) {
        try {
            authService.changeMpin(vpa, oldMpin, newMpin);
            return ResponseEntity.ok(Map.of("message", "MPIN updated successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/reset-mpin")
    public ResponseEntity<?> resetMpin(@RequestParam String vpa, @RequestParam String newMpin) {
        try {
            authService.resetMpin(vpa, newMpin);
            return ResponseEntity.ok(Map.of("message", "MPIN reset successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/auth/delete-account")
    public ResponseEntity<?> deleteAccount(@RequestParam String vpa) {
        try {
            authService.deleteAccount(vpa);
            return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Requests models

    public static class AuthRegisterRequest {
        public String vpa;
        public String holderName;
        public String phoneNumber;
        public String mpin;
    }

    public static class AuthLoginRequest {
        public String vpa;
        public String mpin;
    }

    // ---------------------------------------------------------------- demo

    @PostMapping("/demo/send")
    public ResponseEntity<?> demoSend(@RequestBody DemoSendRequest req) throws Exception {
        MeshPacket packet = demo.createPacket(
                req.senderVpa, req.receiverVpa, req.amount, req.pin,
                req.ttl == null ? 5 : req.ttl);

        String startDevice = req.startDevice == null ? "phone-alice" : req.startDevice;
        mesh.inject(startDevice, packet);

        return ResponseEntity.ok(Map.of(
                "packetId", packet.getPacketId(),
                "ciphertextPreview", packet.getCiphertext().substring(0, 64) + "...",
                "ttl", packet.getTtl(),
                "injectedAt", startDevice
        ));
    }

    public static class DemoSendRequest {
        public String senderVpa;
        public String receiverVpa;
        public BigDecimal amount;
        public String pin;
        public Integer ttl;
        public String startDevice;
    }

    // -------------------------------------------------------------- mesh sim

    @GetMapping("/mesh/state")
    public Map<String, Object> meshState() {
        List<Map<String, Object>> deviceData = new ArrayList<>();
        for (VirtualDevice d : mesh.getDevices()) {
            deviceData.add(Map.of(
                    "deviceId", d.getDeviceId(),
                    "hasInternet", d.hasInternet(),
                    "packetCount", d.packetCount(),
                    "packetIds", d.getHeldPackets().stream()
                            .map(p -> p.getPacketId().substring(0, 8))
                            .toList()
            ));
        }
        return Map.of(
                "devices", deviceData,
                "idempotencyCacheSize", idempotency.size()
        );
    }

    @PostMapping("/mesh/gossip")
    public Map<String, Object> meshGossip() {
        MeshSimulatorService.GossipResult r = mesh.gossipOnce();
        return Map.of(
                "transfers", r.transfers(),
                "deviceCounts", r.deviceCounts()
        );
    }

    @PostMapping("/mesh/flush")
    public Map<String, Object> meshFlush() {
        List<MeshSimulatorService.BridgeUpload> uploads = mesh.collectBridgeUploads();

        List<Map<String, Object>> results = new ArrayList<>();
        uploads.parallelStream().forEach(up -> {
            BridgeIngestionService.IngestResult r =
                    bridge.ingest(up.packet(), up.bridgeNodeId(), 5 - up.packet().getTtl());
            synchronized (results) {
                results.add(Map.of(
                        "bridgeNode", up.bridgeNodeId(),
                        "packetId", up.packet().getPacketId().substring(0, 8),
                        "outcome", r.outcome(),
                        "reason", r.reason() == null ? "" : r.reason(),
                        "transactionId", r.transactionId() == null ? -1 : r.transactionId()
                ));
            }
        });

        return Map.of(
                "uploadsAttempted", uploads.size(),
                "results", results
        );
    }

    @PostMapping("/mesh/reset")
    public Map<String, Object> meshReset() {
        mesh.resetMesh();
        idempotency.clear();
        return Map.of("status", "mesh and idempotency cache cleared");
    }

    // -------------------------------------------------------------- bridge

    @PostMapping("/bridge/ingest")
    public ResponseEntity<?> ingest(
            @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count", defaultValue = "0") int hopCount) {

        BridgeIngestionService.IngestResult r = bridge.ingest(packet, bridgeNodeId, hopCount);
        return ResponseEntity.ok(r);
    }

    @PostMapping("/accounts/register")
    public ResponseEntity<?> registerAccount(@RequestBody RegisterRequest req) {
        if (req.vpa == null || req.vpa.trim().isEmpty() || req.holderName == null || req.holderName.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "VPA and holderName are required"));
        }
        if (accountRepo.existsById(req.vpa)) {
            return ResponseEntity.badRequest().body(Map.of("error", "VPA already exists"));
        }
        // Default mpin registration as "1234" for compatibility with original code
        String hashed = authService.hashMpin("1234");
        Account acc = new Account(req.vpa, req.holderName, req.initialBalance == null ? new BigDecimal("5000.00") : req.initialBalance, "", hashed);
        accountRepo.save(acc);
        return ResponseEntity.ok(acc);
    }

    @PostMapping("/demo/create-packet")
    public ResponseEntity<?> createPacketOnly(@RequestBody DemoSendRequest req) throws Exception {
        MeshPacket packet = demo.createPacket(
                req.senderVpa, req.receiverVpa, req.amount, req.pin,
                req.ttl == null ? 5 : req.ttl);
        return ResponseEntity.ok(packet);
    }

    @PostMapping("/mesh/inject")
    public ResponseEntity<?> injectPacket(@RequestBody InjectPacketRequest req) {
        if (req.deviceId == null || req.packet == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "deviceId and packet are required"));
        }
        mesh.inject(req.deviceId, req.packet);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "deviceId", req.deviceId,
                "packetId", req.packet.getPacketId()
        ));
    }

    public static class RegisterRequest {
        public String vpa;
        public String holderName;
        public BigDecimal initialBalance;
    }

    public static class InjectPacketRequest {
        public String deviceId;
        public MeshPacket packet;
    }

    // ------------------------------------------------------------- accounts

    @GetMapping("/accounts")
    public List<Account> listAccounts() {
        return accountRepo.findAll();
    }

    @GetMapping("/transactions")
    public List<Transaction> listTransactions() {
        return txRepo.findTop20ByOrderByIdDesc();
    }
}
