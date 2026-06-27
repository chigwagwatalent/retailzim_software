package com.retailzw.controller.api;

import com.retailzw.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestControllerAdvice(basePackages = "com.retailzw.controller.api")
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> validation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        log.warn("API validation failed: {}", message);
        return ApiResponse.error(message);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Object> business(Exception ex) {
        log.warn("API request rejected: {}", ex.getMessage());
        return ApiResponse.error(ex.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Object> generic(Exception ex) {
        log.error("Unhandled API error", ex);
        return ApiResponse.error("Unexpected server error. Check backend logs for details.");
    }
}

