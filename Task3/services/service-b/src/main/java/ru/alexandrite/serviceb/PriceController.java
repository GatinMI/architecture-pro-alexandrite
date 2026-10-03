package ru.alexandrite.serviceb;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;


@RestController
public class PriceController {

    private final Tracer tracer;

    public PriceController(OtelConfig otel) {
        this.tracer = otel.tracer();
    }

    @GetMapping("/price")
    public Map<String, Object> calculatePrice(
            @RequestParam(name = "order-id", defaultValue = "ORD-1000") String orderId,
            HttpServletRequest request) {

        TextMapGetter<HttpServletRequest> getter = new TextMapGetter<>() {
            @Override
            public String get(HttpServletRequest carrier, String key) {
                return carrier.getHeader(key);
            }

            @Override
            public Iterable<String> keys(HttpServletRequest carrier) {
                return Collections.list(carrier.getHeaderNames());
            }
        };
        Context incoming = W3CTraceContextPropagator.getInstance()
                .extract(Context.root(), request, getter);

        Span span = tracer.spanBuilder("GET /price")
                .setParent(incoming)
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.method", "GET")
                .setAttribute("http.route", "/price")
                .setAttribute("order.id", orderId)
                .startSpan();

        Map<String, Object> response = new HashMap<>();
        try (Scope scope = span.makeCurrent()) {
            span.addEvent("price calculation started", Attributes.builder()
                    .put("order.id", orderId).build());

            long start = System.nanoTime();
            Thread.sleep(ThreadLocalRandom.current().nextLong(50, 400));
            long computeMs = (System.nanoTime() - start) / 1_000_000;

            double price = 15_000 + ThreadLocalRandom.current().nextDouble(0, 40_000);
            int polygons = 10_000 + ThreadLocalRandom.current().nextInt(990_000);

            span.setAttribute("price.amount", price);
            span.setAttribute("model.polygons", (long) polygons);
            span.setAttribute("calculation.duration.ms", computeMs);
            span.addEvent("price calculated", Attributes.builder()
                    .put("price.amount", price)
                    .put("model.polygons", (long) polygons)
                    .build());

            response.put("order-id", orderId);
            response.put("price", Math.round(price * 100.0) / 100.0);
            response.put("currency", "RUB");
            response.put("polygons", polygons);
            response.put("calculation-ms", computeMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            span.setStatus(StatusCode.ERROR, "interrupted");
            response.put("status", "error");
            response.put("error", "interrupted");
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            response.put("status", "error");
            response.put("error", String.valueOf(e.getMessage()));
        } finally {
            span.end();
        }
        return response;
    }
}
