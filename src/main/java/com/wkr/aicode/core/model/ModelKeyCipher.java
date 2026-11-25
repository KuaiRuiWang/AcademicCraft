package com.wkr.aicode.core.model;

import com.wkr.aicode.exception.BusinessException;
import com.wkr.aicode.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ModelKeyCipher {
    private static final int NONCE_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final String configuredKey;

    public ModelKeyCipher(@Value("${platform.models.encryption-key:}") String configuredKey) {
        this.configuredKey = configuredKey;
    }

    public String encrypt(String apiKey) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, nonce, apiKey.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                .put(nonce).put(encrypted).array());
    }

    public String decrypt(String ciphertext) {
        try {
            ByteBuffer stored = ByteBuffer.wrap(Base64.getDecoder().decode(ciphertext));
            if (stored.remaining() < NONCE_BYTES + 16) throw new IllegalArgumentException();
            byte[] nonce = new byte[NONCE_BYTES];
            stored.get(nonce);
            byte[] encrypted = new byte[stored.remaining()];
            stored.get(encrypted);
            return new String(crypt(Cipher.DECRYPT_MODE, nonce, encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "模型密钥无法解密，请重新保存");
        }
    }

    private byte[] crypt(int mode, byte[] nonce, byte[] input) {
        byte[] key;
        try {
            key = Base64.getDecoder().decode(configuredKey);
            if (key.length != 32) throw new IllegalArgumentException();
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "请配置管理端的模型密钥加密密钥");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return cipher.doFinal(input);
        } catch (GeneralSecurityException error) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "模型密钥处理失败，请检查加密密钥或重新保存");
        }
    }
}
