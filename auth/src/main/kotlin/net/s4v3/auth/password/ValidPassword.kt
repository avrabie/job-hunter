package net.s4v3.auth.password

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

/** A password of 8 to 72 characters that also fits bcrypt's 72-byte limit. */
@Target(AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidPasswordValidator::class])
annotation class ValidPassword(
    val message: String = "must be 8 to 72 characters",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ValidPasswordValidator : ConstraintValidator<ValidPassword, String?> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean =
        value == null || (value.length in MIN..MAX && value.toByteArray(Charsets.UTF_8).size <= MAX)

    companion object {
        const val MIN = 8
        const val MAX = 72
    }
}
