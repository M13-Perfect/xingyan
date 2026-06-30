package org.example.xyyx.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PhonePrivacyServiceTests {
    private static final String ENC_KEY = key("phone-privacy-enc-key-v1-32bytes");
    private static final String HASH_KEY = key("phone-privacy-hmac-key-v1-32byt");
    private static final String HASH_KEY_2 = key("phone-privacy-hmac-key-v2-32byt");

    @Test
    void normalizePhoneReturnsE164AndRejectsBadInput() throws Exception {
        Object service = service("v1", ENC_KEY, "h1", HASH_KEY);

        assertEquals("+8613812345678", invoke(service, "normalizePhone", "138 1234 5678", "CN"));
        assertEquals("+8613812345678", invoke(service, "normalizePhone", "+86 138-1234-5678", "CN"));
        assertPhoneCode("PHONE_EMPTY", () -> invoke(service, "normalizePhone", " ", "CN"));
        assertPhoneCode("PHONE_INVALID", () -> invoke(service, "normalizePhone", "12345", "CN"));
    }

    @Test
    void samePhoneFormatsGenerateSameHashAndDifferentHashKeysDoNotCollide() throws Exception {
        Object service = service("v1", ENC_KEY, "h1", HASH_KEY);
        Object serviceWithOtherHashKey = service("v1", ENC_KEY, "h2", HASH_KEY_2);

        byte[] hash1 = (byte[]) invoke(service, "buildPhoneHash",
                invoke(service, "normalizePhone", "13812345678", "CN"), "default");
        byte[] hash2 = (byte[]) invoke(service, "buildPhoneHash",
                invoke(service, "normalizePhone", "+86 138 1234 5678", "CN"), "default");
        byte[] hash3 = (byte[]) invoke(serviceWithOtherHashKey, "buildPhoneHash", "+8613812345678", "default");

        assertArrayEquals(hash1, hash2);
        assertEquals(32, hash1.length);
        assertFalse(Arrays.equals(hash1, hash3));
    }

    @Test
    void encryptUsesRandomIvAndDecryptRequiresMatchingKeyVersion() throws Exception {
        Object service = service("v1", ENC_KEY, "h1", HASH_KEY);

        Object first = invoke(service, "encryptPhone", "+8613812345678", "default", "customer-1");
        Object second = invoke(service, "encryptPhone", "+8613812345678", "default", "customer-1");

        assertFalse(Arrays.equals(bytes(first, "iv"), bytes(second, "iv")));
        assertFalse(Arrays.equals(bytes(first, "ciphertext"), bytes(second, "ciphertext")));
        assertEquals(16, bytes(first, "tag").length);

        assertEquals("+8613812345678", invoke(service, "decryptPhone",
                bytes(first, "ciphertext"), bytes(first, "iv"), bytes(first, "tag"), value(first, "keyVersion"), "default", "customer-1"));
        assertPhoneCode("PHONE_DECRYPT_FAILED", () -> invoke(service, "decryptPhone",
                bytes(first, "ciphertext"), bytes(first, "iv"), bytes(first, "tag"), "missing", "default", "customer-1"));
    }

    @Test
    void decryptClassifiesMissingKeyVersionAndAadMismatch() throws Exception {
        Object service = service("v1", ENC_KEY, "h1", HASH_KEY);
        Object encrypted = invoke(service, "encryptPhone", "+8613812345678", "default", "customer-1");

        assertPhoneError("PHONE_DECRYPT_FAILED", "KEY_VERSION_NOT_FOUND", () -> invoke(service, "decryptPhone",
                bytes(encrypted, "ciphertext"), bytes(encrypted, "iv"), bytes(encrypted, "tag"), "missing", "default", "customer-1"));
        assertPhoneError("PHONE_DECRYPT_FAILED", "AEAD_TAG_MISMATCH", () -> invoke(service, "decryptPhone",
                bytes(encrypted, "ciphertext"), bytes(encrypted, "iv"), bytes(encrypted, "tag"), value(encrypted, "keyVersion"), "default", "customer-2"));
    }

    @Test
    void maskPhoneIsStableForChinaAndInternationalNumbers() throws Exception {
        Object service = service("v1", ENC_KEY, "h1", HASH_KEY);

        assertEquals("138****5678", invoke(service, "maskPhone", "+8613812345678"));
        assertEquals("138****5678", invoke(service, "maskPhone", "+86 138 1234 5678"));
        assertEquals("+1****2671", invoke(service, "maskPhone", "+14155552671"));
    }

    @Test
    void legacyDigitsNormalizationPreservesSearchableImportedValues() throws Exception {
        Object service = service("v1", ENC_KEY, "h1", HASH_KEY);

        assertEquals("10000000000", invoke(service, "normalizeLegacyDigitsPhone", "100 0000 0000"));
        assertEquals("100****0000", invoke(service, "maskLegacyDigitsPhone", "10000000000"));
        assertEquals("****1", invoke(service, "maskLegacyDigitsPhone", "1"));
        assertEquals("LEGACY_DIGITS_V1", invoke(service, "legacyDigitsNormalizedVersion"));
    }

    private static Object service(String encVersion, String encKey, String hashVersion, String hashKey) throws Exception {
        Class<?> serviceClass = Class.forName("org.example.xyyx.service.PhonePrivacyService");
        return serviceClass.getMethod("forTesting", String.class, String.class, String.class, String.class)
                .invoke(null, encVersion, encKey, hashVersion, hashKey);
    }

    private static Object invoke(Object target, String method, Object... args) throws Exception {
        Method found = Arrays.stream(target.getClass().getMethods())
                .filter(candidate -> candidate.getName().equals(method) && candidate.getParameterCount() == args.length)
                .findFirst()
                .orElseThrow();
        try {
            return found.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    private static byte[] bytes(Object target, String method) throws Exception {
        return (byte[]) value(target, method);
    }

    private static Object value(Object target, String method) throws Exception {
        return target.getClass().getMethod(method).invoke(target);
    }

    private static void assertPhoneCode(String code, ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
        } catch (Exception e) {
            assertEquals(code, e.getClass().getMethod("code").invoke(e));
            return;
        }
        throw new AssertionError("expected phone privacy exception " + code);
    }

    private static void assertPhoneError(String code, String detailCode, ThrowingRunnable runnable) throws Exception {
        try {
            runnable.run();
        } catch (Exception e) {
            assertEquals(code, e.getClass().getMethod("code").invoke(e));
            assertEquals(detailCode, e.getClass().getMethod("detailCode").invoke(e));
            return;
        }
        throw new AssertionError("expected phone privacy exception " + code);
    }

    private static String key(String value) {
        byte[] bytes = new byte[32];
        byte[] seed = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(seed, 0, bytes, 0, Math.min(seed.length, bytes.length));
        return Base64.getEncoder().encodeToString(bytes);
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Exception;
    }
}
