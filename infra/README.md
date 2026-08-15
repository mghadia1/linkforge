# Deploying LinkForge to AWS

> **Nothing here has been deployed.** There is no live URL, no AWS account was involved, and no
> resource in these files has ever existed. The configuration is written, formatted, schema-
> validated against the AWS provider, and graph-checked for cycles — that is all. Everything below
> is a plan, and the first real `apply` will surface things a validator cannot.

## What it builds

```
                         ┌──────────── CloudFront ────────────┐
   visitor ── https ──►  │  /index.html /assets/* /favicon.ico│──► S3 (private, OAC)
                         │  everything else (incl. /{code})   │──► ALB ──► ECS Fargate (API)
                         └────────────────────────────────────┘                │
                                                                    ┌──────────┴──────────┐
                                                            RDS Postgres        ElastiCache Redis
                                                          (private subnet)       (private subnet)
```

**One hostname, so there is no CORS anywhere** — the same reason nginx proxies `/api` locally.

**The default CloudFront behaviour is the API, not the bucket.** The root path belongs to short
codes, and a short code has no prefix to write a path pattern for, so only the handful of paths
that are genuinely the app get carved out to S3. This is the same reasoning that put the
management API under `/api` in the first place.

## Cost, honestly

Roughly, `us-east-1`, before free-tier credits:

| | ~$/month |
|---|---|
| ALB | 16 |
| Fargate, 1 task @ 0.25 vCPU / 0.5 GB | 9 |
| RDS `db.t4g.micro` + 20 GB gp3 | 15 (free tier: 0 for 12 months) |
| ElastiCache `cache.t4g.micro` | 12 (free tier: 0 for 12 months) |
| Secrets Manager, 2 secrets | 1 |
| CloudFront + S3 at demo traffic | <1 |
| **Total** | **~$25–55/month** |

**There is no NAT gateway**, which would have been about $32/month on its own — the largest line
on the bill, for a component that exists only to give tasks outbound access. Instead tasks run in
public subnets with public IPs and a security group that accepts traffic only from the load
balancer. See the comment in `network.tf` for the trade and the stricter alternative.

Even so: **at $25–55/month this is not a stack to leave running.** Stand it up, take screenshots,
record the numbers, tear it down. `infra/budget.tf` creates a budget alarm because the realistic
failure here is a forgotten stack, not an outage — and note that AWS has no spending cap, so an
alarm tells you to act, it does not stop anything.

## First deploy

Prerequisites: an AWS account, the AWS CLI configured, and OpenTofu (or Terraform ≥ 1.6).

**1. Create the account-wide GitHub OIDC provider** (once per account, not part of this stack):

```bash
aws iam create-open-id-connect-provider --url https://token.actions.githubusercontent.com --client-id-list sts.amazonaws.com
```

**2. Apply the infrastructure.** Review the plan properly — it creates about 40 billable resources.

```bash
cd infra && tofu init
```

```bash
tofu apply -var 'budget_alert_email=you@example.com' -var 'github_repository=mghadia1/linkforge'
```

RDS takes 5–10 minutes and CloudFront 10–15, so first apply is roughly 20 minutes.

**3. Set the outputs as GitHub repository variables** (Settings → Secrets and variables → Actions
→ Variables). None of these is a secret; they are names and ARNs.

| Variable | From |
|---|---|
| `AWS_DEPLOY_ROLE` | `tofu output -raw github_deploy_role_arn` |
| `AWS_WEB_BUCKET` | `tofu output -raw web_bucket` |
| `AWS_CLOUDFRONT_DISTRIBUTION` | `tofu output -raw cloudfront_distribution_id` |
| `AWS_APP_URL` | `tofu output -raw app_url` |

**4. Seed the first image.** The ECS service cannot start until something is in ECR, and the
service was created pointing at `:latest`:

```bash
aws ecr get-login-password | docker login --username AWS --password-stdin "$(tofu output -raw ecr_repository_url | cut -d/ -f1)"
```

```bash
docker build --platform linux/amd64 -t "$(tofu output -raw ecr_repository_url):latest" ../api && docker push "$(tofu output -raw ecr_repository_url):latest"
```

**5. Deploy** — run the `Deploy` workflow in GitHub Actions. It builds and pushes the API image
tagged with the commit SHA, registers a task definition revision, rolls the service, waits for it
to stabilise, publishes the frontend to S3, invalidates the CDN, and smoke-tests `/actuator/health`.

## Tear it down

```bash
cd infra && tofu destroy -var 'budget_alert_email=you@example.com' -var 'github_repository=mghadia1/linkforge'
```

`force_delete` on ECR and `force_destroy` on the S3 bucket are set so this does not stop halfway
on a non-empty resource, and `skip_final_snapshot` is on for RDS. All three are right for a
throwaway environment and all three are **wrong** for anything holding data worth keeping.

## Known gaps in this configuration

- **State is local.** No locking, and the file contains the database password in plain text. An S3
  backend with DynamoDB locking is the first change if anyone else touches this.
- **No custom domain, no HTTPS to the origin.** TLS terminates at CloudFront using its default
  certificate; CloudFront to the ALB is plain HTTP. Acceptable with no real user data, not
  otherwise. A domain needs Route 53 plus an ACM certificate in `us-east-1`.
- **No encryption in transit to ElastiCache**, because the application's Redis client is not
  configured for TLS. Turning it on here alone would produce a cache that fails every operation —
  and because `RedisLinkCache` degrades to a database read rather than erroring, it would look
  like a working service whose cache simply never hits. Change the client first.
- **Single task, single-AZ database, one cache node.** Nothing here is highly available. Do not
  describe it as such.
- **No autoscaling and no alarms** beyond the budget. Nothing pages anyone if the service dies.
- **The API is reachable directly at the ALB's DNS name**, bypassing CloudFront. Restricting the
  ALB to CloudFront's managed prefix list is a one-line change that was not made.
