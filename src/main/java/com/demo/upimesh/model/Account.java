package com.demo.upimesh.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private String vpa; // Virtual Payment Address, e.g. "alice@demo"

    @Column(nullable = false)
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal pendingBalance = BigDecimal.ZERO;

    @Column(nullable = true)
    private String phoneNumber;

    @Column(nullable = true)
    private String hashedMpin;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit = new BigDecimal("10000.00");

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyLimit = new BigDecimal("100000.00");

    @Column(nullable = false)
    private boolean isFrozen = false;

    @Version
    private Long version;

    public Account() {}

    public Account(String vpa, String holderName, BigDecimal balance) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.balance = balance;
    }

    public Account(String vpa, String holderName, BigDecimal balance, String phoneNumber, String hashedMpin) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.balance = balance;
        this.phoneNumber = phoneNumber;
        this.hashedMpin = hashedMpin;
    }

    public String getVpa() { return vpa; }
    public void setVpa(String vpa) { this.vpa = vpa; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public BigDecimal getPendingBalance() { return pendingBalance; }
    public void setPendingBalance(BigDecimal pendingBalance) { this.pendingBalance = pendingBalance; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public String getHashedMpin() { return hashedMpin; }
    public void setHashedMpin(String hashedMpin) { this.hashedMpin = hashedMpin; }

    public BigDecimal getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(BigDecimal dailyLimit) { this.dailyLimit = dailyLimit; }

    public BigDecimal getMonthlyLimit() { return monthlyLimit; }
    public void setMonthlyLimit(BigDecimal monthlyLimit) { this.monthlyLimit = monthlyLimit; }

    public boolean isFrozen() { return isFrozen; }
    public void setFrozen(boolean frozen) { isFrozen = frozen; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
