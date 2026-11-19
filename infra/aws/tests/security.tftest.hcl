mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}"
    }
  }
  mock_data "aws_availability_zones" {
    defaults = {
      names = ["us-east-1a", "us-east-1b"]
    }
  }
  mock_resource "aws_db_instance" {
    defaults = {
      master_user_secret = [{ secret_arn = "arn:aws:secretsmanager:us-east-1:123456789012:secret:database" }]
    }
  }
}

variables {
  environment           = "prod"
  certificate_arn       = "arn:aws:acm:us-east-1:123456789012:certificate/12345678-1234-1234-1234-123456789012"
  jwt_secret_arn        = "arn:aws:secretsmanager:us-east-1:123456789012:secret:jwt"
  image_tag             = "security-test"
  observability_backend = "none"
}

run "production_security" {
  command = plan
  assert {
    condition     = aws_wafv2_web_acl.api.scope == "REGIONAL" && length(aws_wafv2_web_acl.api.rule) == 4
    error_message = "Production must define managed WAF and rate-based rules."
  }
  assert {
    condition     = aws_lb_listener.https.routing_http_response_strict_transport_security_header_value == "max-age=31536000; includeSubDomains"
    error_message = "The HTTPS ingress must enforce HSTS."
  }
  assert {
    condition     = aws_appautoscaling_target.api[0].min_capacity == 2 && aws_ecs_service.app.desired_count >= 2
    error_message = "Production must keep at least two tasks available."
  }
  assert {
    condition     = aws_db_instance.main.storage_encrypted && !aws_db_instance.main.publicly_accessible
    error_message = "The database must be encrypted and private."
  }
}
