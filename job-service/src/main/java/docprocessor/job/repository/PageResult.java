package docprocessor.job.repository;

import java.util.List;

/** One page of items, the page and size it was asked for, and the total number of matches. */
public record PageResult<T>(List<T> content, int page, int size, long totalElements) {
}
