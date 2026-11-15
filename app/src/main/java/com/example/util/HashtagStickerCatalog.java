package com.example.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HashtagStickerCatalog {

    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#[a-zA-Z0-9_]{1,30}");
    private static final Pattern UPI_PATTERN = Pattern.compile("^[a-zA-Z0-9._\\-]{2,}@[a-zA-Z0-9]{2,}$");

    public static final List<String> TRENDING_HASHTAGS = Arrays.asList(
            "#trending",
            "#reels",
            "#viral",
            "#music",
            "#comedy",
            "#dance",
            "#gaming",
            "#foodie",
            "#travel",
            "#creator"
    );

    public static final List<String> TRENDING_STICKERS = Arrays.asList(
            "🔥 Hot",
            "⚡ Viral",
            "🎵 Remix",
            "💎 Gold",
            "🚀 Boost",
            "😂 LOL",
            "❤️ Vibe",
            "👑 King"
    );

    private HashtagStickerCatalog() {}

    public static List<String> extractHashtags(String text, List<String> selectedTags) {
        List<String> result = new ArrayList<>();
        if (selectedTags != null) {
            for (String t : selectedTags) {
                if (t != null && !t.trim().isEmpty()) {
                    String norm = t.trim().startsWith("#") ? t.trim().toLowerCase() : "#" + t.trim().toLowerCase();
                    if (!result.contains(norm) && result.size() < 10) {
                        result.add(norm);
                    }
                }
            }
        }
        if (text != null) {
            Matcher matcher = HASHTAG_PATTERN.matcher(text);
            while (matcher.find() && result.size() < 10) {
                String tag = matcher.group().toLowerCase();
                if (!result.contains(tag)) {
                    result.add(tag);
                }
            }
        }
        return result;
    }

    public static boolean isValidUpiId(String upiId) {
        return upiId != null && UPI_PATTERN.matcher(upiId.trim()).matches();
    }
}
