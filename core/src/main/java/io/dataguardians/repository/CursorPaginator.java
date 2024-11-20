package io.dataguardians.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CursorPaginator<T> {
    public enum SortDirection {
        ASC, DESC
    }

    private String orderByField;
    private SortDirection orderByDirection = SortDirection.ASC;
    private List<T> itemList = new ArrayList<>();
    private final Map<String, String> filterMap = new HashMap<>();

    public CursorPaginator() {}

    public CursorPaginator(String orderByField) {
        this.orderByField = sanitizeOrderByField(orderByField);
    }

    public String getOrderByField() {
        return orderByField;
    }

    public CursorPaginator<T> setOrderByField(String orderByField) {
        this.orderByField = sanitizeOrderByField(orderByField);
        return this;
    }

    private String sanitizeOrderByField(String field) {
        if (field == null) {
            return null;
        }
        return field.replaceAll("[^0-9a-zA-Z_.,]", "");
    }

    public SortDirection getOrderByDirection() {
        return orderByDirection;
    }

    public CursorPaginator<T> setOrderByDirection(SortDirection orderByDirection) {
        this.orderByDirection = orderByDirection;
        return this;
    }

    public List<T> getItemList() {
        return itemList;
    }

    public CursorPaginator<T> setItemList(List<T> itemList) {
        this.itemList = itemList;
        return this;
    }

    public Map<String, String> getFilterMap() {
        return filterMap;
    }

    public CursorPaginator<T> addFilter(String key, String value) {
        this.filterMap.put(key, value);
        return this;
    }

    public CursorPaginator<T> clearFilters() {
        this.filterMap.clear();
        return this;
    }
}
