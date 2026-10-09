package rw.ikimina.shared.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Step-up authentication (spec 16.1): the endpoint requires that the caller entered their
 * password within {@code ikimina.auth.recent-auth-max-age}. Otherwise the request fails with
 * REAUTHENTICATION_REQUIRED and the client calls {@code POST /api/v1/auth/reauth} first.
 *
 * <p>Used for role transfer, financial settings changes, member removal, high-value loan
 * approval and data export.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresRecentAuthentication {
}
