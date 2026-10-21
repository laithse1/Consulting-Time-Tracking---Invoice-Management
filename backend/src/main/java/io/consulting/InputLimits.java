package io.consulting;

import java.lang.reflect.Type;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

/** Reject structured values and oversized text before generic command payloads reach the domain. */
@ControllerAdvice
public class InputLimits extends RequestBodyAdviceAdapter {

  @Override
  public boolean supports(
    MethodParameter p,
    Type type,
    Class<? extends HttpMessageConverter<?>> converter
  ) {
    return Map.class.isAssignableFrom(p.getParameterType());
  }

  @Override
  public Object afterBodyRead(
    Object body,
    HttpInputMessage input,
    MethodParameter p,
    Type type,
    Class<? extends HttpMessageConverter<?>> converter
  ) {
    var values = (Map<?, ?>) body;
    Ledger.require(values.size() <= 40, "Too many input fields");
    for (var item : values.entrySet()) {
      Object value = item.getValue();
      Ledger.require(
        value == null ||
          value instanceof String ||
          value instanceof Number ||
          value instanceof Boolean,
        "Input fields must be scalar values"
      );
      Ledger.require(
        value == null || value.toString().length() <= 10000,
        "Input field exceeds 10,000 characters"
      );
    }
    return body;
  }
}
