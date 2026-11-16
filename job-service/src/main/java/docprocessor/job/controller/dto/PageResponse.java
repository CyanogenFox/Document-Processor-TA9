package docprocessor.job.controller.dto;

import docprocessor.job.repository.PageResult;
import java.util.List;
import java.util.function.Function;

/** One page of a listing, with the paging numbers a client needs. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> from(PageResult<S> result, Function<S, T> mapper) {
        int totalPages = (int) ((result.totalElements() + result.size() - 1) / result.size());
        return new PageResponse<>(result.content().stream().map(mapper).toList(), result.page(), result.size(),
                result.totalElements(), totalPages);
    }
}
