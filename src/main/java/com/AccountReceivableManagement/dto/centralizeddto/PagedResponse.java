package com.AccountReceivableManagement.dto.centralizeddto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * A single page of results, carried as the {@code data} of an {@link ApiResponse}.
 *
 * Spring Data's own {@code Page}/{@code PageImpl} is deliberately not serialized
 * directly: its JSON shape is not a stable contract (Spring Boot 3 warns about it),
 * so clients get this explicit envelope instead.
 *
 * @param <T> row type
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PagedResponse<T> {

    /** Rows of this page, in the order the query returned them. Never null. */
    private List<T> content;

    /** Zero-based index of this page. */
    private int page;

    /** Requested page size (the last page may hold fewer rows). */
    private int size;

    /** Total rows matching the query across all pages. */
    private long totalElements;

    /** Total number of pages, 0 when nothing matched. */
    private int totalPages;

    /** True when this is the first page. */
    private boolean first;

    /** True when this is the last page. */
    private boolean last;

    /**
     * Wraps {@code source}'s paging metadata around {@code content}, which is the
     * already-mapped DTO form of {@code source.getContent()}.
     */
    public static <T> PagedResponse<T> of(Page<?> source, List<T> content) {
        return PagedResponse.<T>builder()
                .content(content)
                .page(source.getNumber())
                .size(source.getSize())
                .totalElements(source.getTotalElements())
                .totalPages(source.getTotalPages())
                .first(source.isFirst())
                .last(source.isLast())
                .build();
    }

    /** An empty page, for a filter that cannot match any row. */
    public static <T> PagedResponse<T> empty(int page, int size) {
        return PagedResponse.<T>builder()
                .content(List.of())
                .page(page)
                .size(size)
                .totalElements(0L)
                .totalPages(0)
                .first(page == 0)
                .last(true)
                .build();
    }
}
