package com.RobinNotBad.BiliClient.util;

import android.util.Base64;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

public class PasswordEncryptUtil {

    public static String getKeyPem(String rawKey) {
        String content = rawKey.strip();
        content = content.replace("-----BEGIN PUBLIC KEY-----", "");
        content = content.replace("-----END PUBLIC KEY-----", "");
        content = content.replace("\n", "").replace("\r", "").trim();
        return content;
    }

    public static String encryptPassword(String password, String hash, String pubKeyPem) throws Exception {
        String keyContent = getKeyPem(pubKeyPem);
        byte[] keyBytes = Base64.decode(keyContent, Base64.DEFAULT);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(keySpec);
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encryptedBytes = cipher.doFinal((hash + password).getBytes());
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP);
    }
}