package rw.ikimina.shared.time;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one place the system clock is read. Everything else injects {@link Clock};
 * {@code ArchitectureTest} fails the build on any other {@code now()} call.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.system(BusinessTime.ZONE);
    }
}
