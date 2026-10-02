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
