package org.example.xyyx.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.example.xyyx.entity.Survey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class PhonePrivacyService {
    private static final String DEFAULT_REGION = "CN";
    private static final String NORMALIZED_VERSION = "E164_V1";
    private static final String LEGACY_DIGITS_NORMALIZED_VERSION = "LEGACY_DIGITS_V1";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BYTES = 16;

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
    private final SecureRandom secureRandom = new SecureRandom();
    private final String encKeyVersion;
    private final Map<String, byte[]> encKeys;
    private final String hashKeyVersion;
    private final Map<String, byte[]> hashKeys;

    @Autowired
    public PhonePrivacyService(
            @Value("${xyyx.phone-privacy.enc-key-version:${XYYX_PHONE_ENC_KEY_VERSION:v1}}") String encKeyVersion,
            @Value("${xyyx.phone-privacy.enc-keys:${XYYX_PHONE_ENC_KEYS:}}") String encKeys,
            @Value("${xyyx.phone-privacy.hash-key-version:${XYYX_PHONE_HASH_KEY_VERSION:v1}}") String hashKeyVersion,
            @Value("${xyyx.phone-privacy.hash-keys:${XYYX_PHONE_HASH_KEYS:}}") String hashKeys) {
        this.encKeyVersion = encKeyVersion;
        this.encKeys = parseKeys(encKeys);
        this.hashKeyVersion = hashKeyVersion;
        this.hashKeys = parseKeys(hashKeys);
    }

    private PhonePrivacyService(String encKeyVersion, byte[] encKey, String hashKeyVersion, byte[] hashKey) {
        this.encKeyVersion = encKeyVersion;
        this.encKeys = Map.of(encKeyVersion, encKey);
        this.hashKeyVersion = hashKeyVersion;
        this.hashKeys = Map.of(hashKeyVersion, hashKey);
    }

    public static PhonePrivacyService forTesting(
            String encKeyVersion,
            String encKeyBase64,
            String hashKeyVersion,
            String hashKeyBase64) {
        return new PhonePrivacyService(
                encKeyVersion,
                decodeAesKey(encKeyBase64),
                hashKeyVersion,
                decodeAesKey(hashKeyBase64)
        );
    }

    public String normalizePhone(String rawPhone, String defaultRegion) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new PhonePrivacyException("PHONE_EMPTY");
        }
        String region = defaultRegion == null || defaultRegion.isBlank() ? DEFAULT_REGION : defaultRegion.trim().toUpperCase();
        try {
            PhoneNumber number = phoneNumberUtil.parse(rawPhone.trim(), region);
            if (!phoneNumberUtil.isValidNumber(number)) {
                throw new PhonePrivacyException("PHONE_INVALID");
            }
            return phoneNumberUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw new PhonePrivacyException("PHONE_INVALID");
        }
    }

    public String normalizeLegacyDigitsPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new PhonePrivacyException("PHONE_EMPTY");
        }
        String digits = rawPhone.replaceAll("\\D", "");
        if (digits.isBlank()) {
            throw new PhonePrivacyException("PHONE_INVALID");
        }
        return digits;
    }

    public EncryptedPhone encryptPhone(String normalizedPhone, String tenantId, String customerUuid) {
        byte[] iv = new byte[GCM_IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(requireKey(encKeys, encKeyVersion), "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(tenantId, customerUuid));
            byte[] sealed = cipher.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));
            int tagOffset = sealed.length - GCM_TAG_BYTES;
            return new EncryptedPhone(
                    Arrays.copyOf(sealed, tagOffset),
                    iv,
                    Arrays.copyOfRange(sealed, tagOffset, sealed.length),
                    encKeyVersion
            );
        } catch (GeneralSecurityException e) {
            throw new PhonePrivacyException("PHONE_ENCRYPT_FAILED");
        }
    }

    public String decryptPhone(Survey survey) {
        return decryptPhone(
                survey.getPhoneCiphertext(),
                survey.getPhoneIv(),
                survey.getPhoneTag(),
                survey.getPhoneEncKeyVersion(),
                survey.getTenantId(),
                survey.getCustomerUuid()
        );
    }

    public String decryptPhone(
            byte[] ciphertext,
            byte[] iv,
            byte[] tag,
            String keyVersion,
            String tenantId,
            String customerUuid) {
        try {
            if (ciphertext == null || ciphertext.length == 0 || iv == null || tag == null
                    || keyVersion == null || keyVersion.isBlank() || tenantId == null || tenantId.isBlank()
                    || customerUuid == null || customerUuid.isBlank()) {
                throw new PhonePrivacyException("PHONE_PRIVACY_NOT_READY", "PHONE_PRIVACY_FIELDS_MISSING");
            }
            if (iv.length != GCM_IV_BYTES || tag.length != GCM_TAG_BYTES) {
                throw new PhonePrivacyException("PHONE_PRIVACY_NOT_READY", "CIPHER_FORMAT_INVALID");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] key = encKeys.get(keyVersion);
            if (key == null) {
                throw new PhonePrivacyException("PHONE_DECRYPT_FAILED", "KEY_VERSION_NOT_FOUND");
            }
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(aad(tenantId, customerUuid));
            byte[] sealed = new byte[ciphertext.length + tag.length];
            System.arraycopy(ciphertext, 0, sealed, 0, ciphertext.length);
            System.arraycopy(tag, 0, sealed, ciphertext.length, tag.length);
            return new String(cipher.doFinal(sealed), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new PhonePrivacyException("PHONE_DECRYPT_FAILED", "AEAD_TAG_MISMATCH");
        } catch (PhonePrivacyException e) {
            throw e;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new PhonePrivacyException("PHONE_DECRYPT_FAILED", "CIPHER_FORMAT_INVALID");
        }
    }

    public byte[] buildPhoneHash(String normalizedPhone, String tenantId) {
        return hmac(hashKeyVersion, "phone-hash:v1|tenant=" + tenantId + "|value=" + normalizedPhone);
    }

    public byte[] buildPhoneSuffix4Hash(String normalizedPhone, String tenantId) {
        String last4 = normalizedPhone.substring(normalizedPhone.length() - 4);
        return buildPhoneSuffix4HashFromLast4(last4, tenantId);
    }

    public byte[] buildPhoneSuffix4HashFromLast4(String last4, String tenantId) {
        if (last4 == null || !last4.matches("\\d{4}")) {
            throw new PhonePrivacyException("PHONE_SUFFIX4_INVALID");
        }
        return hmac(hashKeyVersion, "phone-suffix4:v1|tenant=" + tenantId + "|value=" + last4);
    }

    public String maskPhone(String normalizedPhone) {
        String e164 = normalizePhone(normalizedPhone, DEFAULT_REGION);
        try {
            PhoneNumber number = phoneNumberUtil.parse(e164, DEFAULT_REGION);
            String national = Long.toString(number.getNationalNumber());
            String last4 = national.substring(Math.max(0, national.length() - 4));
            if (number.getCountryCode() == 86 && national.length() == 11) {
                return national.substring(0, 3) + "****" + last4;
            }
            return "+" + number.getCountryCode() + "****" + last4;
        } catch (NumberParseException e) {
            throw new PhonePrivacyException("PHONE_INVALID");
        }
    }

    public String maskLegacyDigitsPhone(String digitsPhone) {
        String digits = normalizeLegacyDigitsPhone(digitsPhone);
        String last4 = digits.substring(Math.max(0, digits.length() - 4));
        if (digits.length() >= 7) {
            return digits.substring(0, 3) + "****" + last4;
        }
        return "****" + last4;
    }

    public String currentHashKeyVersion() {
        return hashKeyVersion;
    }

    public String normalizedVersion() {
        return NORMALIZED_VERSION;
    }

    public String legacyDigitsNormalizedVersion() {
        return LEGACY_DIGITS_NORMALIZED_VERSION;
    }

    private byte[] hmac(String keyVersion, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requireKey(hashKeys, keyVersion), "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new PhonePrivacyException("PHONE_HASH_FAILED");
        }
    }

    private byte[] aad(String tenantId, String customerUuid) {
        String value = "customer.phone:v1|tenant=" + tenantId + "|customer=" + customerUuid;
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Map<String, byte[]> parseKeys(String encodedKeys) {
        Map<String, byte[]> parsed = new HashMap<>();
        if (encodedKeys == null || encodedKeys.isBlank()) {
            return parsed;
        }
        for (String part : encodedKeys.split(",")) {
            String[] pair = part.trim().split(":", 2);
            if (pair.length == 2 && !pair[0].isBlank() && !pair[1].isBlank()) {
                parsed.put(pair[0].trim(), decodeAesKey(pair[1].trim()));
            }
        }
        return parsed;
    }

    private static byte[] decodeAesKey(String keyBase64) {
        byte[] key = Base64.getDecoder().decode(keyBase64);
        if (key.length != 32) {
            throw new PhonePrivacyException("PHONE_KEY_INVALID");
        }
        return key;
    }

    private byte[] requireKey(Map<String, byte[]> keys, String version) {
        byte[] key = keys.get(version);
        if (key == null) {
            throw new PhonePrivacyException("PHONE_KEY_MISSING");
        }
        return key;
    }

    public record EncryptedPhone(byte[] ciphertext, byte[] iv, byte[] tag, String keyVersion) {
    }
}
