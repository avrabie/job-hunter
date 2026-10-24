package net.s4v3.backend.cv.export

/** The groups of skills in the Professional template, in print order. [OTHER] is printed last. */
enum class SkillGroup { LANGUAGES, FRAMEWORKS, MESSAGING, SECURITY, CLOUD, TESTING, DATABASES, TOOLS, OTHER }

/**
 * Puts a skill name into one [SkillGroup] by a fixed keyword list. A keyword matches a whole token of the name
 * (case-insensitive): `Javascript` is not `Java`, `C` is not inside `Docker`, while `C++`, `C#`, `PL/SQL` and
 * `CI/CD` match as whole names. When a name matches several groups, the first group in print order wins. A name that
 * matches none goes to [SkillGroup.OTHER]. The list is a layout aid only; a skill appears in exactly one group.
 */
internal object SkillGroups {

    private val keywords: Map<SkillGroup, List<String>> = mapOf(
        SkillGroup.LANGUAGES to listOf(
            "java", "kotlin", "python", "c", "c++", "c#", "rust", "go", "scala", "typescript", "javascript", "sql",
            "pl/sql", "julia", "r", "php", "ruby", "swift", "bash",
        ),
        SkillGroup.FRAMEWORKS to listOf(
            "spring", "spring boot", "webflux", "hibernate", "jpa", "react", "angular", "vue", "django", "flask",
            "quarkus", "micronaut", "jax-rs", "jsf", "jsp",
        ),
        SkillGroup.MESSAGING to listOf("kafka", "activemq", "rabbitmq", "jms", "mqtt", "nats", "pulsar"),
        SkillGroup.SECURITY to listOf("oauth2", "oidc", "jwt", "saml", "mtls", "scim", "keycloak", "tls", "sso"),
        SkillGroup.CLOUD to listOf(
            "azure", "aws", "gcp", "docker", "podman", "kubernetes", "rancher", "terraform", "helm", "hetzner",
            "openshift", "pcf", "ansible", "linux", "rhel",
        ),
        SkillGroup.TESTING to listOf(
            "junit", "mockito", "testcontainers", "selenium", "cypress", "jest", "playwright", "gatling", "jmeter",
            "elk", "opentelemetry", "otel", "prometheus", "grafana",
        ),
        SkillGroup.DATABASES to listOf(
            "oracle", "postgresql", "postgres", "mysql", "mariadb", "mongodb", "cassandra", "redis", "h2", "sqlite",
            "elasticsearch", "neo4j",
        ),
        SkillGroup.TOOLS to listOf(
            "git", "gitlab", "github", "maven", "gradle", "jenkins", "jira", "intellij", "ci/cd",
        ),
    )

    /** One pattern per keyword: the keyword with no letter, digit, `_`, `+` or `#` directly before or after it. */
    private val matchers: List<Pair<SkillGroup, List<Regex>>> = SkillGroup.entries.map { group ->
        group to keywords[group].orEmpty().map { keyword ->
            Regex("(?<![\\p{L}\\p{N}_+#])${Regex.escape(keyword)}(?![\\p{L}\\p{N}_+#])")
        }
    }

    fun group(skillName: String): SkillGroup {
        val name = skillName.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
        return matchers.firstOrNull { (_, patterns) -> patterns.any { it.containsMatchIn(name) } }?.first
            ?: SkillGroup.OTHER
    }
}
