package com.demo.upimesh.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;

@Service
public class OtpServiceImpl implements OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpServiceImpl.class);
    private final Map<String, String> otpStorage = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Override
    public String generateOtp(String phoneNumber) {
        // Generate a 6-digit OTP
        int num = 100000 + random.nextInt(900000);
        String otp = String.valueOf(num);
        otpStorage.put(phoneNumber, otp);
        
        // Demo Mode: Print in console/logs
        log.info("------------------------------------------------");
        log.info("OTP Generated for {}: {}", phoneNumber, otp);
        log.info("------------------------------------------------");
        
        return otp;
    }

    @Override
    public boolean verifyOtp(String phoneNumber, String otp) {
        if (phoneNumber == null || otp == null) return false;
        
        // Match OTP (or allow "123456" for ultra-smooth simulator demo testing if desired)
        String storedOtp = otpStorage.get(phoneNumber);
        if (otp.equals(storedOtp) || otp.equals("123456")) {
            otpStorage.remove(phoneNumber);
            return true;
        }
        return false;
    }
}
