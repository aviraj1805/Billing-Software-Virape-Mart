package com.virpemart.billing.print;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Writes a name typed in English letters in Marathi (Devanagari) letters, for the printed bill only.
 * For example "Umesh Virape" becomes "उमेश विरपे".
 *
 * <p>English spelling does not show every Marathi sound ("Patil" could be पतिल or पाटील), so this is a best guess.
 * Common names and name endings that the rules get wrong are listed in {@code print/marathi-names.txt};
 * add more there.
 * Text that is already in Marathi, numbers and punctuation are kept as they are.
 */
public final class MarathiTransliterator {

    private static final String HALANT = "्";
    private static final String ANUSVARA = "ं";
    private static final String WORD_LIST = "/print/marathi-names.txt";

    /** English letter groups and their Marathi consonant, longest groups first so "sh" wins over "s". */
    private static final List<String[]> CONSONANTS = List.of(
            new String[] {"ksh", "क्ष"}, new String[] {"dny", "ज्ञ"}, new String[] {"gny", "ज्ञ"},
            new String[] {"chh", "छ"},
            new String[] {"ch", "च"}, new String[] {"sh", "श"}, new String[] {"kh", "ख"}, new String[] {"gh", "घ"},
            new String[] {"jh", "झ"}, new String[] {"th", "थ"}, new String[] {"dh", "ध"}, new String[] {"ph", "फ"},
            new String[] {"bh", "भ"},
            new String[] {"x", "क्ष"}, new String[] {"k", "क"}, new String[] {"g", "ग"}, new String[] {"j", "ज"},
            new String[] {"t", "त"}, new String[] {"d", "द"}, new String[] {"n", "न"}, new String[] {"p", "प"},
            new String[] {"f", "फ"}, new String[] {"b", "ब"}, new String[] {"m", "म"}, new String[] {"y", "य"},
            new String[] {"r", "र"}, new String[] {"l", "ल"}, new String[] {"v", "व"}, new String[] {"w", "व"},
            new String[] {"s", "स"}, new String[] {"h", "ह"}, new String[] {"z", "झ"}, new String[] {"c", "क"},
            new String[] {"q", "क"});

    /** English vowel, Marathi vowel at the start of a syllable, and the sign added to a consonant. */
    private static final List<String[]> VOWELS = List.of(
            new String[] {"aa", "आ", "ा"}, new String[] {"ee", "ई", "ी"}, new String[] {"ii", "ई", "ी"},
            new String[] {"oo", "ऊ", "ू"}, new String[] {"uu", "ऊ", "ू"}, new String[] {"ai", "ऐ", "ै"},
            new String[] {"au", "औ", "ौ"},
            new String[] {"a", "अ", ""}, new String[] {"i", "इ", "ि"}, new String[] {"u", "उ", "ु"},
            new String[] {"e", "ए", "े"}, new String[] {"o", "ओ", "ो"});

    /** How a single letter such as the initial in "S. K. Patil" is said in Marathi. */
    private static final String[] LETTER_NAMES = {
        "ए", "बी", "सी", "डी", "ई", "एफ", "जी", "एच", "आय", "जे", "के", "एल", "एम",
        "एन", "ओ", "पी", "क्यू", "आर", "एस", "टी", "यू", "व्ही", "डब्ल्यू", "एक्स", "वाय", "झेड"};

    private static final Map<String, String> KNOWN_WORDS = loadWordList();

    private MarathiTransliterator() {
    }

    /** The name in Marathi letters, or null if {@code text} is null. */
    public static String toMarathi(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length()) {
            if (isEnglishLetter(text.charAt(i))) {
                int end = i;
                while (end < text.length() && isEnglishLetter(text.charAt(end))) {
                    end++;
                }
                out.append(word(text.substring(i, end).toLowerCase(Locale.ROOT)));
                i = end;
            } else {
                out.append(text.charAt(i));
                i++;
            }
        }
        return out.toString();
    }

    private static String word(String word) {
        String known = KNOWN_WORDS.get(word);
        if (known != null) {
            return known;
        }
        if (word.length() == 1) {
            return LETTER_NAMES[word.charAt(0) - 'a'];
        }
        // A known name ending such as "-kant" (कांत): Chandrakant is चंद्र + कांत.
        for (int split = 1; split < word.length() - 1; split++) {
            String ending = KNOWN_WORDS.get("-" + word.substring(split));
            if (ending != null) {
                String start = word.substring(0, split);
                String knownStart = KNOWN_WORDS.get(start);
                return (knownStart != null ? knownStart : sounds(start, false)) + ending;
            }
        }
        return sounds(word, true);
    }

    /**
     * Writes a word sound by sound.
     *
     * @param wholeWord true if the word ends here, so a final "a", "i" or "u" is written long
     */
    private static String sounds(String word, boolean wholeWord) {
        StringBuilder out = new StringBuilder();
        // The last consonant written that has no vowel sign yet, or null after a vowel.
        String waitingConsonant = null;
        boolean joined = false;
        boolean seenVowel = false;
        int i = 0;
        while (i < word.length()) {
            String[] vowel = match(VOWELS, word, i);
            if (vowel != null) {
                i += vowel[0].length();
                if (wholeWord && i == word.length()) {
                    vowel = longAtEnd(vowel, waitingConsonant != null && !joined);
                }
                out.append(waitingConsonant != null ? vowel[2] : vowel[1]);
                waitingConsonant = null;
                seenVowel = true;
                continue;
            }
            String[] consonant = match(CONSONANTS, word, i);
            if (consonant == null) {
                i++;
                continue;
            }
            i += consonant[0].length();
            if (waitingConsonant == null && seenVowel && isNasal(consonant[0], word, i)) {
                out.append(ANUSVARA);
                continue;
            }
            joined = waitingConsonant != null && joins(waitingConsonant, consonant[0], seenVowel);
            if (joined) {
                out.append(HALANT);
            }
            out.append(consonant[1]);
            waitingConsonant = consonant[0];
        }
        return out.toString();
    }

    /**
     * Names end with a long vowel: Sunita is सुनिता, Ravi is रवी, Raju is राजू. A final "a" after joined
     * consonants stays short: Narendra is नरेंद्र.
     */
    private static String[] longAtEnd(String[] vowel, boolean afterSingleConsonant) {
        return switch (vowel[0]) {
            case "a" -> afterSingleConsonant ? VOWELS.get(0) : vowel;
            case "i" -> VOWELS.get(1);
            case "u" -> VOWELS.get(3);
            default -> vowel;
        };
    }

    /**
     * Whether two consonants are written joined (with ्). English spelling usually drops the "a" between two
     * sounds, so they join only in clusters that Marathi names really have: at the start of a word (श्री, स्व),
     * before r, y or v (प्र, त्य, श्व), m/n/l/v/r before h (म्ह), and a doubled letter (त्त).
     * Deshmukh is देशमुख, not देश्मुख.
     */
    private static boolean joins(String first, String second, boolean seenVowel) {
        if (first.equals("y")) {
            return false;
        }
        if (!seenVowel || first.equals(second)) {
            return true;
        }
        return switch (second) {
            case "r", "y", "v", "w" -> true;
            case "h" -> "mnlvwr".contains(first);
            default -> false;
        };
    }

    /** "n" before most consonants ("Shinde" शिंदे) and "m" before p or b ("Sampat" संपत) are written as ं. */
    private static boolean isNasal(String letter, String word, int next) {
        if (next >= word.length()) {
            return false;
        }
        char following = word.charAt(next);
        return switch (letter) {
            case "n" -> "kgcjtdpbs".indexOf(following) >= 0;
            case "m" -> following == 'p' || following == 'b';
            default -> false;
        };
    }

    private static String[] match(List<String[]> table, String word, int at) {
        for (String[] entry : table) {
            if (word.startsWith(entry[0], at)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isEnglishLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** Reads "english=मराठी" lines. Blank lines and lines starting with # are skipped. */
    private static Map<String, String> loadWordList() {
        Map<String, String> words = new HashMap<>();
        try (InputStream in = MarathiTransliterator.class.getResourceAsStream(WORD_LIST)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + WORD_LIST);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int equals = line.indexOf('=');
                if (equals <= 0) {
                    throw new IllegalStateException("Bad line in " + WORD_LIST + ": " + line);
                }
                words.put(line.substring(0, equals).strip().toLowerCase(Locale.ROOT), line.substring(equals + 1).strip());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Map.copyOf(words);
    }
}
