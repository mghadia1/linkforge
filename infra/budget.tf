/*
 * The most likely bad outcome of this whole project is not an outage — it is a stack nobody
 * remembers, quietly billing a student for months. This is the cheapest insurance available.
 *
 * An alert is not a cap: AWS has no spending limit, so this tells you to run `tofu destroy`, it
 * does not stop anything by itself.
 */
resource "aws_budgets_budget" "monthly" {
  name         = "${local.name}-monthly"
  budget_type  = "COST"
  limit_amount = tostring(var.monthly_budget_usd)
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  # Warn at 60% of the budget, while there is still time to act.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 60
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_alert_email]
  }

  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "ACTUAL"
    subscriber_email_addresses = [var.budget_alert_email]
  }

  # Forecast, not just actuals: by the time actual spend crosses the line the month is already
  # lost, whereas a forecast breach fires in the first days.
  notification {
    comparison_operator        = "GREATER_THAN"
    threshold                  = 100
    threshold_type             = "PERCENTAGE"
    notification_type          = "FORECASTED"
    subscriber_email_addresses = [var.budget_alert_email]
  }
}
