package dev.yskeli.musicmc.model;

import java.text.Normalizer;
import java.util.Locale;

public final class SongNames {
    private SongNames() {}

    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }

    public static String validateDisplayName(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (normalized.isEmpty() || normalized.length() > 48
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("歌曲命令名称必须是 1 到 48 个可见字符。");
        }
        return normalized;
    }
}
