package com.repomatch.api;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.repomatch.ai.AiServiceException;
import com.repomatch.analysis.AnalysisNotFoundException;
import com.repomatch.analysis.NoRepositoriesException;
import com.repomatch.code.NotIndexedException;
import com.repomatch.code.NothingToIndexException;
import com.repomatch.code.RepoNotFoundException;
import com.repomatch.analysis.UsageLimitException;
import com.repomatch.jobs.JobFeedService;
import com.repomatch.jobs.JobWatchService;
import com.repomatch.jobs.OwnerKeys;
import com.repomatch.tracker.TrackerService;
import com.repomatch.workflow.InvalidSelectionException;
import com.repomatch.workflow.WorkflowNotFoundException;
import com.repomatch.workflow.WorkflowStepException;
import com.repomatch.github.GitHubApiException;
import com.repomatch.github.GitHubRateLimitException;
import com.repomatch.github.GitHubUserNotFoundException;

import jakarta.validation.ConstraintViolationException;

/** Maps domain failures to RFC 7807 problem responses the frontend can show directly. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({GitHubUserNotFoundException.class, AnalysisNotFoundException.class, WorkflowNotFoundException.class})
    ProblemDetail notFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(WorkflowStepException.class)
    ProblemDetail wrongStep(WorkflowStepException e) {
        return problem(HttpStatus.CONFLICT, "Step not available", e.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentUpdate(ObjectOptimisticLockingFailureException e) {
        return problem(HttpStatus.CONFLICT, "Step not available",
                "This workflow was just updated elsewhere (another tab?). Reload to see its current state.");
    }

    @ExceptionHandler(InvalidSelectionException.class)
    ProblemDetail invalidSelection(InvalidSelectionException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid selection", e.getMessage());
    }

    @ExceptionHandler(UsageLimitException.class)
    ResponseEntity<ProblemDetail> usageLimit(UsageLimitException e) {
        ProblemDetail pd = problem(HttpStatus.TOO_MANY_REQUESTS, "Usage limit reached", e.getMessage());
        long seconds = Math.max(1, e.getRetryAfter().toSeconds());
        pd.setProperty("retryAfterSeconds", seconds);
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
                .body(pd);
    }

    @ExceptionHandler({JobFeedService.PostingNotFoundException.class, JobWatchService.WatchNotFoundException.class,
            TrackerService.ApplicationNotFoundException.class})
    ProblemDetail jobThingNotFound(RuntimeException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(OwnerKeys.MissingOwnerKeyException.class)
    ProblemDetail noKey(OwnerKeys.MissingOwnerKeyException e) {
        return problem(HttpStatus.UNAUTHORIZED, "Tracker key needed", e.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(
            org.springframework.web.bind.ServletRequestBindingException ex, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        if (ex instanceof org.springframework.web.bind.MissingRequestHeaderException m
                && JobsController.KEY_HEADER.equals(m.getHeaderName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(problem(HttpStatus.UNAUTHORIZED, "Tracker key needed", new OwnerKeys.MissingOwnerKeyException().getMessage()));
        }
        return super.handleServletRequestBindingException(ex, headers, status, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    @ExceptionHandler(RepoNotFoundException.class)
    ProblemDetail repoNotFound(RepoNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler({NotIndexedException.class, NothingToIndexException.class})
    ProblemDetail notIndexed(RuntimeException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Not indexed", e.getMessage());
    }

    @ExceptionHandler(NoRepositoriesException.class)
    ProblemDetail noRepos(NoRepositoriesException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "No repositories", e.getMessage());
    }

    @ExceptionHandler(GitHubRateLimitException.class)
    ResponseEntity<ProblemDetail> rateLimited(GitHubRateLimitException e) {
        ProblemDetail pd = problem(HttpStatus.TOO_MANY_REQUESTS, "GitHub rate limit reached",
                "GitHub's API rate limit was hit. Try again later.");
        HttpHeaders headers = new HttpHeaders();
        if (e.getResetAt() != null) {
            pd.setProperty("resetAt", e.getResetAt());
            long seconds = Math.max(0, Duration.between(Instant.now(), e.getResetAt()).toSeconds());
            headers.add(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        }
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(pd);
    }

    @ExceptionHandler(GitHubApiException.class)
    ProblemDetail gitHubDown(GitHubApiException e) {
        return problem(HttpStatus.BAD_GATEWAY, "GitHub error", e.getMessage());
    }

    @ExceptionHandler(AiServiceException.class)
    ProblemDetail aiDown(AiServiceException e) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AI service error", e.getMessage());
    }

    /** Lists each invalid field, e.g. {"username": "must be a valid GitHub username"}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Invalid request",
                fieldErrors.entrySet().stream().map(e -> e.getKey() + " " + e.getValue())
                        .collect(Collectors.joining("; ")));
        pd.setProperty("fieldErrors", fieldErrors);
        return ResponseEntity.badRequest().body(pd);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParam(ConstraintViolationException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
