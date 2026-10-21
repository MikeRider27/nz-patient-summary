package nz.portfolio.patientsummary.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class NhiTest {

    /** Example NHIs published by Health NZ ("Upcoming changes to the NHI"). */
    @ParameterizedTest
    @ValueSource(strings = {"ZAA0067", "ZAA0075", "ZAA0083", "ACA31FM", "ASE37QK", "ARE62RS"})
    void acceptsOfficialExamples(String nhi) {
        assertThat(Nhi.isValid(nhi)).isTrue();
    }

    @Test
    void isCaseInsensitive() {
        assertThat(Nhi.isValid("zaa0067")).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ZAA0068",   // wrong numeric check digit
            "ACA31FN",   // wrong alphabetic check letter
            "IAA0067",   // I is not in the NHI alphabet
            "OAA0067",   // O is not in the NHI alphabet
            "ZAA006",    // too short
            "ZAA00677",  // too long
            "ZA10067",   // digit in the letter block
            "ZAA0A67",   // letter in the digit block
            "ZAA00F7",   // mixed check format
    })
    void rejectsInvalidNumbers(String nhi) {
        assertThat(Nhi.isValid(nhi)).isFalse();
    }

    @Test
    void rejectsNullAndBlank() {
        assertThat(Nhi.isValid(null)).isFalse();
        assertThat(Nhi.isValid("")).isFalse();
    }

    /** Validation routine note: no digit can be appended to ZZZ004 to make a valid NHI (remainder 0). */
    @Test
    void prefixWithRemainderZeroCanNeverBeValid() {
        assertThat(Nhi.legacyCheckDigit("ZZZ004")).isNull();
        for (char digit = '0'; digit <= '9'; digit++) {
            assertThat(Nhi.isValid("ZZZ004" + digit)).isFalse();
        }
    }

    @Test
    void checkValueTenBecomesZero() {
        // find a prefix whose check value is 10 and confirm it is encoded as '0'
        String prefix = findPrefixGivingCheckTen();
        assertThat(Nhi.legacyCheckDigit(prefix)).isEqualTo('0');
        assertThat(Nhi.isValid(prefix + "0")).isTrue();
    }

    private static String findPrefixGivingCheckTen() {
        for (int n = 0; n <= 999; n++) {
            String prefix = "ZAA" + String.format("%03d", n);
            if (11 - (Nhi.weightedSum(prefix) % 11) == 10) {
                return prefix;
            }
        }
        throw new AssertionError("no ZAA prefix yields check value 10");
    }

    @ParameterizedTest
    @EnumSource(Nhi.Format.class)
    void generatedNumbersAreValidAndSynthetic(Nhi.Format format) {
        SplittableRandom random = new SplittableRandom(42);
        for (int i = 0; i < 5_000; i++) {
            String nhi = Nhi.generate(format, random);
            assertThat(Nhi.isValid(nhi)).as(nhi).isTrue();
            assertThat(nhi).startsWith("Z");
            if (format == Nhi.Format.LEGACY) {
                assertThat(nhi).matches("Z[A-HJ-NP-Z]{2}\\d{4}");
            } else {
                assertThat(nhi).matches("Z[A-HJ-NP-Z]{2}\\d{2}[A-HJ-NP-Z]{2}");
            }
        }
    }

    @Test
    void generationIsReproducibleForASeed() {
        assertThat(Nhi.generate(Nhi.Format.EXPANDED, new SplittableRandom(7)))
                .isEqualTo(Nhi.generate(Nhi.Format.EXPANDED, new SplittableRandom(7)));
    }
}
