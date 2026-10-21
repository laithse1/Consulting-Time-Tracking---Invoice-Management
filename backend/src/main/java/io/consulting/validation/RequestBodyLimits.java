package io.consulting.validation;

import io.consulting.controller.AssistantController;
import io.consulting.domain.DomainValues;
import java.lang.reflect.Type;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Reject structured values and oversized text before generic command payloads reach the domain. */
@ControllerAdvice
public class RequestBodyLimits extends RequestBodyAdviceAdapter {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public RequestBodyLimits(com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(
        MethodParameter p,
        Type type,
        Class<? extends HttpMessageConverter<?>> converter
    ) {
        return Map.class.isAssignableFrom(p.getParameterType()) || p.getParameterType().isRecord();
    }

    @Override
    public Object afterBodyRead(
        Object body,
        HttpInputMessage input,
        MethodParameter p,
        Type type,
        Class<? extends HttpMessageConverter<?>> converter
    ) {
        Map<?, ?> values =
            body instanceof Map<?, ?> map ? map : objectMapper.convertValue(body, Map.class);
        DomainValues.require(values.size() <= 40, "Too many input fields");
        for (var item : values.entrySet()) {
            Object value = item.getValue();
            if (
                p.getContainingClass() == AssistantController.class &&
                "actions".equals(item.getKey())
            ) {
                DomainValues.require(
                    value instanceof java.util.List<?> list && list.size() <= 30,
                    "Use at most 30 actions"
                );
                DomainValues.require(
                    ((java.util.List<?>) value).stream().allMatch(Map.class::isInstance),
                    "Each assistant action must be an object"
                );
                bounded(value, 0);
                continue;
            }
            DomainValues.require(
                value == null ||
                    value instanceof String ||
                    value instanceof Number ||
                    value instanceof Boolean,
                "Input fields must be scalar values"
            );
            DomainValues.require(
                value == null || value.toString().length() <= 10000,
                "Input field exceeds 10,000 characters"
            );
        }
        return body;
    }

    void bounded(Object value, int depth) {
        DomainValues.require(depth <= 5, "Input is too deeply nested");
        if (value instanceof Map<?, ?> map) {
            DomainValues.require(map.size() <= 40, "Too many action fields");
            map.values().forEach(v -> bounded(v, depth + 1));
        } else if (value instanceof java.util.List<?> list) {
            DomainValues.require(list.size() <= 30, "Too many action values");
            list.forEach(v -> bounded(v, depth + 1));
        } else DomainValues.require(
            value == null ||
                ((value instanceof String || value instanceof Number || value instanceof Boolean) &&
                    value.toString().length() <= 10000),
            "Invalid action value"
        );
    }
}
