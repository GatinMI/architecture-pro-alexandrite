package ru.alexandrite.servicea;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
public class OrderController {

    private static final String SERVICE_B_URL =
            System.getenv().getOrDefault("SERVICE_B_URL", "http://service-b:8080/price");

    private final OtelConfig otel;
    private final Tracer tracer;
    private final HttpClient http;

    public OrderController(OtelConfig otel) {
        this.otel = otel;
        this.tracer = otel.tracer();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @GetMapping("/order")
    public Map<String, Object> createOrder(@RequestParam(name = "order-id", defaultValue = "ORD-1000") String orderId,
                                           @RequestParam(name = "customer", defaultValue = "customer-42") String customer) {

        Span serverSpan = tracer.spanBuilder("GET /order")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", "GET")
                .setAttribute("http.route", "/order")
                .setAttribute("order.id", orderId)
                .setAttribute("customer.id", customer)
                .startSpan();

        Map<String, Object> response = new HashMap<>();
        try (Scope scope = serverSpan.makeCurrent()) {
            serverSpan.addEvent("order accepted", io.opentelemetry.api.common.Attributes.builder()
                    .put("order.id", orderId).build());
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(SERVICE_B_URL + "?order-id=" + orderId))
                    .timeout(Duration.ofSeconds(5))
                    .GET();

            Span clientSpan = tracer.spanBuilder("GET http://service-b:8080/price")
                    .setSpanKind(SpanKind.CLIENT)
                    .setAttribute("http.method", "GET")
                    .setAttribute("http.url", SERVICE_B_URL)
                    .startSpan();

            try (Scope clientScope = clientSpan.makeCurrent()) {
                Map<String, List<String>> headers = new HashMap<>();
                otel.propagator().inject(Context.current(), headers,
                        (carrier, key, value) -> carrier.computeIfAbsent(key, k -> new ArrayList<>()).add(value));
                headers.forEach((k, values) -> values.forEach(v -> requestBuilder.header(k, v)));

                HttpResponse<String> resp = http.send(requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());

                clientSpan.setAttribute("http.status_code", resp.statusCode());
                if (resp.statusCode() >= 500) {
                    clientSpan.setStatus(StatusCode.ERROR, "service-b http " + resp.statusCode());
                }

                response.put("order-id", orderId);
                response.put("customer", customer);
                response.put("status", resp.statusCode() == 200 ? "created" : "price-calculation-failed");
                response.put("price-calc", resp.body());
            } catch (Exception e) {
                clientSpan.setStatus(StatusCode.ERROR, e.getMessage());
                clientSpan.recordException(e);
                response.put("order-id", orderId);
                response.put("status", "price-calculation-failed");
                response.put("error", String.valueOf(e.getMessage()));
            } finally {
                clientSpan.end();
            }
        } catch (Exception e) {
            serverSpan.setStatus(StatusCode.ERROR, e.getMessage());
            serverSpan.recordException(e);
            response.put("status", "error");
            response.put("error", String.valueOf(e.getMessage()));
        } finally {
            serverSpan.end();
        }
        return response;
    }
}
