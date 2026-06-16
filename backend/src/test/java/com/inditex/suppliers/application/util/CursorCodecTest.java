package com.inditex.suppliers.application.util;

import com.inditex.suppliers.application.port.out.PotentialSupplierQuery.Cursor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CursorCodecTest {

    private final CursorCodec codec = new CursorCodec("test-secret".getBytes());

    @Test
    void round_trips_cursor() {
        Cursor original = new Cursor(42.5, 123_456_789L);
        String token = codec.encode(original);
        Cursor decoded = codec.decode(token);
        assertThat(decoded.score()).isEqualTo(42.5);
        assertThat(decoded.duns()).isEqualTo(123_456_789L);
    }

    @Test
    void encode_null_returns_null() {
        assertThat(codec.encode(null)).isNull();
    }

    @Test
    void decode_null_returns_null() {
        assertThat(codec.decode(null)).isNull();
    }

    @Test
    void decode_blank_returns_null() {
        assertThat(codec.decode("  ")).isNull();
    }

    @Test
    void decode_malformed_token_throws() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decode_tampered_token_throws() {
        Cursor original = new Cursor(10.0, 100_000_000L);
        String token = codec.encode(original);
        // Tamper with the last character
        String tampered = token.substring(0, token.length() - 1) + (token.charAt(token.length() - 1) == 'A' ? 'B' : 'A');
        assertThatThrownBy(() -> codec.decode(tampered))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void different_keys_produce_incompatible_tokens() {
        CursorCodec other = new CursorCodec("different-secret".getBytes());
        Cursor original = new Cursor(42.5, 123_456_789L);
        String token = codec.encode(original);
        assertThatThrownBy(() -> other.decode(token))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_null_key() {
        assertThatThrownBy(() -> new CursorCodec(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constructor_rejects_empty_key() {
        assertThatThrownBy(() -> new CursorCodec(new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void encodes_zero_score() {
        Cursor cursor = new Cursor(0.0, 100_000_000L);
        String token = codec.encode(cursor);
        Cursor decoded = codec.decode(token);
        assertThat(decoded.score()).isZero();
        assertThat(decoded.duns()).isEqualTo(100_000_000L);
    }

    @Test
    void preserves_score_bit_exact() {
        double score = 1.0 / 3.0;
        Cursor cursor = new Cursor(score, 999_999_999L);
        String token = codec.encode(cursor);
        Cursor decoded = codec.decode(token);
        assertThat(Double.doubleToRawLongBits(decoded.score()))
                .isEqualTo(Double.doubleToRawLongBits(score));
    }
}
