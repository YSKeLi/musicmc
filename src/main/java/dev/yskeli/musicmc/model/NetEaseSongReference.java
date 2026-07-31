package dev.yskeli.musicmc.model;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class NetEaseSongReference {
    private static final Pattern ID_PATTERN = Pattern.compile("[1-9][0-9]{0,19}");

    private NetEaseSongReference() {}

    public static String parse(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (ID_PATTERN.matcher(normalized).matches()) {
            return normalized;
        }
        return parseUrl(normalized.replace("&amp;", "&"));
    }

    private static String parseUrl(String value) {
        final URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException exception) {
            throw invalidReference();
        }
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || !isNetEaseHost(host)) {
            throw invalidReference();
        }
        String id = queryParameter(uri.getRawQuery(), "id");
        if (id == null) {
            String fragment = uri.getRawFragment();
            int queryStart = fragment == null ? -1 : fragment.indexOf('?');
            if (queryStart >= 0) {
                id = queryParameter(fragment.substring(queryStart + 1), "id");
            }
        }
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw invalidReference();
        }
        return id;
    }

    private static boolean isNetEaseHost(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("music.163.com") || normalized.endsWith(".music.163.com");
    }

    private static String queryParameter(String query, String wantedName) {
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String rawName = separator < 0 ? part : part.substring(0, separator);
            if (wantedName.equals(decode(rawName))) {
                return separator < 0 ? "" : decode(part.substring(separator + 1));
            }
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw invalidReference();
        }
    }

    private static IllegalArgumentException invalidReference() {
        return new IllegalArgumentException("请输入网易云歌曲数字 ID，或 music.163.com 的歌曲链接。");
    }
}
