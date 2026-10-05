package me.tbsten.katachi.intellij.presentation.rules

import me.tbsten.katachi.intellij.presentation.entry.NotificationDecision

/** Which of the two katachi notifications a file shows. */
internal enum class NotificationWinner { Template, Rule, None }

/**
 * One file shows at most one katachi notification (design 2.4). In order: a generation under way or
 * failed (the template notification: dropping it would lose the user's next step), then "no role holds
 * this file" (the test will fail), then "a template fits" (a convenience).
 *
 * Both providers call it with the same two decisions, so they always agree on the winner and a file
 * never gets two or none when there is something to show.
 *
 * ```kotlin
 * if (arbitrateNotifications(template, rule) != NotificationWinner.Rule) return null
 * ```
 */
internal fun arbitrateNotifications(template: NotificationDecision, rule: NoRoleDecision): NotificationWinner = when {
    template is NotificationDecision.Generating || template is NotificationDecision.FailedWithCommand -> NotificationWinner.Template
    rule is NoRoleDecision.NoRole -> NotificationWinner.Rule
    template != NotificationDecision.Hidden -> NotificationWinner.Template
    else -> NotificationWinner.None
}
