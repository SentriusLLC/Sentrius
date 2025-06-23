package io.sentrius.sso.core.utils;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void isBlankReturnsTrueForNullString() {
        assertTrue(StringUtils.isBlank(null));
    }

    @Test
    void isBlankReturnsTrueForEmptyString() {
        assertTrue(StringUtils.isBlank(""));
    }

    @Test
    void isBlankReturnsTrueForWhitespaceOnlyString() {
        assertTrue(StringUtils.isBlank("   "));
        assertTrue(StringUtils.isBlank("\t\n"));
    }

    @Test
    void isBlankReturnsFalseForNonEmptyString() {
        assertFalse(StringUtils.isBlank("test"));
        assertFalse(StringUtils.isBlank(" test "));
    }

    @Test
    void truncateLeftReturnsOriginalWhenTruncateBeginNotFound() {
        String original = "Hello World";
        String result = StringUtils.truncateLeft(original, "xyz", 5);
        assertEquals(original, result);
    }

    @Test
    void truncateLeftTruncatesCorrectlyWhenTruncateBeginFound() {
        String original = "This is a long string with target text";
        String result = StringUtils.truncateLeft(original, "target", 5);
        assertEquals("...with target text", result);
    }

    @Test
    void truncateLeftReturnsOriginalWhenStartIndexIsZero() {
        String original = "target text";
        String result = StringUtils.truncateLeft(original, "target", 10);
        assertEquals(original, result);
    }

    @Test
    void truncateRightReturnsOriginalWhenTruncateEndNotFound() {
        String original = "Hello World";
        String result = StringUtils.truncateRight(original, "xyz", 5);
        assertEquals(original, result);
    }

    @Test
    void truncateRightTruncatesCorrectlyWhenEndIndexWithinLimit() {
        String original = "This is a test string with end marker and more text";
        String result = StringUtils.truncateRight(original, "end", 10);
        assertEquals("This is a test string with end marker an...", result);
    }

    @Test
    void truncateStringCombinesBothTruncations() {
        String original = "This is a very long string with begin marker and end marker followed by more text";
        String result = StringUtils.truncateString(original, "begin", "end", 5);
        assertTrue(result.contains("begin"));
        assertTrue(result.contains("end"));
    }

    @Test
    void allToLowerCaseConvertsAllStringsToLowercase() {
        List<String> input = Arrays.asList("HELLO", "World", "TeSt");
        List<String> result = StringUtils.allToLowerCase(input);
        
        assertEquals(3, result.size());
        assertEquals("hello", result.get(0));
        assertEquals("world", result.get(1));
        assertEquals("test", result.get(2));
    }

    @Test
    void allToLowerCaseHandlesEmptyList() {
        List<String> input = Arrays.asList();
        List<String> result = StringUtils.allToLowerCase(input);
        assertTrue(result.isEmpty());
    }
}