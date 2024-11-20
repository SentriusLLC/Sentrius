package io.dataguardians.sso.core.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SizeConverter {

    private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+(\\.\\d+)?)\\s*(B|KB|MB|GB|TB|PB)", Pattern.CASE_INSENSITIVE);

    public static long parseSize(String size) {
        Matcher matcher = SIZE_PATTERN.matcher(size.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid size format: " + size);
        }

        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(3).toUpperCase();

        switch (unit) {
            case "B":
                return (long) value;
            case "KB":
                return (long) (value * 1024);
            case "MB":
                return (long) (value * 1024 * 1024);
            case "GB":
                return (long) (value * 1024 * 1024 * 1024);
            case "TB":
                return (long) (value * 1024 * 1024 * 1024 * 1024);
            case "PB":
                return (long) (value * 1024 * 1024 * 1024 * 1024 * 1024);
            case "EB":
                return (long) (value * 1024 * 1024 * 1024 * 1024 * 1024* 1024);
            default:
                throw new IllegalArgumentException("Unknown unit: " + unit);
        }
    }

    public static void main(String[] args) {
        String sizeString = "2MB";
        long sizeInBytes = parseSize(sizeString);
        System.out.println(sizeString + " = " + sizeInBytes + " bytes");
    }
}

