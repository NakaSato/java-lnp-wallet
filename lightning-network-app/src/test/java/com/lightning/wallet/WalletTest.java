package com.lightning.wallet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class WalletTest {
    private Wallet wallet;

    @BeforeEach
    public void setUp() {
        wallet = new Wallet();
    }

    @Test
    public void testInitialBalanceIsZero() {
        assertEquals(0, wallet.getBalance());
    }

    @Test
    public void testDepositIncreasesBalance() {
        wallet.deposit(100);
        assertEquals(100, wallet.getBalance());
    }

    @Test
    public void testWithdrawDecreasesBalance() {
        wallet.deposit(100);
        wallet.withdraw(50);
        assertEquals(50, wallet.getBalance());
    }

    @Test
    public void testWithdrawMoreThanBalanceThrowsException() {
        wallet.deposit(100);
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            wallet.withdraw(150);
        });
        assertEquals("Insufficient balance", exception.getMessage());
    }

    @Test
    public void testTransactionHistoryIsEmptyInitially() {
        assertTrue(wallet.getTransactionHistory().isEmpty());
    }
}