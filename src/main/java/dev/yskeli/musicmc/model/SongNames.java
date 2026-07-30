package dev.yskeli.musicmc.model;

import java.text.Normalizer;
import java.util.Locale;

public final class SongNames {
    private static final String INVALID_FILENAME_CHARACTERS = "<>:\"/\\|?*";

    private SongNames() {}

    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).strip().toLowerCase(Locale.ROOT);
    }

    public static String validateDisplayName(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).strip();
        if (normalized.isEmpty() || normalized.length() > 48 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("歌曲名称必须为 1 到 48 个可见字符");
        }
        if (normalized.equals(".")
                || normalized.equals("..")
                || normalized.endsWith(".")
                || normalized.chars().anyMatch(character -> INVALID_FILENAME_CHARACTERS.indexOf(character) >= 0)) {
            throw new IllegalArgumentException("歌曲名称不能包含 Windows 文件名禁用符号，也不能以句点结尾");
        }
        return normalized;
    }
}
