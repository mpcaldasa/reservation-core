output "image_repository_url" {
  value = aws_ecr_repository.app.repository_url
}

output "api_load_balancer_dns" {
  value = aws_lb.app.dns_name
}

output "assets_bucket" {
  value = aws_s3_bucket.assets.bucket
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.app.name
}
