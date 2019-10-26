package com.stripe.net;

import com.stripe.exception.ApiConnectionException;
import com.stripe.exception.ApiException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public abstract class HttpClient {
  /** Maximum sleep time between tries to send HTTP requests after network failure. */
  private static final Duration maxNetworkRetriesDelay = Duration.ofSeconds(5);

  /** Minimum sleep time between tries to send HTTP requests after network failure. */
  private static final Duration minNetworkRetriesDelay = Duration.ofMillis(500);

  private final int maxNetworkRetries;

  public HttpClient() {
    this.maxNetworkRetries = 0;
  }

  public HttpClient(int maxNetworkRetries) {
    this.maxNetworkRetries = maxNetworkRetries;
  }

  public abstract StripeResponse request(StripeRequest request)
      throws AuthenticationException, InvalidRequestException, ApiConnectionException, ApiException;

  public StripeResponse requestWithRetries(StripeRequest request)
      throws AuthenticationException, InvalidRequestException, ApiConnectionException,
          ApiException {
    StripeResponse response;
    int retry = 0;

    while (true) {
      response = this.request(request);

      if (!this.shouldRetry(retry, response)) {
        break;
      }

      retry += 1;

      try {
        Thread.sleep(this.sleepTime(retry).toMillis());
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException(e);
      }
    }

    return response;
  }

  private boolean shouldRetry(int numRetries, StripeResponse response) {
    // Do not retry if we are out of retries.
    if (numRetries >= this.maxNetworkRetries) {
      return false;
    }

    // TODO retry on connection error

    // The API may ask us not to retry (eg; if doing so would be a no-op) or advise us to retry
    // (eg; in cases of lock timeouts); we defer to that.
    Map<String, List<String>> headers = response.headers();
    if ((headers != null) && headers.containsKey("Stripe-Should-Retry")) {
      String value = headers.get("Stripe-Should-Retry").get(0);

      switch (value) {
        case "true":
          return true;
        case "false":
          return false;
      }
    }

    // Retry on conflict errors.
    if (response.code() == 409) {
      return true;
    }

    // Retry on 500, 503, and other internal errors.
    //
    // Note that we expect the Stripe-Should-Retry header to be false in most cases when a 500 is
    // returned, since our idempotency framework would typically replay it anyway.
    if (response.code() >= 500) {
      return true;
    }

    return false;
  }

  private Duration sleepTime(int numRetries) {
    // Apply exponential backoff with MinNetworkRetriesDelay on the number of numRetries
    // so far as inputs.
    Duration delay =
        Duration.ofNanos((long) (minNetworkRetriesDelay.toNanos() * Math.pow(2, numRetries - 1)));

    // Do not allow the number to exceed MaxNetworkRetriesDelay
    if (delay.compareTo(maxNetworkRetriesDelay) > 0) {
      delay = maxNetworkRetriesDelay;
    }

    // Apply some jitter by randomizing the value in the range of 75%-100%.
    double jitter = (3.0 + ThreadLocalRandom.current().nextDouble()) / 4.0;
    delay = Duration.ofNanos((long) (delay.toNanos() * jitter));

    // But never sleep less than the base sleep seconds.
    if (delay.compareTo(minNetworkRetriesDelay) < 0) {
      delay = minNetworkRetriesDelay;
    }

    return delay;
  }
}
