resource "aws_db_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "random_password" "db" {
  length = 32
  # RDS rejects '/', '@', '"' and spaces in a master password.
  override_special = "!#$%&*()-_=+[]{}<>:?"
}

resource "aws_db_instance" "main" {
  identifier     = local.name
  engine         = "postgres"
  engine_version = "16.4"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 50
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = "linkforge"
  username = "linkforge"
  password = random_password.db.result

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.data.id]
  publicly_accessible    = false

  # Single-AZ: this is a portfolio deployment, and multi-AZ doubles the database bill. Say
  # "single-AZ, no automatic failover" out loud rather than letting anyone assume otherwise.
  multi_az = false

  backup_retention_period = 1
  skip_final_snapshot     = true
  deletion_protection     = false

  # Both false to keep the free tier genuinely free.
  performance_insights_enabled = false
  monitoring_interval          = 0

  apply_immediately = true

  tags = { Name = local.name }
}

resource "aws_elasticache_subnet_group" "main" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

/*
 * One cache node, no replica, no failover, and no encryption in transit.
 *
 * That last one is a real decision, not an omission: enabling transit encryption on ElastiCache
 * requires the client to speak TLS, and the application's Redis settings do not. Turning it on
 * here without changing the app would produce a cache that silently fails every operation — which,
 * because RedisLinkCache degrades to a database read rather than erroring, would look like a
 * working service with a cache that never hits. The traffic never leaves a private subnet that has
 * no internet route. For anything holding real user data, enable it and configure the client to
 * match, in that order.
 */
resource "aws_elasticache_cluster" "main" {
  cluster_id           = local.name
  engine               = "redis"
  engine_version       = "7.1"
  node_type            = var.cache_node_type
  num_cache_nodes      = 1
  parameter_group_name = "default.redis7"
  port                 = 6379

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.data.id]

  tags = { Name = local.name }
}

resource "random_password" "jwt" {
  # HS256 with a short key can be brute-forced offline from a single captured token; the
  # application refuses to start below 32 bytes.
  length  = 64
  special = false
}

/*
 * Secrets live in Secrets Manager and are injected into the task at start time, so neither the
 * database password nor the signing key appears in the task definition, in the console, or in
 * `docker inspect`. Secrets Manager costs about $0.40 per secret per month; SSM Parameter Store
 * would do the same job free, and is the swap to make if the bill matters more than the rotation
 * features nothing here uses yet.
 */
resource "aws_secretsmanager_secret" "db_password" {
  name                    = "${local.name}/db-password"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "db_password" {
  secret_id     = aws_secretsmanager_secret.db_password.id
  secret_string = random_password.db.result
}

resource "aws_secretsmanager_secret" "jwt" {
  name                    = "${local.name}/jwt-secret"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "jwt" {
  secret_id     = aws_secretsmanager_secret.jwt.id
  secret_string = random_password.jwt.result
}
