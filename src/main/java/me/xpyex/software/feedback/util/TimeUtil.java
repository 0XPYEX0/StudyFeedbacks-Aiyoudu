package me.xpyex.software.feedback.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.WeakHashMap;

public class TimeUtil {
    public static final String dateFormat = "yyyy-MM-dd";
    private static final WeakHashMap<String, SimpleDateFormat> formats = new WeakHashMap<>();

    public static Exception sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        }
        return null;
    }

    public static String parseDate(Date date, String pattern) {
        SimpleDateFormat format = formats.get(pattern);
        if (format == null) {
            format = new SimpleDateFormat(pattern);
            formats.put(pattern, format);
        }
        return format.format(date);
    }
}
