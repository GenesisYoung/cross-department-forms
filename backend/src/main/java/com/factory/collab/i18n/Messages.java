package com.factory.collab.i18n;

import org.springframework.context.i18n.LocaleContextHolder;
import java.util.*;
import java.util.regex.*;

/** One catalog for HTTP errors and background notifications. Never interpolate using regex replacement strings. */
public final class Messages {
    public static final List<Locale> SUPPORTED = List.of(Locale.ENGLISH, Locale.SIMPLIFIED_CHINESE, Locale.TRADITIONAL_CHINESE);
    private static final ResourceBundle.Control CONTROL = ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static final Pattern ARG = Pattern.compile("\\{(\\d+)\\}");
    private Messages() {}

    public static Locale resolve(String header) {
        if (header == null || header.isBlank()) return Locale.ENGLISH;
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                if (range.getWeight() == 0) continue;
                String tag = range.getRange();
                if (tag.equals("en") || tag.startsWith("en-")) return Locale.ENGLISH;
                if (tag.equals("zh") || tag.startsWith("zh-")) {
                    return tag.contains("hant") || tag.endsWith("-tw") || tag.endsWith("-hk") || tag.endsWith("-mo")
                            ? Locale.TRADITIONAL_CHINESE : Locale.SIMPLIFIED_CHINESE;
                }
            }
        } catch (IllegalArgumentException ignored) { /* malformed headers use English */ }
        return Locale.ENGLISH;
    }

    public static String text(String code, Object... args) {
        return text(LocaleContextHolder.getLocale(), code, args);
    }

    public static String text(Locale locale, String code, Object... args) {
        ResourceBundle bundle = ResourceBundle.getBundle("messages", locale, CONTROL);
        String pattern = bundle.containsKey(code) ? bundle.getString(code) : code;
        Matcher matcher = ARG.matcher(pattern);
        return matcher.replaceAll(m -> {
            int i = Integer.parseInt(m.group(1));
            return Matcher.quoteReplacement(i < args.length ? String.valueOf(args[i]) : m.group());
        });
    }
}
