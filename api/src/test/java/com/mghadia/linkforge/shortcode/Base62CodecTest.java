package com.mghadia.linkforge.shortcode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Base62CodecTest {

    @Test
    void encodesSmallValuesWithinTheAlphabet() {
        assertThat(Base62Codec.encode(0)).isEqualTo("0");
        assertThat(Base62Codec.encode(1)).isEqualTo("1");
        assertThat(Base62Codec.encode(10)).isEqualTo("A");
        assertThat(Base62Codec.encode(61)).isEqualTo("z");
        assertThat(Base62Codec.encode(62)).isEqualTo("10");
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, 1L, 61L, 62L, 1_000_000L, 3_521_614_606_207L, Long.MAX_VALUE})
    void decodeInvertsEncode(long value) {
        assertThat(Base62Codec.decode(Base62Codec.encode(value))).isEqualTo(value);
    }

    @Test
    void sequenceValuesProduceDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (long value = 1_000_000L; value < 1_010_000L; value++) {
            assertThat(codes.add(Base62Codec.encode(value)))
                    .as("code for %d was already produced", value)
                    .isTrue();
        }
    }

    @Test
    void codesForRealisticSequenceValuesStayShort() {
        // The sequence starts at 1_000_000, so the first codes are four characters and stay
        // short for a long time: six characters covers a billion links.
        assertThat(Base62Codec.encode(1_000_000L)).hasSize(4);
        assertThat(Base62Codec.encode(1_000_000_000L)).hasSize(6);
    }

    @Test
    void rejectsNegativeValues() {
        assertThatThrownBy(() -> Base62Codec.encode(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonBase62Input() {
        assertThatThrownBy(() -> Base62Codec.decode("ab-cd"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
