package docprocessor.job.controller;

import docprocessor.job.config.JobProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Rejects POST /api/jobs without a Content-Length (411) or above the size limit (413) before the body is read. */
@Component
@RequiredArgsConstructor
public class RequestSizeFilter extends OncePerRequestFilter {

    private final JobProperties properties;
    private final JsonMapper jsonMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !HttpMethod.POST.matches(request.getMethod()) || !"/api/jobs".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long length = request.getContentLengthLong();
        long limit = properties.maxRequestSize().toBytes();
        if (length < 0) {
            reject(response, HttpStatus.LENGTH_REQUIRED, "Content-Length header is required");
        } else if (length > limit) {
            reject(response, HttpStatus.CONTENT_TOO_LARGE, "Request body exceeds " + limit + " bytes");
        } else {
            chain.doFilter(request, response);
        }
    }

    private void reject(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
