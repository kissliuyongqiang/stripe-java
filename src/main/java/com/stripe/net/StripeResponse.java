package com.stripe.net;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class StripeResponse {

  int code;
  String body;
  Map<String, List<String>> headers;

  /** Constructs a Stripe response with the specified status code and body. */
  public StripeResponse(int code, String body) {
    this(code, body, null);
  }

  /** Constructs a Stripe response with the specified status code, body and headers. */
  public StripeResponse(int code, String body, Map<String, List<String>> headers) {
    this.code = code;
    this.body = body;

    if (headers != null) {
      this.headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      this.headers.putAll(headers);
    }
  }

  public int code() {
    return this.code;
  }

  public String body() {
    return this.body;
  }

  public Map<String, List<String>> headers() {
    return headers;
  }

  public String idempotencyKey() {
    return ((headers != null) && headers.containsKey("Idempotency-Key"))
        ? headers.get("Idempotency-Key").get(0)
        : null;
  }

  public String requestId() {
    return ((headers != null) && headers.containsKey("Request-Id"))
        ? headers.get("Request-Id").get(0)
        : null;
  }
}
