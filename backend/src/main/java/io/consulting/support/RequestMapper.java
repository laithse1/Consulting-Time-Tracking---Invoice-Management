package io.consulting.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Bridges typed HTTP requests to the JDBC projection model without inventing defaults. */
@Component
public class RequestMapper {

    private final ObjectMapper objectMapper;

    public RequestMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> toCommand(Object request) {
        Map<String, Object> command = objectMapper.convertValue(
            request,
            new TypeReference<Map<String, Object>>() {}
        );
        command.values().removeIf(java.util.Objects::isNull);
        return command;
    }
}
