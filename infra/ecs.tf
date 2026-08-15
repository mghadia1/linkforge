resource "aws_ecr_repository" "api" {
  name                 = "${local.name}-api"
  image_tag_mutability = "MUTABLE"

  image_scanning_configuration {
    scan_on_push = true
  }

  force_delete = true
}

# Untagged layers accumulate on every deploy and are billed per GB. Nothing prunes them by hand.
resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Keep the 10 most recent images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_cloudwatch_log_group" "api" {
  name = "/ecs/${local.name}-api"
  # Logs are billed for as long as they are kept, and nobody reads a two-month-old container log.
  retention_in_days = 14
}

resource "aws_ecs_cluster" "main" {
  name = local.name
}

# The role ECS itself uses: pull the image, write logs, read the secrets it injects.
resource "aws_iam_role" "task_execution" {
  name = "${local.name}-task-execution"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy_attachment" "task_execution" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

# Scoped to these two secrets by ARN rather than "*": the execution role should be able to read
# exactly the secrets this task needs and nothing else in the account.
resource "aws_iam_role_policy" "task_execution_secrets" {
  name = "read-linkforge-secrets"
  role = aws_iam_role.task_execution.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = ["secretsmanager:GetSecretValue"]
      Resource = [aws_secretsmanager_secret.db_password.arn, aws_secretsmanager_secret.jwt.arn]
    }]
  })
}

# The role the application runs as. It is empty because the application calls no AWS APIs — it
# talks to Postgres and Redis and nothing else. An empty role is the correct amount of permission.
resource "aws_iam_role" "task" {
  name = "${local.name}-task"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.api_cpu
  memory                   = var.api_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    # x86_64 so CI can build the image natively on a standard runner. Graviton (ARM64) is around
    # 20% cheaper per vCPU-hour and both base images support it, but it needs either an arm64
    # runner or QEMU cross-compilation of a Maven build — more to go wrong than 20% of a $9/month
    # line item is worth on a first deployment. To switch, change this to ARM64 *and* the
    # platform in the deploy workflow's build step together; changing one alone means every task
    # dies on start with an exec-format error.
    cpu_architecture = "X86_64"
  }

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]

    environment = [
      { name = "DB_URL", value = "jdbc:postgresql://${aws_db_instance.main.endpoint}/linkforge" },
      { name = "DB_USER", value = "linkforge" },
      { name = "REDIS_HOST", value = aws_elasticache_cluster.main.cache_nodes[0].address },
      { name = "REDIS_PORT", value = "6379" },
      # The short URL the API hands back must be the address a visitor can actually reach, which
      # is CloudFront's, not the load balancer's.
      { name = "LINKFORGE_BASE_URL", value = "https://${aws_cloudfront_distribution.main.domain_name}" },
      # One task with 0.25 vCPU cannot serve much; the analytics queue is sized for it.
      { name = "JAVA_TOOL_OPTIONS", value = "-XX:MaxRAMPercentage=70" },
    ]

    secrets = [
      { name = "DB_PASSWORD", valueFrom = aws_secretsmanager_secret.db_password.arn },
      { name = "LINKFORGE_JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt.arn },
    ]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "api"
      }
    }

    # ECS restarts a container that fails this. It is deliberately more patient than the load
    # balancer's check: a JVM that is still doing Flyway migrations is starting, not broken.
    healthCheck = {
      command     = ["CMD-SHELL", "wget -qO- http://localhost:8080/actuator/health | grep -q UP || exit 1"]
      interval    = 30
      timeout     = 5
      retries     = 3
      startPeriod = 90
    }
  }])
}

resource "aws_lb" "main" {
  name               = local.name
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id
}

resource "aws_lb_target_group" "api" {
  name        = "${local.name}-api"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  # Give in-flight redirects a moment to finish before a task is killed during a deploy.
  deregistration_delay = 30
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # HTTP only. TLS terminates at CloudFront, which supplies a certificate for its own domain
  # without needing one issued for the load balancer. The hop from CloudFront to the ALB is
  # unencrypted, which is acceptable only because there is no custom domain and no real user data
  # here; with either, this needs ACM plus an HTTPS listener.
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

resource "aws_ecs_service" "api" {
  name            = "${local.name}-api"
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.desired_count
  launch_type     = "FARGATE"

  network_configuration {
    subnets = aws_subnet.public[*].id
    # Required without a NAT gateway: the task needs a route out to pull its image. Inbound is
    # still closed to everything but the load balancer's security group.
    assign_public_ip = true
    security_groups  = [aws_security_group.api.id]
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  # A JVM plus Flyway migrations is not ready in the default 60 seconds, and without this the
  # load balancer kills the task mid-startup and ECS restarts it forever.
  health_check_grace_period_seconds = 120

  depends_on = [aws_lb_listener.http]

  lifecycle {
    # CI deploys by registering a new task definition revision, so Terraform must not treat the
    # running revision as drift and roll it back on the next apply.
    ignore_changes = [task_definition]
  }
}
