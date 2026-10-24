package net.s4v3.backend.profile

private val nameSegment = Regex("[A-Za-z0-9._-]{1,100}")

/** Whether [fullName] is `owner/repo` with each part 1 to 100 characters of letters, digits, dots, dashes and underscores. */
fun isValidFullName(fullName: String): Boolean {
    val parts = fullName.split('/')
    return parts.size == 2 && parts.all { it != "." && it != ".." && nameSegment.matches(it) }
}
