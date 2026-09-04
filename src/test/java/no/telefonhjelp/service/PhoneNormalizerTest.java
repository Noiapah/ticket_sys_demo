package no.telefonhjelp.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class PhoneNormalizerTest {
    @ParameterizedTest
    @ValueSource(strings = {"99 12 34 56", "99123456", "+47 991 23 456", "0047 991 23 456"})
    void recognizesEquivalentNorwegianNumbers(String input) {
        assertThat(PhoneNormalizer.normalize(input)).isEqualTo("+4799123456");
    }
}

