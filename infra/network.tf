data "aws_availability_zones" "available" {
  state = "available"
}

locals {
  name = "linkforge-${var.environment}"

  # Two AZs because RDS subnet groups and ALBs both require them. Nothing here is actually
  # multi-AZ: the database is single-AZ and there is one task.
  azs = slice(data.aws_availability_zones.available.names, 0, 2)
}

resource "aws_vpc" "main" {
  cidr_block           = "10.20.0.0/16"
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = local.name }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id
  tags   = { Name = local.name }
}

# Public subnets hold the ALB and the Fargate tasks.
resource "aws_subnet" "public" {
  count = length(local.azs)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index)
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = { Name = "${local.name}-public-${count.index}" }
}

# Private subnets hold the database and the cache. Neither has a route to the internet, so
# neither can be reached from it regardless of any security group mistake.
resource "aws_subnet" "private" {
  count = length(local.azs)

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(aws_vpc.main.cidr_block, 8, count.index + 10)
  availability_zone = local.azs[count.index]

  tags = { Name = "${local.name}-private-${count.index}" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public" }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

/*
 * There is no NAT gateway anywhere in this file, and that is the single largest cost decision
 * here: a NAT gateway is roughly $32/month before a byte moves through it, which would be the
 * biggest line on the bill by a wide margin.
 *
 * Avoiding it means Fargate tasks run in the public subnets with public IPs, because a task needs
 * outbound reachability to pull its image from ECR and ship logs to CloudWatch. A public IP is not
 * the same as being reachable: the task security group below accepts inbound traffic only from the
 * load balancer's security group, so nothing on the internet can open a connection to a task.
 *
 * The stricter alternative is private subnets plus VPC interface endpoints for ECR, S3 and
 * CloudWatch Logs, which costs about $7/month per endpoint per AZ. For a portfolio deployment that
 * is worse value than the arrangement here; for anything holding real user data it would be the
 * right call.
 */

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb"
  description = "Public entry point"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP from anywhere"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-alb" }
}

resource "aws_security_group" "api" {
  name        = "${local.name}-api"
  description = "Fargate tasks"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Only the load balancer may reach the API"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = { Name = "${local.name}-api" }
}

resource "aws_security_group" "data" {
  name        = "${local.name}-data"
  description = "Postgres and Redis"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Postgres from the API only"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.api.id]
  }

  ingress {
    description     = "Redis from the API only"
    from_port       = 6379
    to_port         = 6379
    protocol        = "tcp"
    security_groups = [aws_security_group.api.id]
  }

  # No egress rule: the datastores have no reason to originate connections.
  tags = { Name = "${local.name}-data" }
}
