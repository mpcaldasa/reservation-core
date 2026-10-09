variable "aws_region" {
  type    = string
  default = "us-east-1"
}

variable "environment" {
  type = string
  validation {
    condition     = contains(["dev", "staging", "prod"], var.environment)
    error_message = "Environment must be dev, staging, or prod."
  }
}

variable "certificate_arn" {
  type        = string
  description = "ACM certificate ARN for the API hostname."
}

variable "jwt_secret_arn" {
  type        = string
  description = "Secrets Manager ARN containing the JWT signing secret as a plain string."
}

variable "image_tag" {
  type        = string
  description = "Immutable container image tag to deploy."
}

variable "db_instance_class" {
  type    = string
  default = "db.t4g.micro"
}

variable "desired_count" {
  type    = number
  default = 1
}

variable "observability_backend" {
  type        = string
  default     = "aws"
  description = "Telemetry destination: aws (ADOT, CloudWatch, and X-Ray), datadog (OTLP intake), or none."
  validation {
    condition     = contains(["aws", "datadog", "none"], var.observability_backend)
    error_message = "observability_backend must be aws, datadog, or none."
  }
}

variable "datadog_otlp_endpoint" {
  type        = string
  default     = "https://otlp.datadoghq.com"
  description = "Datadog OTLP intake base URL for the selected Datadog site."
  validation {
    condition     = can(regex("^https://", var.datadog_otlp_endpoint))
    error_message = "datadog_otlp_endpoint must use HTTPS."
  }
}

variable "datadog_api_key_secret_arn" {
  type        = string
  default     = ""
  description = "Secrets Manager ARN containing a Datadog API key. Required for the Datadog backend."
}

variable "adot_collector_image" {
  type        = string
  default     = "public.ecr.aws/aws-observability/aws-otel-collector:v0.49.0"
  description = "Version-pinned AWS Distro for OpenTelemetry Collector image."
}

variable "trace_sampling_probability" {
  type        = number
  default     = 0.1
  description = "Fraction of root requests to sample, from 0.0 to 1.0."
  validation {
    condition     = var.trace_sampling_probability >= 0 && var.trace_sampling_probability <= 1
    error_message = "trace_sampling_probability must be between 0.0 and 1.0."
  }
}

variable "operations_alert_email" {
  type        = string
  default     = ""
  description = "Optional email subscription for infrastructure alarms. The recipient must confirm the SNS subscription."
  validation {
    condition     = var.operations_alert_email == "" || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.operations_alert_email))
    error_message = "operations_alert_email must be a valid email address or empty."
  }
}

variable "ecs_cpu_alarm_threshold" {
  type        = number
  default     = 80
  description = "Average ECS service CPU percentage that triggers a sustained utilization alarm."
  validation {
    condition     = var.ecs_cpu_alarm_threshold > 0 && var.ecs_cpu_alarm_threshold <= 100
    error_message = "ecs_cpu_alarm_threshold must be greater than 0 and no greater than 100."
  }
}

variable "ecs_memory_alarm_threshold" {
  type        = number
  default     = 80
  description = "Average ECS service memory percentage that triggers a sustained utilization alarm."
  validation {
    condition     = var.ecs_memory_alarm_threshold > 0 && var.ecs_memory_alarm_threshold <= 100
    error_message = "ecs_memory_alarm_threshold must be greater than 0 and no greater than 100."
  }
}

variable "smtp_enabled" {
  type    = bool
  default = false
}

variable "invitation_acceptance_url" {
  type        = string
  default     = ""
  description = "HTTPS frontend URL that receives the invitation token and completes acceptance. Required when SMTP is enabled."
  validation {
    condition     = var.invitation_acceptance_url == "" || can(regex("^https://", var.invitation_acceptance_url))
    error_message = "invitation_acceptance_url must use HTTPS."
  }
}

variable "password_reset_acceptance_url" {
  type        = string
  default     = ""
  description = "HTTPS frontend URL that receives the password reset token and completes the reset. Required when SMTP is enabled."
  validation {
    condition     = var.password_reset_acceptance_url == "" || can(regex("^https://", var.password_reset_acceptance_url))
    error_message = "password_reset_acceptance_url must use HTTPS."
  }
}

variable "smtp_host" {
  type    = string
  default = ""
}

variable "smtp_port" {
  type    = number
  default = 587
  validation {
    condition     = var.smtp_port >= 1 && var.smtp_port <= 65535 && floor(var.smtp_port) == var.smtp_port
    error_message = "SMTP port must be an integer between 1 and 65535."
  }
}

variable "smtp_username" {
  type    = string
  default = ""
}

variable "smtp_from" {
  type    = string
  default = ""
}

variable "smtp_password_secret_arn" {
  type        = string
  default     = ""
  description = "Optional Secrets Manager ARN containing the SMTP password as a plain string."
}

variable "platform_admin_bootstrap_enabled" {
  type    = bool
  default = false
}

variable "platform_admin_bootstrap_email" {
  type    = string
  default = ""
}

variable "platform_admin_bootstrap_full_name" {
  type    = string
  default = "Platform Administrator"
}

variable "platform_admin_bootstrap_password_secret_arn" {
  type        = string
  default     = ""
  description = "Secrets Manager ARN containing the initial platform administrator password as a plain string."
}
