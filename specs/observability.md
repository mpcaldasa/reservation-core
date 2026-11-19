# Observability

## Goals

Slotix emits correlated structured logs, request and JDBC traces, framework metrics, and bounded business counters. Telemetry is opt-in in local and test environments. The application exports OTLP over HTTP Protobuf so a collector or vendor can receive telemetry without tying domain code to that vendor.

## Signals

- Console output uses Spring Boot Logstash JSON. Trace and span IDs are added from the logging context when a sampled trace is active.
- Spring Boot and Micrometer provide HTTP, JVM, process, executor, and connection-pool metrics and HTTP server traces.
- Datasource Micrometer records JDBC query spans and metrics. Query values are not captured; SQL literals are sanitized; ResultSet operation spans are disabled to limit noise.
- Scheduled outbox, expiration, idempotency cleanup, and email delivery passes are independent root observations with fixed job names, so background work is traceable even without an HTTP parent.
- Slotix business counters cover booking creation, booking lifecycle transitions, and booking email delivery outcomes, including exhausted retries. Tags are limited to booking status, delivery channel, and result. Tenant, user, booking, and resource IDs are never metric tags.
- Notification failure logs use stable JSON fields for channel, delivery ID, attempt, error code, persistence result, and retry exhaustion. They never include recipient addresses, message bodies, or credentials.
- Actuator exposes only public liveness/readiness and info endpoints over HTTP. Metrics and traces are pushed to the configured OTLP destination; actuator metrics are not exposed publicly.

## Destinations

### Local development

Start the free local Jaeger UI with `docker compose --profile observability up -d jaeger`. Run the app with `OTEL_TRACES_EXPORTER_ENABLED=true`, `OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318`, and `OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf`; open `http://localhost:16686` to inspect traces. Console logs are structured JSON. Local metrics remain available through the in-process Micrometer registry.

### AWS

Terraform defaults `observability_backend` to `aws`. ECS runs the pinned AWS Distro for OpenTelemetry collector as a nonessential sidecar. It receives OTLP on localhost and exports traces to X-Ray and app metrics to CloudWatch. Application logs are JSON in the existing retained CloudWatch log group; collector output uses a separate stream. ECS service CPU and memory metrics remain available from the native ECS namespace.

This path adds a collector sidecar and increases the Fargate task size to 1 vCPU and 2 GiB. ECS service CPU and memory metrics are free. CloudWatch has a free tier for logs, custom metrics, dashboards, and standard alarms; usage above account limits, OTLP application metrics, X-Ray traces, SNS notifications, NAT traffic, and Fargate usage can incur charges. AWS free usage depends on account eligibility and current pricing; review the AWS pricing pages before enabling a production environment.

### Datadog

Set `observability_backend=datadog`, `datadog_otlp_endpoint` to the organization's OTLP intake URL, and `datadog_api_key_secret_arn` to a Secrets Manager secret containing the API key. Traces and metrics use OTLP HTTP Protobuf. The key is injected at task startup and is not stored in Terraform state as a literal. CloudWatch remains the AWS log source; configure the Datadog AWS integration and CloudWatch log forwarding if logs should also appear in Datadog. Datadog plans and ingestion are separate from AWS charges.

Set `observability_backend=none` to disable application export. Structured stdout logs and CloudWatch container logging continue to work.

Terraform creates the `${environment}` CloudWatch operations dashboard with ECS CPU and memory, target 5xx responses, unhealthy load balancer targets, and exhausted notification retries. Alarms cover sustained ECS CPU and memory utilization, target 5xx responses, unhealthy targets, and exhausted notification retries. Set `operations_alert_email` to create an SNS email subscription; the recipient must confirm it from the AWS subscription email before alerts are delivered. Without an email, alarms remain visible in CloudWatch but send no notifications. Set `ecs_cpu_alarm_threshold` and `ecs_memory_alarm_threshold` to adapt utilization thresholds. The notification alarm is driven by a structured log metric filter and fires when at least one delivery reaches its configured retry limit in a five-minute period.

## Sampling and operational limits

`trace_sampling_probability` controls root trace sampling in Terraform and defaults to 0.1. Keep sampling conservative as request volume grows. Parent-based propagation keeps child spans together with the sampled request. Raise sampling temporarily during an incident only after considering ingestion cost and sensitive-data controls.

Telemetry exporters must not contain credentials in ordinary environment values or Terraform source. Avoid high-cardinality IDs in metric tags and avoid adding request bodies, passwords, tokens, or database bind parameters to logs and spans.

## Verification still required

Infrastructure validation verifies the dashboard and alarm configuration but does not create AWS resources or prove runtime ingestion. Before production, deploy to staging, confirm the SNS subscription, trigger a controlled SMTP failure through a test mailbox, and verify the structured log, metric, alarm, dashboard, trace export, and recovery path in the selected destination. A real SMTP provider and AWS account are required for this end-to-end check.
