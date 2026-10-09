package rw.ikimina.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import rw.ikimina.shared.security.RecentAuthenticationInterceptor;

/**
 * Handler interceptors that apply across modules. Order matters: the group membership
 * guard (order {@value #GROUP_ACCESS_ORDER}, registered by the groups module) runs first,
 * so a non-member gets 404 before anything else can reveal that the group exists.
 */
@Configuration(proxyBeanMethods = false)
public class WebConfig implements WebMvcConfigurer {

    public static final int GROUP_ACCESS_ORDER = 10;
    public static final int RECENT_AUTHENTICATION_ORDER = 20;

    private final RecentAuthenticationInterceptor recentAuthentication;

    public WebConfig(RecentAuthenticationInterceptor recentAuthentication) {
        this.recentAuthentication = recentAuthentication;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(recentAuthentication).order(RECENT_AUTHENTICATION_ORDER);
    }
}
