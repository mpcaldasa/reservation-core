resource "aws_ecr_repository" "app" {
  name                 = "${local.prefix}-reservation-core"
  image_tag_mutability = "IMMUTABLE"
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_cloudwatch_log_group" "app" {
  name              = "/ecs/${local.prefix}-reservation-core"
  retention_in_days = 30
}

data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${local.prefix}-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "secrets" {
  statement {
    actions = ["secretsmanager:GetSecretValue"]
    resources = concat([
      var.jwt_secret_arn,
      aws_db_instance.main.master_user_secret[0].secret_arn
      ], var.smtp_enabled && var.smtp_password_secret_arn != "" ? [var.smtp_password_secret_arn] : [],
      var.observability_backend == "datadog" && var.datadog_api_key_secret_arn != "" ? [var.datadog_api_key_secret_arn] : [],
    var.platform_admin_bootstrap_enabled ? [var.platform_admin_bootstrap_password_secret_arn] : [])
  }
}

resource "aws_iam_role_policy" "secrets" {
  name   = "${local.prefix}-secrets"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${local.prefix}-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

data "aws_iam_policy_document" "telemetry" {
  count = var.observability_backend == "aws" ? 1 : 0

  statement {
    actions = [
      "xray:GetSamplingRules",
      "xray:GetSamplingTargets",
      "xray:GetSamplingStatisticSummaries",
      "xray:PutTelemetryRecords",
      "xray:PutTraceSegments"
    ]
    resources = ["*"]
  }

  statement {
    actions   = ["cloudwatch:PutMetricData"]
    resources = ["*"]
    condition {
      test     = "StringEquals"
      variable = "cloudwatch:namespace"
      values   = ["ECS/AWSOTel/Application"]
    }
  }
}

resource "aws_iam_role_policy" "telemetry" {
  count  = var.observability_backend == "aws" ? 1 : 0
  name   = "${local.prefix}-telemetry"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.telemetry[0].json
}

resource "aws_ecs_cluster" "main" {
  name = "${local.prefix}-cluster"
}

resource "aws_ecs_task_definition" "app" {
  lifecycle {
    precondition {
      condition     = var.observability_backend != "datadog" || trimspace(var.datadog_api_key_secret_arn) != ""
      error_message = "datadog_api_key_secret_arn is required when observability_backend is datadog."
    }
    precondition {
      condition     = !var.smtp_enabled || (trimspace(var.smtp_host) != "" && trimspace(var.smtp_from) != "")
      error_message = "smtp_host and smtp_from are required when smtp_enabled is true."
    }
    precondition {
      condition     = !var.smtp_enabled || trimspace(var.invitation_acceptance_url) != ""
      error_message = "invitation_acceptance_url is required when smtp_enabled is true."
    }
    precondition {
      condition     = !var.smtp_enabled || trimspace(var.password_reset_acceptance_url) != ""
      error_message = "password_reset_acceptance_url is required when smtp_enabled is true."
    }
    precondition {
      condition     = !var.smtp_enabled || var.smtp_username == "" || var.smtp_password_secret_arn != ""
      error_message = "Authenticated SMTP requires smtp_password_secret_arn."
    }
    precondition {
      condition = !var.platform_admin_bootstrap_enabled || (
        trimspace(var.platform_admin_bootstrap_email) != "" &&
        trimspace(var.platform_admin_bootstrap_password_secret_arn) != ""
      )
      error_message = "Platform administrator bootstrap requires an email and a password secret ARN."
    }
  }
  family                   = "${local.prefix}-reservation-core"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.observability_backend == "aws" ? 1024 : 512
  memory                   = var.observability_backend == "aws" ? 2048 : 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn
  container_definitions = jsonencode(concat([{
    name      = "reservation-core"
    image     = "${aws_ecr_repository.app.repository_url}:${var.image_tag}"
    essential = true
    cpu       = var.observability_backend == "aws" ? 768 : 512
    memory    = var.observability_backend == "aws" ? 1792 : 1024
    portMappings = [{
      containerPort = 8080
      hostPort      = 8080
      protocol      = "tcp"
    }]
    environment = concat([
      { name = "SPRING_PROFILES_ACTIVE", value = var.observability_backend == "datadog" ? "aws,datadog" : "aws" },
      { name = "APP_ENVIRONMENT", value = var.environment },
      { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${aws_db_instance.main.endpoint}/slotix" },
      { name = "SPRING_DATASOURCE_USERNAME", value = "slotix_admin" }
      ], var.observability_backend != "none" ? [
      { name = "OTEL_SERVICE_NAME", value = "slotix-reservation-core" },
      { name = "OTEL_RESOURCE_ATTRIBUTES", value = "deployment.environment=${var.environment},service.version=${var.image_tag}" },
      { name = "OTEL_EXPORTER_OTLP_ENDPOINT", value = var.observability_backend == "aws" ? "http://127.0.0.1:4318" : var.datadog_otlp_endpoint },
      { name = "OTEL_EXPORTER_OTLP_PROTOCOL", value = "http/protobuf" },
      { name = "OTEL_TRACES_EXPORTER_ENABLED", value = "true" },
      { name = "OTEL_METRICS_EXPORTER_ENABLED", value = "true" },
      { name = "APP_TRACING_SAMPLING_PROBABILITY", value = tostring(var.trace_sampling_probability) }
      ] : [], var.smtp_enabled ? [
      { name = "APP_NOTIFICATION_EMAIL_ENABLED", value = "true" },
      { name = "APP_NOTIFICATION_EMAIL_FROM", value = var.smtp_from },
      { name = "APP_IDENTITY_INVITATION_ACCEPTANCE_URL", value = var.invitation_acceptance_url },
      { name = "APP_IDENTITY_PASSWORD_RESET_ACCEPTANCE_URL", value = var.password_reset_acceptance_url },
      { name = "SPRING_MAIL_HOST", value = var.smtp_host },
      { name = "SPRING_MAIL_PORT", value = tostring(var.smtp_port) },
      { name = "SPRING_MAIL_USERNAME", value = var.smtp_username },
      { name = "SMTP_AUTH", value = tostring(var.smtp_username != "") }
      ] : [], var.platform_admin_bootstrap_enabled ? [
      { name = "APP_BOOTSTRAP_PLATFORM_ADMIN_ENABLED", value = "true" },
      { name = "APP_BOOTSTRAP_PLATFORM_ADMIN_EMAIL", value = var.platform_admin_bootstrap_email },
      { name = "APP_BOOTSTRAP_PLATFORM_ADMIN_FULL_NAME", value = var.platform_admin_bootstrap_full_name }
    ] : [])
    secrets = concat([
      { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${aws_db_instance.main.master_user_secret[0].secret_arn}:password::" },
      { name = "APP_JWT_SECRET", valueFrom = var.jwt_secret_arn }
      ], var.observability_backend == "datadog" ? [
      { name = "DATADOG_API_KEY", valueFrom = var.datadog_api_key_secret_arn }
      ] : [], var.smtp_enabled && var.smtp_password_secret_arn != "" ? [
      { name = "SPRING_MAIL_PASSWORD", valueFrom = var.smtp_password_secret_arn }
      ] : [], var.platform_admin_bootstrap_enabled ? [
      { name = "APP_BOOTSTRAP_PLATFORM_ADMIN_PASSWORD", valueFrom = var.platform_admin_bootstrap_password_secret_arn }
    ] : [])
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "app"
      }
    }
    dependsOn = var.observability_backend == "aws" ? [{ containerName = "aws-otel-collector", condition = "START" }] : []
    }], var.observability_backend == "aws" ? [{
    name      = "aws-otel-collector"
    image     = var.adot_collector_image
    essential = false
    cpu       = 256
    memory    = 256
    command   = ["--config=/etc/ecs/ecs-default-config.yaml"]
    environment = [
      { name = "AWS_REGION", value = var.aws_region },
      { name = "ECS_CLUSTER_NAME", value = aws_ecs_cluster.main.name }
    ]
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.app.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "otel"
      }
    }
  }] : []))
  depends_on = [aws_iam_role_policy_attachment.execution, aws_iam_role_policy.secrets, aws_iam_role_policy.telemetry]
}

resource "aws_lb" "app" {
  name                       = "${local.prefix}-api"
  load_balancer_type         = "application"
  subnets                    = aws_subnet.public[*].id
  security_groups            = [aws_security_group.alb.id]
  enable_deletion_protection = var.environment == "prod"
}

resource "aws_lb_target_group" "app" {
  name        = "${local.prefix}-api"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = aws_vpc.main.id
  health_check {
    path                = "/actuator/health/readiness"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.app.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = var.certificate_arn
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.app.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type = "redirect"
    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_ecs_service" "app" {
  name            = "${local.prefix}-reservation-core"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.app.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"
  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }
  load_balancer {
    target_group_arn = aws_lb_target_group.app.arn
    container_name   = "reservation-core"
    container_port   = 8080
  }
  depends_on = [aws_lb_listener.https]
}

resource "aws_sns_topic" "operations_alerts" {
  count = var.operations_alert_email == "" ? 0 : 1
  name  = "${local.prefix}-operations-alerts"
}

resource "aws_sns_topic_subscription" "operations_alert_email" {
  count     = var.operations_alert_email == "" ? 0 : 1
  topic_arn = aws_sns_topic.operations_alerts[0].arn
  protocol  = "email"
  endpoint  = var.operations_alert_email
}

resource "aws_cloudwatch_metric_alarm" "ecs_cpu_high" {
  alarm_name          = "${local.prefix}-ecs-cpu-high"
  alarm_description   = "ECS service CPU utilization is persistently above the configured threshold."
  namespace           = "AWS/ECS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = var.ecs_cpu_alarm_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.app.name
  }
  alarm_actions = var.operations_alert_email == "" ? [] : [aws_sns_topic.operations_alerts[0].arn]
  ok_actions    = var.operations_alert_email == "" ? [] : [aws_sns_topic.operations_alerts[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "ecs_memory_high" {
  alarm_name          = "${local.prefix}-ecs-memory-high"
  alarm_description   = "ECS service memory utilization is persistently above the configured threshold."
  namespace           = "AWS/ECS"
  metric_name         = "MemoryUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  datapoints_to_alarm = 2
  threshold           = var.ecs_memory_alarm_threshold
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ClusterName = aws_ecs_cluster.main.name
    ServiceName = aws_ecs_service.app.name
  }
  alarm_actions = var.operations_alert_email == "" ? [] : [aws_sns_topic.operations_alerts[0].arn]
  ok_actions    = var.operations_alert_email == "" ? [] : [aws_sns_topic.operations_alerts[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "alb_target_errors" {
  alarm_name          = "${local.prefix}-alb-target-5xx"
  alarm_description   = "The application load balancer observed at least five target 5xx responses in five minutes."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "HTTPCode_Target_5XX_Count"
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 5
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
    TargetGroup  = aws_lb_target_group.app.arn_suffix
  }
  alarm_actions = var.operations_alert_email == "" ? [] : [aws_sns_topic.operations_alerts[0].arn]
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  alarm_name          = "${local.prefix}-alb-unhealthy-targets"
  alarm_description   = "At least one ECS target has failed the application readiness health check."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.app.arn_suffix
    TargetGroup  = aws_lb_target_group.app.arn_suffix
  }
  alarm_actions = var.operations_alert_email == "" ? [] : [aws_sns_topic.operations_alerts[0].arn]
}

resource "aws_cloudwatch_log_metric_filter" "notification_exhausted" {
  name           = "${local.prefix}-notification-delivery-exhausted"
  log_group_name = aws_cloudwatch_log_group.app.name
  pattern        = "{ $.notificationExhausted = true }"

  metric_transformation {
    name          = "NotificationDeliveryExhausted"
    namespace     = "${local.prefix}/ReservationCore"
    value         = "1"
    default_value = "0"
    unit          = "Count"
  }
}

resource "aws_cloudwatch_metric_alarm" "notification_exhausted" {
  alarm_name          = "${local.prefix}-notification-delivery-exhausted"
  alarm_description   = "At least one booking or identity email exhausted its configured retry attempts."
  namespace           = aws_cloudwatch_log_metric_filter.notification_exhausted.metric_transformation[0].namespace
  metric_name         = aws_cloudwatch_log_metric_filter.notification_exhausted.metric_transformation[0].name
  statistic           = "Sum"
  period              = 300
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = var.operations_alert_email == "" ? [] : [aws_sns_topic.operations_alerts[0].arn]
}

resource "aws_cloudwatch_dashboard" "operations" {
  dashboard_name = "${local.prefix}-operations"
  dashboard_body = jsonencode({
    widgets = [
      {
        type       = "text", x = 0, y = 0, width = 24, height = 2,
        properties = { markdown = "# Slotix operations\nECS, load balancer, and notification delivery health for `${var.environment}`." }
      },
      {
        type = "metric", x = 0, y = 2, width = 12, height = 6,
        properties = {
          title = "ECS service utilization", view = "timeSeries", region = var.aws_region, period = 300,
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", aws_ecs_cluster.main.name, "ServiceName", aws_ecs_service.app.name],
            [".", "MemoryUtilization", ".", ".", ".", "."]
          ]
        }
      },
      {
        type = "metric", x = 12, y = 2, width = 12, height = 6,
        properties = {
          title = "Load balancer health", view = "timeSeries", region = var.aws_region, period = 300,
          metrics = [
            ["AWS/ApplicationELB", "HTTPCode_Target_5XX_Count", "LoadBalancer", aws_lb.app.arn_suffix, "TargetGroup", aws_lb_target_group.app.arn_suffix, { stat = "Sum" }],
            [".", "UnHealthyHostCount", ".", ".", ".", ".", { stat = "Maximum", yAxis = "right" }]
          ]
        }
      },
      {
        type = "metric", x = 0, y = 8, width = 12, height = 6,
        properties = {
          title = "Exhausted notification deliveries", view = "timeSeries", region = var.aws_region, period = 300,
          metrics = [[
            aws_cloudwatch_log_metric_filter.notification_exhausted.metric_transformation[0].namespace,
            aws_cloudwatch_log_metric_filter.notification_exhausted.metric_transformation[0].name,
            { stat = "Sum" }
          ]]
        }
      }
    ]
  })
}
