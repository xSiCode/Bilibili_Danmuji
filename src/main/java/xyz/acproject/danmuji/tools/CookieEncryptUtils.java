package xyz.acproject.danmuji.tools;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM cookie encryption. Stores data as "AES:" prefix + Base64(salt + iv + ciphertext).
 * Falls back to legacy custom base64 decoder for existing config files.
 */
public class CookieEncryptUtils {

    private static final String PREFIX = "AES:";
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int GCM_TAG_LEN = 128;
    private static final int KEY_ITERATIONS = 10000;
    private static final int KEY_LENGTH = 256;

    // Derivation salt — not a secret, prevents precomputation
    private static final byte[] PBKDF_SALT = {
        0x4f, 0x6e, 0x65, 0x2e, 0x4d, 0x69, 0x73, 0x73, 0x69, 0x73, 0x73, 0x69, 0x70, 0x70, 0x69, 0x20,
        0x52, 0x65, 0x63, 0x6f, 0x72, 0x64, 0x73, 0x2c, 0x20, 0x4e, 0x6f, 0x74, 0x20, 0x46, 0x61, 0x6d
    };

    // Key material — compiled into bytecode
    private static final char[] SECRET = {
        'Y', 'o', 'u', 'R', 'H', 'e', 'a', 'r', 't', 'I', 's', 'A', 'S', 'e', 'c', 'r',
        'e', 't', 'F', 'l', 'a', 'm', 'e', 'T', 'h', 'a', 't', 'C', 'a', 'n', 'N', 'e',
        'v', 'e', 'r', 'B', 'e', 'E', 'x', 't', 'i', 'n', 'g', 'u', 'i', 's', 'h', 'e',
        'd', 'W', 'i', 'l', 'l', 'B', 'u', 'r', 'n', 'E', 't', 'e', 'r', 'n', 'a', 'l'
    };

    private static SecretKeySpec deriveKey(byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(SECRET, salt, KEY_ITERATIONS, KEY_LENGTH);
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return plaintext;
        try {
            SecureRandom sr = new SecureRandom();
            byte[] salt = new byte[SALT_LEN];
            byte[] iv = new byte[IV_LEN];
            sr.nextBytes(salt);
            sr.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Combine: salt + iv + ciphertext
            byte[] combined = new byte[SALT_LEN + IV_LEN + ct.length];
            System.arraycopy(salt, 0, combined, 0, SALT_LEN);
            System.arraycopy(iv, 0, combined, SALT_LEN, IV_LEN);
            System.arraycopy(ct, 0, combined, SALT_LEN + IV_LEN, ct.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            // Fallback to legacy encoding on encryption failure
            return null;
        }
    }

    public static String decrypt(String encoded) {
        if (encoded == null || encoded.isEmpty()) return encoded;

        if (encoded.startsWith(PREFIX)) {
            try {
                byte[] combined = Base64.getDecoder().decode(encoded.substring(PREFIX.length()));
                byte[] salt = new byte[SALT_LEN];
                byte[] iv = new byte[IV_LEN];
                byte[] ct = new byte[combined.length - SALT_LEN - IV_LEN];
                System.arraycopy(combined, 0, salt, 0, SALT_LEN);
                System.arraycopy(combined, SALT_LEN, iv, 0, IV_LEN);
                System.arraycopy(combined, SALT_LEN + IV_LEN, ct, 0, ct.length);

                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_LEN, iv));
                return new String(cipher.doFinal(ct), "UTF-8");
            } catch (Exception e) {
                return null;
            }
        }

        // Legacy format: decode with custom Base64
        try {
            return new String(new BASE64Encoder().decode(encoded));
        } catch (Exception e) {
            return null;
        }
    }
}
