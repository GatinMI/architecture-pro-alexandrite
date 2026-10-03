package ru.alexandrite.servicea;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.stereotype.Component;

import java.time.Duration;


@Component
public class OtelConfig {

    public static final String SERVICE_NAME = "service-a";
    public static final String SERVICE_VERSION = "1.0.0";

    private static final String OTEL_ENDPOINT =
            System.getenv().getOrDefault("OTEL_EXPORTER_OTLP_ENDPOINT",
                    "http://simplest-collector.observability.svc:4318");

    private final Tracer tracer;
    private final TextMapPropagator propagator;

    public OtelConfig() {
        Resource resource = Resource.getDefault().merge(Resource.create(Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), SERVICE_NAME)
                .put(AttributeKey.stringKey("service.version"), SERVICE_VERSION)
                .put(AttributeKey.stringKey("deployment.environment"), "dev")
                .build()));

        W3CTraceContextPropagator w3c = W3CTraceContextPropagator.getInstance();
        ContextPropagators propagators = ContextPropagators.create(w3c);

        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder()
                .setEndpoint(OTEL_ENDPOINT + "/v1/traces")
                .setTimeout(Duration.ofSeconds(10))
                .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();

        OpenTelemetrySdk sdk = OpenTelemetrySdk.builder()
                .setPropagators(propagators)
                .setTracerProvider(tracerProvider)
                .build();

        this.tracer = sdk.getTracer(SERVICE_NAME, SERVICE_VERSION);
        this.propagator = propagators.getTextMapPropagator();
    }

    public Tracer tracer() {
        return tracer;
    }

    public TextMapPropagator propagator() {
        return propagator;
    }
}
