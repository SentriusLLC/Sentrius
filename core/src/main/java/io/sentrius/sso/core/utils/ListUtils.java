package io.sentrius.sso.core.utils;

import java.util.List;

public class ListUtils {
    public static <T> List<T> getLastNElements(List<T> list, int n) {
        int size = list.size();
        return list.subList(Math.max(size - n, 0), size);
    }
}