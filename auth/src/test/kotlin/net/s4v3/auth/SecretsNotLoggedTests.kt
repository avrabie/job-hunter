package net.s4v3.auth

import net.s4v3.auth.user.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the sensitive flows with verbose logging and checks that no secret value reaches the log. */
@WebIntegrationTest
@TestPropertySource(
    properties = [
        "logging.level.org.springframework.security=TRACE",
        "logging.level.org.springframework.web=DEBUG",
        "logging.level.org.springframework.jdbc=DEBUG",
        "logging.level.net.s4v3=TRACE",
    ],
)
class SecretsNotLoggedTests(
    @LocalServerPort private val port: Int,
    @Autowired private val users: UserRepository,
    @Autowired private val encoder: PasswordEncoder,
    @Autowired private val mail: RecordingMailSender,
) {

    @Test
    fun `passwords, tokens, codes and authorization headers never appear in the log`() {
        val flow = AuthFlow(port)
        val email = "log-${UUID.randomUUID()}@example.com"
        val registerPassword = "Reg1ster-Secret-pw"
        val wrongPassword = "Wr0ng-Secret-pw"
        val resetPassword = "Res3t-Secret-pw"
        val changedPassword = "Chang3d-Secret-pw"
        val appender = LogCapture.attach()
        val secrets = mutableMapOf<String, String>()
        try {
            flow.postJson("${flow.base}/api/register", """{"email":"$email","password":"$registerPassword"}""")
            AuthFlow(port).login(email, wrongPassword)

            val pkce = flow.pkce()
            val redirect = flow.authorizeAndLogin(email, registerPassword, pkce)
            val code = flow.codeFrom(redirect)
            val tokens = flow.exchange(code, pkce.verifier).json()
            val access = tokens["access_token"].asString()
            secrets["authorization code"] = code
            secrets["access token"] = access
            secrets["id token"] = tokens["id_token"].asString()
            secrets["refresh token"] = tokens["refresh_token"].asString()
            secrets["code verifier"] = pkce.verifier

            flow.postJson("${flow.base}/api/password-reset/request", """{"email":"$email"}""")
            val resetToken = mail.tokenOf(mail.to(email).single())
            flow.postJson(
                "${flow.base}/api/password-reset/confirm",
                """{"token":"$resetToken","newPassword":"$resetPassword"}""",
            )
            val access2 = AuthFlow(port).tokens(email, resetPassword)["access_token"].asString()
            flow.putJson(
                "${flow.base}/api/me/password",
                """{"currentPassword":"$resetPassword","newPassword":"$changedPassword"}""",
                "Authorization" to "Bearer $access2",
            )
            secrets["access token 2"] = access2
            // a rejected bearer token
            flow.get("${flow.base}/api/me/password", "Authorization" to "Bearer ${access.dropLast(4)}AAAA")
            secrets["tampered bearer token"] = access.dropLast(4) + "AAAA"
            secrets["reset token (outside the logging mail sender)"] = resetToken
        } finally {
            LogCapture.detach(appender)
        }

        val lines = LogCapture.lines(appender)
        val everything = lines.joinToString("\n")
        assertTrue(lines.size > 50, "verbose logging was on: ${lines.size} lines")

        for (password in listOf(registerPassword, wrongPassword, resetPassword, changedPassword)) {
            assertTrue(password !in everything, "password leaked: $password")
        }
        for ((name, value) in secrets) {
            val leaking = lines.filter { value in it }
            if (name.startsWith("reset token")) {
                // allowed only in the line the temporary logging mail sender writes
                assertTrue(leaking.all { it.startsWith("net.s4v3.auth.mail.LoggingMailSender:") }, "$name leaked: $leaking")
                assertEquals(1, leaking.size, name)
            } else {
                assertTrue(leaking.isEmpty(), "$name leaked: ${leaking.map { it.take(200) }}")
            }
        }
        assertTrue("Bearer ey" !in everything, "an Authorization header value was logged")
        assertTrue("Basic " !in everything.replace("Basic authentication", ""), "a Basic credential was logged")
    }
}
