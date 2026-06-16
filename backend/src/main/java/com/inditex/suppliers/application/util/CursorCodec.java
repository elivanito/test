package com.inditex.suppliers.application.util;

import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Cursor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Opaque base64-url codec for keyset cursors, with explicit format versioning
 * and an HMAC-SHA256 integrity tag.
 *
 * <p>Wire format (after base64-url decoding):
 * {@code "v1:<doubleToRawLongBits(score)>:<duns>:<base64(hmac)>"}.
 * Versioning lets us bump the schema (e.g. add a partition key) without
 * silently breaking older cursors. The HMAC stops clients from forging
 * positions to jump the queue — at the price of one keyed hash per request.</p>
 *
 * <p>{@code score} is encoded as the raw {@link Double#doubleToRawLongBits} long
 * to avoid locale-sensitive {@link Double#toString} and to guarantee bit-exact
 * round-trip, which the keyset SQL depends on for stable ordering.</p>
 *
 * <p>The HMAC key is injected by composition root (see {@code CursorCodecConfig}),
 * defaults to a fixed dev secret if not configured.</p>
 */
public final class CursorCodec {

    private static final String VERSION = "v1";
    private static final String HMAC_ALG = "HmacSHA256";

    private final byte[] key;

    public CursorCodec(byte[] key) {
        if (key == null || key.length == 0) {
            throw new IllegalArgumentException("cursor key must not be empty");
        }
        this.key = key.clone();
    }

    public String encode(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        String payload = VERSION + ":" + Double.doubleToRawLongBits(cursor.score()) + ":" + cursor.duns();
        String tag = sign(payload);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + ":" + tag).getBytes(StandardCharsets.UTF_8));
    }

    public Cursor decode(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = raw.split(":");
            if (parts.length != 4) {
                throw new IllegalArgumentException("cursor is malformed");
            }
            if (!VERSION.equals(parts[0])) {
                throw new IllegalArgumentException("cursor version unsupported: " + parts[0]);
            }
            String payload = parts[0] + ":" + parts[1] + ":" + parts[2];
            String expected = sign(payload);
            if (!constantTimeEquals(expected, parts[3])) {
                throw new IllegalArgumentException("cursor signature mismatch");
            }
            double score = Double.longBitsToDouble(Long.parseLong(parts[1]));
            long duns = Long.parseLong(parts[2]);
            return new Cursor(score, duns);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("cursor is malformed", e);
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(key, HMAC_ALG));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }
}
