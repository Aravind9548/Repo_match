package com.repomatch.jobs;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import com.repomatch.config.AppProperties;

/**
 * There are no accounts yet, so alerts and applications belong to whoever holds a random key.
 *
 * <p>Only the SHA-256 of the key is stored. Emails can't contain the key (we never keep it), so they carry a
 * signed link instead: {@code s.<ownerHash>.<hmac>}. Either form is accepted wherever a key is.
 */
@Component
public class OwnerKeys {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SIGNED_PREFIX = "s.";

    private final byte[] secret;

    public OwnerKeys(AppProperties props) {
        String s = props.notifications().linkSecret();
        // No secret configured: signed links just won't verify, raw keys still work.
        this.secret = s == null || s.isBlank() ? null : s.getBytes(StandardCharsets.UTF_8);
    }

    public static String newKey() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The owner hash behind a raw key or a signed email link. */
    public String resolve(String credential) {
        if (credential == null || credential.isBlank()) {
            throw new MissingOwnerKeyException();
        }
        if (credential.startsWith(SIGNED_PREFIX)) {
            String[] parts = credential.split("\\.");
            if (parts.length != 3 || secret == null || !MessageDigest.isEqual(
                    sign(parts[1]).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) {
                throw new MissingOwnerKeyException();
            }
            return parts[1];
        }
        if (credential.length() < 20 || credential.length() > 64) {
            throw new MissingOwnerKeyException();
        }
        return sha256(credential);
    }

    /** A credential for links in emails, or null when no signing secret is configured. */
    public String signedLink(String ownerHash) {
        return secret == null ? null : SIGNED_PREFIX + ownerHash + "." + sign(ownerHash);
    }

    private String sign(String ownerHash) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(ownerHash.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String sha256(String key) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static class MissingOwnerKeyException extends RuntimeException {
        public MissingOwnerKeyException() {
            super("Missing or invalid tracker key. Open the link from your alert email, or start a new tracker on this device.");
        }
    }
}
