package docprocessor.job.repository;

import java.util.List;

/** One page of items plus the total number of matches. */
public record PageResult<T>(List<T> content, long totalElements) {
}
