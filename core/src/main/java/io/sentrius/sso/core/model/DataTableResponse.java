package io.sentrius.sso.core.model;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class DataTableResponse<T> {
    private List<T> data;
    private String search;
    private String order;
    private long recordsTotal;
    private long recordsFiltered;
}
