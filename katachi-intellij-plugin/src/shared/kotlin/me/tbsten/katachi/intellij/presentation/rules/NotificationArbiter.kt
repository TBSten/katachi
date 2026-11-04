package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision

internal enum class NotificationWinner { Template, Rule, None }

// Priority: a generation under way or failed (dropping it would lose the user's next step), then "no role holds this file", then "a template fits".
internal fun arbitrateNotifications(template: NotificationDecision, rule: NoRoleDecision): NotificationWinner = when {
    template is NotificationDecision.Generating || template is NotificationDecision.FailedWithCommand -> NotificationWinner.Template
    rule is NoRoleDecision.NoRole -> NotificationWinner.Rule
    template != NotificationDecision.Hidden -> NotificationWinner.Template
    else -> NotificationWinner.None
}
