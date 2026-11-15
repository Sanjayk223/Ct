package com.example.util;

import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TimeFormatUtil {

    private TimeFormatUtil() {}

    public static String formatRelative(Timestamp ts) {
        if (ts == null) return "Just now";
        long diffMs = System.currentTimeMillis() - ts.toDate().getTime();
        if (diffMs < 60_000L) return "Just now";
        long mins = diffMs / 60_000L;
        if (mins < 60L) return mins + "m ago";
        long hrs = mins / 60L;
        if (hrs < 24L) return hrs + "h ago";
        long days = hrs / 24L;
        if (days < 7L) return days + "d ago";
        return new SimpleDateFormat("dd MMM", Locale.US).format(ts.toDate());
    }

    public static String formatClock(Timestamp ts) {
        Date d = ts != null ? ts.toDate() : new Date();
        return new SimpleDateFormat("hh:mm a", Locale.US).format(d);
    }

    public static String formatCount(long count) {
        if (count >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM", count / 1_000_000.0);
        }
        if (count >= 1_000L) {
            return String.format(Locale.US, "%.1fK", count / 1_000.0);
        }
        return String.valueOf(count);
    }
}
