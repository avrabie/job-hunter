package net.s4v3.auth.mail

import org.springframework.stereotype.Component

/** Texts of the mails the service sends. Placeholder wording until the sender address and copy are decided. */
@Component
class Mails {

    fun passwordReset(to: String, link: String) = Mail(
        to = to,
        subject = "Reset your password",
        body = "Use this link to choose a new password. It works once and expires in 30 minutes.\n$link\n" +
            "If you did not ask for this, ignore this message.",
    )

    fun passwordChanged(to: String) = Mail(
        to = to,
        subject = "Your password was changed",
        body = "The password of your account was just changed. If this was not you, reset your password now.",
    )
}
