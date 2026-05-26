package me.xpyex.software.feedback.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.WeakHashMap;

public class TimeUtil {
    private static final WeakHashMap<String, SimpleDateFormat> formats = new WeakHashMap<>();
    private static final String dateFormat = "yyyy-MM-dd";

    /**
     * 获取上周的日期范围（周一到周日）
     *
     * @return [上周一，上周日]
     */
    public static String[] getLastWeekRange() {
        Calendar calendar = Calendar.getInstance();
        int today = calendar.get(Calendar.DAY_OF_WEEK); // 1=周日，2=周一...7=周六

        // 计算上周一：从本周的某一天回到上周一
        // 如果今天是周日 (1)，上周一是 6 天前
        // 如果今天是周一 (2)，上周一是 7 天前
        // 如果今天是周六 (7)，上周一是 12 天前
        int mondayDaysToSubtract = (today == 1 ? 6 : today + 5);

        Calendar mondayCalendar = (Calendar) calendar.clone();
        mondayCalendar.add(Calendar.DAY_OF_MONTH, -mondayDaysToSubtract);
        String lastMonday = parseDate(mondayCalendar.getTime(), dateFormat);

        // 计算上周日：从上周一再加 6 天
        Calendar sundayCalendar = (Calendar) mondayCalendar.clone();
        sundayCalendar.add(Calendar.DAY_OF_MONTH, 6);
        String lastSunday = parseDate(sundayCalendar.getTime(), dateFormat);

        AiyouduUtil.log.debug("今天是周{} ({})，上周一：{}，上周日：{}",
            today == 1 ? "日" : String.valueOf(today),
            parseDate(new Date(), dateFormat),
            lastMonday,
            lastSunday);

        return new String[]{lastMonday, lastSunday};
    }

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
