package io.github.oatelauser.springplus.boot.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 配置项对称加解密核心工具：AES-256-GCM。
 *
 * <p>密文格式：{@code ENC(Base64(IV + 密文 + GCM 认证标签))}。IV 在每次加密时随机生成，
 * 因此同一明文每次加密产生的密文不同；GCM 认证标签保证密钥不匹配或密文被篡改时解密必然失败。</p>
 *
 * <p>密钥为 256-bit 随机数的 Base64 编码（44 个字符），通过
 * {@link #generateKey()} 生成，由运维在启动时注入，不落盘、不进代码仓库。</p>
 */
public final class ConfigCipher {

    /**
     * 密文前缀，配置文件中以此包裹的值会在启动时被解密。
     */
    public static final String PREFIX = "ENC(";

    /**
     * 密文后缀。
     */
    public static final String SUFFIX = ")";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String KEY_ALGORITHM = "AES";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int KEY_LENGTH_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ConfigCipher() {
    }

    /**
     * 生成 256-bit AES 密钥，返回其 Base64 编码。
     */
    public static String generateKey() {
        byte[] key = new byte[KEY_LENGTH_BYTES];
        RANDOM.nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * 判断给定值是否为 {@code ENC(...)} 形式的密文。
     *
     * @param value 待判断的配置值，可为 null
     * @return true 表示该值需要解密
     */
    public static boolean isEncrypted(String value) {
        return value != null
                && value.startsWith(PREFIX)
                && value.endsWith(SUFFIX)
                && value.length() > PREFIX.length() + SUFFIX.length();
    }

    /**
     * 加密明文，返回 {@code ENC(...)} 形式的密文，可直接写入配置文件。
     *
     * @param base64Key  Base64 编码的 256-bit 密钥
     * @param plaintext  待加密明文
     * @return ENC(...) 形式的密文
     * @throws IllegalStateException 密钥非法或加密失败
     */
    public static String encrypt(String base64Key, String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, toKeySpec(base64Key), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined) + SUFFIX;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("配置加密失败", e);
        }
    }

    /**
     * 解密 {@code ENC(...)} 形式的密文，返回明文。
     *
     * @param base64Key       Base64 编码的 256-bit 密钥
     * @param encryptedValue  ENC(...) 形式的密文
     * @return 明文
     * @throws IllegalArgumentException 值不是 ENC(...) 格式
     * @throws IllegalStateException    密钥非法、密文损坏或密钥不匹配（GCM 认证失败）
     */
    public static String decrypt(String base64Key, String encryptedValue) {
        if (!isEncrypted(encryptedValue)) {
            throw new IllegalArgumentException("待解密的值不是 " + PREFIX + "..." + SUFFIX + " 格式: " + encryptedValue);
        }
        String base64Body = encryptedValue.substring(PREFIX.length(), encryptedValue.length() - SUFFIX.length());
        byte[] combined;
        try {
            combined = Base64.getDecoder().decode(base64Body);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("密文不是合法的 Base64 字符串", e);
        }
        if (combined.length <= GCM_IV_LENGTH_BYTES) {
            throw new IllegalStateException("密文长度不合法");
        }
        try {
            GCMParameterSpec parameterSpec =
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, combined, 0, GCM_IV_LENGTH_BYTES);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, toKeySpec(base64Key), parameterSpec);
            byte[] decrypted = cipher.doFinal(combined, GCM_IV_LENGTH_BYTES, combined.length - GCM_IV_LENGTH_BYTES);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("解密失败：密钥不匹配或密文已损坏", e);
        }
    }

    private static SecretKeySpec toKeySpec(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("密钥为空");
        }
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("密钥不是合法的 Base64 字符串", e);
        }
        if (keyBytes.length != KEY_LENGTH_BYTES) {
            throw new IllegalStateException("密钥长度必须为 " + KEY_LENGTH_BYTES + " 字节（Base64 编码后 44 个字符）");
        }
        return new SecretKeySpec(keyBytes, KEY_ALGORITHM);
    }
}
