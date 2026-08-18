package com.leaseguard.exception;

import com.leaseguard.config.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Renders consistent, correlation-ID-bearing error pages for exceptions that are not already
 * handled inline in a controller (see {@code LeaseController} and {@code ImportController} for
 * cases - optimistic-lock conflicts, import validation failures - that redirect back into the
 * flow with a contextual message instead).
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({LeaseNotFoundException.class, NoResourceFoundException.class})
    public ModelAndView handleNotFound(Exception e, HttpServletRequest request) {
        log.info("Not found: {}", e.getMessage());
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("title", "Not Found");
        mav.addObject("message", "The page or lease you requested does not exist.");
        mav.addObject("correlationId", request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME));
        mav.setStatus(HttpStatus.NOT_FOUND);
        return mav;
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleUploadTooLarge(RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("uploadError", "The uploaded file is too large (max 5MB).");
        return "redirect:/import";
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleUnexpected(Exception e, HttpServletRequest request) {
        String correlationId = String.valueOf(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME));
        log.error("Unhandled exception [correlationId={}]", correlationId, e);
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("title", "Something went wrong");
        mav.addObject("message", "An unexpected error occurred. Please try again; if it persists, share the correlation ID below.");
        mav.addObject("correlationId", correlationId);
        mav.setStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        return mav;
    }
}
