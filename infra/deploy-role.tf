/*
 * The role GitHub Actions assumes to deploy.
 *
 * It authenticates with OIDC, so there is no access key in the repository's secrets. That matters
 * more than it sounds: a long-lived AWS key in CI is a credential that never expires, is copied
 * into every fork that gets one, and shows up in logs when someone prints the environment. OIDC
 * issues a token per run, scoped to this repository, that expires with the job.
 */

data "aws_iam_openid_connect_provider" "github" {
  # Created once per account, outside this stack, because it is account-wide and shared:
  #   aws iam create-open-id-connect-provider \
  #     --url https://token.actions.githubusercontent.com \
  #     --client-id-list sts.amazonaws.com
  url = "https://token.actions.githubusercontent.com"
}

resource "aws_iam_role" "github_deploy" {
  name = "${local.name}-github-deploy"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = data.aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
        }
        # Scoped to this repository. Without a `sub` condition, ANY GitHub repository in the world
        # could assume this role — the single most common and most damaging mistake in an OIDC
        # trust policy.
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:${var.github_repository}:*"
        }
      }
    }]
  })
}

resource "aws_iam_role_policy" "github_deploy" {
  name = "deploy"
  role = aws_iam_role.github_deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "GetAnEcrLoginToken"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*" # this action does not accept a resource
      },
      {
        Sid    = "PushToThisRepositoryOnly"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
          "ecr:BatchGetImage",
        ]
        Resource = aws_ecr_repository.api.arn
      },
      {
        Sid    = "RollTheApiService"
        Effect = "Allow"
        Action = [
          "ecs:DescribeServices",
          "ecs:DescribeTaskDefinition",
          "ecs:RegisterTaskDefinition",
          "ecs:UpdateService",
        ]
        # RegisterTaskDefinition cannot be scoped to a resource; the others are scoped below by
        # a condition on the cluster.
        Resource = "*"
        Condition = {
          StringEqualsIfExists = {
            "ecs:cluster" = aws_ecs_cluster.main.arn
          }
        }
      },
      {
        Sid    = "PassOnlyTheTaskRoles"
        Effect = "Allow"
        Action = ["iam:PassRole"]
        # Without this restriction, permission to register a task definition is permission to run
        # a container as any role in the account.
        Resource = [aws_iam_role.task.arn, aws_iam_role.task_execution.arn]
        Condition = {
          StringEquals = { "iam:PassedToService" = "ecs-tasks.amazonaws.com" }
        }
      },
      {
        Sid      = "PublishTheFrontend"
        Effect   = "Allow"
        Action   = ["s3:PutObject", "s3:DeleteObject", "s3:ListBucket"]
        Resource = [aws_s3_bucket.web.arn, "${aws_s3_bucket.web.arn}/*"]
      },
      {
        Sid      = "InvalidateTheCdnAfterAFrontendDeploy"
        Effect   = "Allow"
        Action   = ["cloudfront:CreateInvalidation"]
        Resource = aws_cloudfront_distribution.main.arn
      },
    ]
  })
}
