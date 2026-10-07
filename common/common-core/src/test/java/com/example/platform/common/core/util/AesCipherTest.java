package com.example.platform.common.core.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AesCipherTest {

    private static final String KEY = "Y29ubi1wbGF0Zm9ybS1kZXYtYWVzLWtleS0zMmJ5dGU=";

    private final AesCipher cipher = new AesCipher(KEY);

    @Test
    void encryptThenDecryptReturnsOriginal() {
        String plain = "p@ssw0rd-中文-123";

        String encrypted = cipher.encrypt(plain);

        assertThat(encrypted).isNotEqualTo(plain);
        assertThat(cipher.decrypt(encrypted)).isEqualTo(plain);
    }

    @Test
    void encryptUsesRandomIvSoCipherTextDiffers() {
        String first = cipher.encrypt("same-input");
        String second = cipher.encrypt("same-input");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("same-input");
        assertThat(cipher.decrypt(second)).isEqualTo("same-input");
    }

    @Test
    void decryptWithAnotherKeyFails() {
        String encrypted = cipher.encrypt("secret");
        AesCipher other = new AesCipher(AesCipher.generateKey());

        assertThatThrownBy(() -> other.decrypt(encrypted))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeyWithIllegalLength() {
        assertThatThrownBy(() -> new AesCipher("YWJj"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("16/24/32");
    }

    @Test
    void generatedKeyIsAccepted() {
        AesCipher generated = new AesCipher(AesCipher.generateKey());
        assertThat(generated.decrypt(generated.encrypt("hello"))).isEqualTo("hello");
    }

    @Test
    void maskKeepsOnlyEdges() {
        assertThat(AesCipher.mask("platform123")).isEqualTo("p******3");
        assertThat(AesCipher.mask("ab")).isEqualTo("**");
        assertThat(AesCipher.mask("")).isEmpty();
    }
}
