package com.virpemart.billing.print;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class MarathiTransliteratorTest {

    private static String mr(String english) {
        return MarathiTransliterator.toMarathi(english);
    }

    @Test
    void theUsersExampleName() {
        assertEquals("उमेश विरपे", mr("Umesh Virape"));
        assertEquals("उमेश विरपे", mr("UMESH VIRAPE"), "capital letters are fine");
    }

    @Test
    void namesTheRulesSpellCorrectly() {
        assertEquals("संदेश", mr("Sandesh"), "n before a consonant is ं");
        assertEquals("हेमंत", mr("Hemant"));
        assertEquals("शेवकर", mr("Shevkar"), "sounds in the middle are not joined");
        assertEquals("नरेंद्र", mr("Narendra"), "joined dr, short a after it");
        assertEquals("ममता", mr("Mamata"), "a final a is long");
        assertEquals("रवी", mr("Ravi"), "a final i is long");
        assertEquals("गुरू", mr("Guru"), "a final u is long");
        assertEquals("जयवंत", mr("Jaywant"));
        assertEquals("श्रीधर", mr("Shreedhar"), "joined shr at the start");
        assertEquals("अश्विन", mr("Ashwin"), "joined before w");
    }

    @Test
    void wordListFixesCommonNames() {
        assertEquals("रमेश पाटील", mr("Ramesh Patil"));
        assertEquals("संतोष गायकवाड", mr("Santosh Gaikwad"));
        assertEquals("विठ्ठल कुलकर्णी", mr("Vitthal Kulkarni"));
    }

    @Test
    void nameEndingsFromTheWordList() {
        assertEquals("चंद्रकांत", mr("Chandrakant"));
        assertEquals("नंदकुमार", mr("Nandkumar"));
        assertEquals("बाबासाहेब", mr("Babasaheb"));
        assertEquals("गणपतराव", mr("Ganpatrao"));
    }

    @Test
    void initialsAreSpelledAsSpoken() {
        assertEquals("एस. के. पाटील", mr("S. K. Patil"));
    }

    @Test
    void marathiTextNumbersAndPunctuationAreKept() {
        assertEquals("उमेश विरपे", mr("उमेश Virape"));
        assertEquals("राम (2)", mr("Ram (2)"));
        assertEquals("", mr(""));
        assertNull(mr(null));
    }

    @Test
    void wordListHasCleanLines() throws IOException {
        String text;
        try (InputStream in = getClass().getResourceAsStream("/print/marathi-names.txt")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Set<String> seen = new HashSet<>();
        for (String line : text.split("\n")) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String english = line.substring(0, line.indexOf('='));
            assertTrue(english.matches("-?[a-z]+"), "English side must be plain small letters: " + line);
            assertTrue(seen.add(english), "listed twice: " + english);
        }
    }
}
