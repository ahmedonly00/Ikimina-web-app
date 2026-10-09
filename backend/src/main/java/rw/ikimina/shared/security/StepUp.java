package rw.ikimina.shared.security;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;
import rw.ikimina.shared.error.ApiException;
import rw.ikimina.shared.error.ErrorCode;

/**
 * Step-up check (spec 16.1) for use inside services, where whether it applies depends on
 * the request - e.g. a settings change needs it only when it touches financial parameters.
 * Endpoints that always need it use {@link RequiresRecentAuthentication} instead.
 */
@Component
public class StepUp {

    private final Clock clock;
    private final Duration maxAge;

    public StepUp(Clock clock, AuthProperties properties) {
        this.clock = clock;
        this.maxAge = properties.recentAuthMaxAge();
    }

    /** @throws ApiException REAUTHENTICATION_REQUIRED unless the password was entered recently */
    public void require() {
        AuthenticatedUser user = CurrentUser.require();
        if (user.authTime().plus(maxAge).isBefore(clock.instant())) {
            throw new ApiException(ErrorCode.REAUTHENTICATION_REQUIRED);
        }
    }
}
