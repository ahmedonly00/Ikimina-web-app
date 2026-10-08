package rw.ikimina.shared.i18n;

import java.util.List;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

/**
 * Supported locales: English and Kinyarwanda (spec 4.3). The request locale comes
 * from {@code Accept-Language}; anything unsupported falls back to English.
 * Phase 1 adds the user's stored preference ahead of the header.
 */
@Configuration(proxyBeanMethods = false)
public class LocaleConfig {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale KINYARWANDA = Locale.of("rw");
    public static final List<Locale> SUPPORTED = List.of(ENGLISH, KINYARWANDA);

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setSupportedLocales(SUPPORTED);
        resolver.setDefaultLocale(ENGLISH);
        return resolver;
    }
}
