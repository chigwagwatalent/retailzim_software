package com.retailzw.controller.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.NoSuchElementException;

@ControllerAdvice(assignableTypes = {
        PlatformAdminController.class,
        ShopWebController.class,
        SmilePayCheckoutController.class
})
public class WebFormExceptionHandler {

    @ExceptionHandler({
            IllegalArgumentException.class,
            IllegalStateException.class,
            NoSuchElementException.class,
            DataIntegrityViolationException.class,
            BindException.class,
            MethodArgumentNotValidException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ModelAndView handleFormException(Exception exception, HttpServletRequest request) {
        RequestContextUtils.getOutputFlashMap(request)
                .put("message", cleanMessage(exception));
        return new ModelAndView("redirect:" + safeReturnPath(request));
    }

    private String cleanMessage(Exception exception) {
        if (exception instanceof MissingServletRequestParameterException missing) {
            return label(missing.getParameterName()) + " is required.";
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            return label(mismatch.getName()) + " has an invalid value.";
        }
        if (exception instanceof BindException || exception instanceof MethodArgumentNotValidException
                || exception instanceof ConstraintViolationException) {
            return "Please check the highlighted form fields and try again.";
        }
        if (exception instanceof DataIntegrityViolationException dataError) {
            String message = rootMessage(dataError).toLowerCase();
            if (message.contains("cannot be null") || message.contains("not-null")) {
                return "Please complete all required fields before saving.";
            }
            if (message.contains("duplicate") || message.contains("unique")) {
                return "A record with those details already exists. Check the unique fields and try again.";
            }
            return "We could not save this form. Please check the values and try again.";
        }
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "Something needs attention. Please check the form and try again."
                : message;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor.getMessage() == null ? "" : cursor.getMessage();
    }

    private String safeReturnPath(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URI uri = new URI(referer);
                String path = uri.getRawPath();
                if (path != null && path.startsWith("/")) {
                    String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
                    String fragment = uri.getRawFragment() == null ? "" : "#" + uri.getRawFragment();
                    return path + query + fragment;
                }
            } catch (URISyntaxException ignored) {
                // Fall back to the current request path below.
            }
        }
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/admin")) {
            return "/admin/dashboard";
        }
        if (uri != null && uri.startsWith("/shop")) {
            return "/shop/dashboard";
        }
        return "/";
    }

    private String label(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            return "This field";
        }
        String spaced = rawName.replaceAll("([a-z])([A-Z])", "$1 $2")
                .replace('_', ' ')
                .replace('-', ' ')
                .trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
