package rw.ikimina.groups.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import rw.ikimina.shared.web.WebConfig;

@Configuration(proxyBeanMethods = false)
class GroupsWebConfig implements WebMvcConfigurer {

    private final GroupAccessGuard guard;

    GroupsWebConfig(GroupAccessGuard guard) {
        this.guard = guard;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(guard).addPathPatterns("/api/v1/groups/**").order(WebConfig.GROUP_ACCESS_ORDER);
    }
}
