package io.dataguardians.sso.core.utils;

import java.util.ArrayList;
import java.util.List;

public class StringUtils {
  private static final int MAX_STRING_LENGTH = 100;

  public static String truncateString(
      String originalString, String truncateBegin, String truncateEnd, int limit) {
    String leftTruncated = truncateLeft(originalString, truncateBegin, limit);

    return truncateRight(leftTruncated, truncateEnd, limit);
  }

  public static String truncateLeft(String original, String truncateBegin, int length) {
    int beginIndex = original.indexOf(truncateBegin);

    if (beginIndex == -1) {
      return original;
    }

    int startIndex = Math.max(0, beginIndex - length);

    if (startIndex > 0) {
      return "..." + original.substring(startIndex);
    } else {
      return original;
    }
  }

  public static String truncateRight(String original, String truncateEnd, int length) {
    int endIndex = original.lastIndexOf(truncateEnd);

    if (endIndex == -1) {
      return original;
    }

    if (endIndex > MAX_STRING_LENGTH) {
      return truncateStringEnd(original, truncateEnd, length, MAX_STRING_LENGTH);
    }

    return truncateStringEnd(original, truncateEnd, length, endIndex);
  }

  private static String truncateStringEnd(
      String original, String truncateEnd, int length, int end) {
    int endIndexPlusTruncate = end + truncateEnd.length() + length;
    int originalLength = original.length();

    if (endIndexPlusTruncate < originalLength) {
      return original.substring(0, end + truncateEnd.length())
          + original.substring(end + truncateEnd.length(), endIndexPlusTruncate)
          + "...";
    } else {
      return original;
    }
  }

  public static List<String> allToLowerCase(List<String> userIds) {
    List<String> formattedIds = new ArrayList<>();

    for (String userId : userIds) {
      formattedIds.add(userId.toLowerCase());
    }

    return formattedIds;
  }
}
