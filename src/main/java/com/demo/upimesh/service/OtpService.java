package com.demo.upimesh.service;

public interface OtpService {
    String generateOtp(String phoneNumber);
    boolean verifyOtp(String phoneNumber, String otp);
}
