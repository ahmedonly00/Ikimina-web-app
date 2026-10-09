package rw.ikimina.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces {@link RequiresRecentAuthentication}. */
@Component
public class RecentAuthenticationInterceptor implements HandlerInterceptor {

    private final StepUp stepUp;

    public RecentAuthenticationInterceptor(StepUp stepUp) {
        this.stepUp = stepUp;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(RequiresRecentAuthentication.class)) {
            stepUp.require();
        }
        return true;
    }
}
