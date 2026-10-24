# auth flaky: GoogleConfiguredTests "parsing HTTP/1.1 status line" (not reproduced)

## What I ran (auth/, `./gradlew ... --rerun`)
| run | count | result |
|---|---|---|
| `--tests net.s4v3.auth.GoogleConfiguredTests` | 15 | 15 pass |
| whole auth suite, idle | 3 | 3 pass |
| whole auth suite, 6x `yes` + `gateway/ ./gradlew test --rerun` x3 in parallel | 3 | 3 pass |
No log contained "parsing HTTP". The original failure is NOT reproduced (21 runs, 0 failures). Backend Testcontainers builds were not run.

## Code facts (read)
- `WebIntegrationTest` = `@SpringBootTest(webEnvironment = RANDOM_PORT)`; `AuthFlow(port)` builds `http://localhost:$port/auth`, plain http, one `HttpClient` per test class instance (never closed, harmless).
- No `server.ssl`, no https base URL, no fixed test port. `server.port=9090` only in main application.yaml (asserted by ConfigurationBindingTests, which is not a web test). `HealthTests` second class uses `--server.port=0`; `FakeGoogle` uses `HttpServer` port 0.
- So every request goes to an OS-assigned ephemeral port on `localhost`. Nothing in the repo can make AuthFlow talk TLS or to another test's server by design: each context has its own port, and Spring caches contexts per config, so a context from an earlier class is still alive (listening) while GoogleConfiguredTests runs.

## Most plausible cause (inferred, unproven)
Binary garbage on a status line means the peer was not Tomcat. Candidates, ranked:
1. Port collision with another process: ephemeral ports are shared machine-wide, and the parallel backend Testcontainers builds publish Postgres on random host ports (Docker Desktop proxy listens on them; Postgres/TLS replies are binary). Tomcat bound its port, then the request reached a listener of the other family/interface on the same number via `localhost` (IPv4 vs ::1; Docker proxy may bind a different address than Tomcat's wildcard). Evidence for: binary reply, other Gradle builds with Testcontainers ran at the time, only this one class hit (two tests, so one connection-level condition lasting seconds). Against: the same port is already bound by Tomcat for the same address family, and I could not make it happen with CPU load plus a second Gradle run (no Docker containers in my gateway run).
2. A closed context whose port was reused: a previous cached context was closed by the JVM and another process took its port; AuthFlow of a later class in the same JVM would not use that port, so unlikely.
3. TLS: ruled out by code (no ssl property, http scheme).

## Proposed smallest fix (only if it recurs; nothing to change for a proven cause)
1. `auth/src/test/kotlin/net/s4v3/auth/AuthFlow.kt` line 29: `val base = "http://127.0.0.1:$port/auth"` (pin IPv4, remove the localhost dual-stack ambiguity). Check that the redirect_uri assertion (GoogleLoginPageTests.kt line 44) uses `flow.base`, so it stays consistent; Host-header tests (AuthorizationServerTests) set their own headers.
2. To learn the cause on the next occurrence: in `AuthFlow.send` (line ~54) wrap `http.send` in try/catch and rethrow with `"${builder.build().uri()} ... local ports: " + ` the output of `lsof -nP -iTCP:$port` is overkill; just include the URI and `InetAddress.getAllByName("localhost")` in the message.
3. Not recommended: a shared test server or closing clients; neither explains foreign bytes.
Honest status: unproven; re-run when Docker builds are active (start a backend `./gradlew test` in parallel, which this run did not do) to confirm theory 1.
