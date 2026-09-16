package io.github.oatelauser.springplus.boot.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ConfigCipherTest {

    @Test
    void encryptThenDecryptRestoresPlaintext() {
        String key = ConfigCipher.generateKey();
        String encrypted = ConfigCipher.encrypt(key, "Dev@123456");

        assertTrue(ConfigCipher.isEncrypted(encrypted));
        assertEquals("Dev@123456", ConfigCipher.decrypt(key, encrypted));
    }

    @Test
    void samePlaintextProducesDifferentCiphertext() {
        String key = ConfigCipher.generateKey();

        String first = ConfigCipher.encrypt(key, "same-input");
        String second = ConfigCipher.encrypt(key, "same-input");

        assertNotEquals(first, second);
        assertEquals(ConfigCipher.decrypt(key, first), ConfigCipher.decrypt(key, second));
    }

    @Test
    void decryptWithWrongKeyFails() {
        String encrypted = ConfigCipher.encrypt(ConfigCipher.generateKey(), "secret");
        String wrongKey = ConfigCipher.generateKey();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> ConfigCipher.decrypt(wrongKey, encrypted));
        assertTrue(exception.getMessage().contains("密钥不匹配"));
    }

    @Test
    void decryptTamperedCiphertextFails() {
        String key = ConfigCipher.generateKey();
        String encrypted = ConfigCipher.encrypt(key, "secret");

        // 篡改密文体中的一个 Base64 字符（保持 Base64 合法性），GCM 认证应失败
        int tamperIndex = 10;
        char tampered = encrypted.charAt(tamperIndex) == 'A' ? 'B' : 'A';
        String tamperedValue = encrypted.substring(0, tamperIndex) + tampered + encrypted.substring(tamperIndex + 1);

        assertThrows(IllegalStateException.class, () -> ConfigCipher.decrypt(key, tamperedValue));
    }

    @Test
    void isEncryptedBoundaryCases() {
        assertFalse(ConfigCipher.isEncrypted(null));
        assertFalse(ConfigCipher.isEncrypted(""));
        assertFalse(ConfigCipher.isEncrypted("plain-text"));
        assertFalse(ConfigCipher.isEncrypted("xxENC(abc)"));
        assertFalse(ConfigCipher.isEncrypted("ENC(abc)xx"));
        assertFalse(ConfigCipher.isEncrypted("ENC()"));
        assertTrue(ConfigCipher.isEncrypted("ENC(abc)"));
    }

    @Test
    void decryptRejectsNonEncryptedValue() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigCipher.decrypt(ConfigCipher.generateKey(), "plain-text"));
    }

    @Test
    void invalidKeyRejected() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThrows(IllegalStateException.class, () -> ConfigCipher.encrypt(null, "x"));
        assertThrows(IllegalStateException.class, () -> ConfigCipher.encrypt("   ", "x"));
        assertThrows(IllegalStateException.class, () -> ConfigCipher.encrypt("not-base64!!!", "x"));
        assertThrows(IllegalStateException.class, () -> ConfigCipher.encrypt(shortKey, "x"));
    }

    @Test
    void encrypt() {
        String key = ConfigCipher.generateKey();
        System.out.println(key);
        String encryptValue = ConfigCipher.encrypt(key, "Alice");
        System.out.println(encryptValue);
    }
}
