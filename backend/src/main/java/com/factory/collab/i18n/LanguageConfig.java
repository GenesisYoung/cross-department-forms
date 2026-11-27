package com.factory.collab.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import java.io.IOException;
import java.util.Locale;

@Configuration
public class LanguageConfig {
    @Bean
    public LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override public Locale resolveLocale(HttpServletRequest request) {
                return Messages.resolve(request.getHeader("Accept-Language"));
            }
        };
    }

    /** Covers the security chain too, which runs before MVC's locale resolver. */
    @Component
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public static class LanguageFilter extends OncePerRequestFilter {
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            var previous = LocaleContextHolder.getLocaleContext();
            Locale locale = Messages.resolve(request.getHeader("Accept-Language"));
            try {
                LocaleContextHolder.setLocale(locale);
                response.setHeader("Content-Language", locale.toLanguageTag());
                response.addHeader("Vary", "Accept-Language");
                chain.doFilter(request, response);
            } finally {
                LocaleContextHolder.setLocaleContext(previous);
            }
        }
    }
}
