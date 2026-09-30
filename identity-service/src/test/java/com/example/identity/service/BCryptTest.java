package com.example.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class BCryptTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("BCrypt hashing produces valid hash starting with $2a$ and matches raw password")
    void testBCryptHashing() {
        String rawPassword = "mySecretPassword123";

        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        // Kiểm tra định dạng BCrypt hash ($2a$...)
        assertTrue(hash1.startsWith("$2a$") || hash1.startsWith("$2b$"));
        assertTrue(hash2.startsWith("$2a$") || hash2.startsWith("$2b$"));

        // Do có Salt ngẫu nhiên, 2 lần hash cùng 1 mật khẩu phải sinh ra 2 chuỗi khác nhau
        assertNotEquals(hash1, hash2);

        // Cả 2 hash đều phải verify thành công với mật khẩu gốc
        assertTrue(passwordEncoder.matches(rawPassword, hash1));
        assertTrue(passwordEncoder.matches(rawPassword, hash2));

        // Mật khẩu sai phải trả về false
        assertFalse(passwordEncoder.matches("wrongPassword", hash1));
    }
}
