package com.example.commons.accounts.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

/**
 * The ID of a user, group, or role: a UUID in the 36-character lower-case text form
 * {@code AbstractAuditableEntity} generates. Anything else is rejected before it reaches
 * a query.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ FIELD, METHOD, PARAMETER, ANNOTATION_TYPE, TYPE_USE })
@Retention(RetentionPolicy.RUNTIME)
@Pattern(regexp = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
@ReportAsSingleViolation
public @interface ResourceId {

	String message() default "{validation.resource-id}";

	Class<?>[] groups() default {};

	Class<? extends jakarta.validation.Payload>[] payload() default {};

}
