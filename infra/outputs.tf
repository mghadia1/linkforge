output "app_url" {
  description = "The public address. This is the URL a short link points at."
  value       = "https://${aws_cloudfront_distribution.main.domain_name}"
}

output "ecr_repository_url" {
  description = "Where CI pushes the API image."
  value       = aws_ecr_repository.api.repository_url
}

output "ecs_cluster" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service" {
  value = aws_ecs_service.api.name
}

output "web_bucket" {
  description = "Bucket the built frontend is synced into."
  value       = aws_s3_bucket.web.id
}

output "cloudfront_distribution_id" {
  description = "Needed to invalidate the cache after a frontend deploy."
  value       = aws_cloudfront_distribution.main.id
}

output "github_deploy_role_arn" {
  description = "Set as the AWS_DEPLOY_ROLE repository variable in GitHub."
  value       = aws_iam_role.github_deploy.arn
}

output "alb_dns_name" {
  description = "Direct load balancer address, for debugging past CloudFront."
  value       = aws_lb.main.dns_name
}

output "db_endpoint" {
  description = "Private; reachable only from inside the VPC."
  value       = aws_db_instance.main.endpoint
}
