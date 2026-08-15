terraform {
  required_version = ">= 1.6"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.60"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.6"
    }
  }

  # State is local. That is fine for one person deploying by hand and wrong for anything else:
  # local state cannot be locked, so two people applying at once corrupt it, and it holds the
  # database password in plain text. Moving to an S3 backend with DynamoDB locking is the first
  # thing to do if this ever becomes shared.
}

provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "linkforge"
      Environment = var.environment
      ManagedBy   = "opentofu"
    }
  }
}
