package io.consulting.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Stores immutable invoice documents without losing decimal precision on reload. */
@Component
public class JsonDocuments {

    private final ObjectMapper objectMapper;

    public JsonDocuments(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not serialize stored document", exception);
        }
    }

    public Map<String, Object> decode(String document) {
        try {
            return objectMapper
                .readerFor(new TypeReference<Map<String, Object>>() {})
                .with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .readValue(document);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read stored document", exception);
        }
    }

    public String fingerprint(Map<String, Object> record) {
        try {
            return java.util.HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(
                    encode(new java.util.TreeMap<>(record)).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                    )
                )
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
