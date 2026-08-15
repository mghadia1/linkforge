variable "region" {
  description = "AWS region to deploy into."
  type        = string
  default     = "us-east-1"
}

variable "environment" {
  description = "Environment name, used in resource names and tags."
  type        = string
  default     = "dev"
}

variable "image_tag" {
  description = "Tag of the API image in ECR to run. CI sets this to the commit SHA."
  type        = string
  default     = "latest"
}

variable "db_instance_class" {
  description = "RDS instance class. db.t4g.micro is the free-tier-eligible size."
  type        = string
  default     = "db.t4g.micro"
}

variable "cache_node_type" {
  description = "ElastiCache node type. cache.t4g.micro is the smallest current-generation node."
  type        = string
  default     = "cache.t4g.micro"
}

variable "api_cpu" {
  description = "Fargate task CPU units (256 = 0.25 vCPU)."
  type        = number
  default     = 256
}

variable "api_memory" {
  description = "Fargate task memory in MiB."
  type        = number
  default     = 512
}

variable "desired_count" {
  description = <<-EOT
    Number of API tasks. One is deliberate: this is a portfolio deployment, and a second task
    doubles the compute bill to demonstrate nothing that cannot be explained from the task
    definition. Raise it to 2 before claiming the deployment is highly available, because with one
    task it is not.
  EOT
  type        = number
  default     = 1
}

variable "monthly_budget_usd" {
  description = <<-EOT
    Monthly spend that triggers an alert. This exists because the most likely bad outcome of this
    whole project is a forgotten stack quietly billing a student for months.
  EOT
  type        = number
  default     = 15
}

variable "budget_alert_email" {
  description = "Address that receives budget alerts. Required — an unmonitored stack is the risk."
  type        = string
}

variable "github_repository" {
  description = "owner/repo allowed to assume the deploy role via OIDC, e.g. mghadia1/linkforge."
  type        = string
}
