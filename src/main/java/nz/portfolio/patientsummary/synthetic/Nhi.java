package nz.portfolio.patientsummary.synthetic;

import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * National Health Index (NHI) number format and check-digit rules, as published by Health NZ
 * in the "NHI Validation Routine" (April 2023 v6) and HISO 10046.
 *
 * <ul>
 *   <li>Legacy format {@code AAANNNC}: three letters, three digits, numeric check digit (modulus 11).</li>
 *   <li>Expanded format {@code AAANNAX}: three letters, two digits, one letter, alphabetic check
 *       letter (modulus 23).</li>
 * </ul>
 * Letters I and O are never used; the remaining 24 letters map to the values 1..24.
 */
public final class Nhi {

    /** Alphabet conversion table: index + 1 is the letter value (A=1 ... Z=24, without I and O). */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    public enum Format { LEGACY, EXPANDED }

    private Nhi() {
    }

    /** Returns true when {@code nhi} has a valid structure and check digit. Case-insensitive. */
    public static boolean isValid(String nhi) {
        if (nhi == null || nhi.length() != 7) {
            return false;
        }
        String value = nhi.toUpperCase(Locale.ROOT);
        for (int i = 0; i < 3; i++) {
            if (letterValue(value.charAt(i)) < 0) {
                return false;
            }
        }
        if (!Character.isDigit(value.charAt(3)) || !Character.isDigit(value.charAt(4))) {
            return false;
        }
        char sixth = value.charAt(5);
        char last = value.charAt(6);
        if (Character.isDigit(sixth) && Character.isDigit(last)) {
            Character expected = legacyCheckDigit(value.substring(0, 6));
            return expected != null && expected == last;
        }
        if (letterValue(sixth) >= 0 && letterValue(last) >= 0) {
            return expandedCheckLetter(value.substring(0, 6)) == last;
        }
        return false;
    }

    /**
     * Generates a random, structurally valid NHI. Synthetic NHIs always start with {@code Z},
     * mirroring the example numbers Health NZ uses in its own documentation, to make them easy to
     * recognise. A valid check digit does NOT guarantee the number is unassigned, which is why every
     * generated resource is additionally tagged as test data (HTEST).
     */
    public static String generate(Format format, RandomGenerator random) {
        while (true) {
            StringBuilder base = new StringBuilder("Z");
            base.append(randomLetter(random)).append(randomLetter(random));
            base.append(random.nextInt(10)).append(random.nextInt(10));
            if (format == Format.LEGACY) {
                base.append(random.nextInt(10));
                Character check = legacyCheckDigit(base.toString());
                if (check != null) {
                    return base.append(check).toString();
                }
                // remainder 0 means no check digit can make this prefix valid: draw again
            } else {
                base.append(randomLetter(random));
                return base.append(expandedCheckLetter(base.toString())).toString();
            }
        }
    }

    /** Weighted sum of the first six characters (weights 7..2), steps 4-10 of the routine. */
    static int weightedSum(String firstSix) {
        int sum = 0;
        for (int i = 0; i < 6; i++) {
            char c = firstSix.charAt(i);
            int value = Character.isDigit(c) ? c - '0' : letterValue(c);
            sum += value * (7 - i);
        }
        return sum;
    }

    /** Legacy check digit, or {@code null} when the prefix can never be valid (check value 11). */
    static Character legacyCheckDigit(String firstSix) {
        int check = 11 - (weightedSum(firstSix) % 11);
        if (check == 11) {
            return null;
        }
        return check == 10 ? '0' : (char) ('0' + check);
    }

    /** Expanded-format check letter: 23 - (sum mod 23), converted through the alphabet table. */
    static char expandedCheckLetter(String firstSix) {
        int check = 23 - (weightedSum(firstSix) % 23);
        return ALPHABET.charAt(check - 1);
    }

    private static int letterValue(char c) {
        int index = ALPHABET.indexOf(c);
        return index < 0 ? -1 : index + 1;
    }

    private static char randomLetter(RandomGenerator random) {
        return ALPHABET.charAt(random.nextInt(ALPHABET.length()));
    }
}
