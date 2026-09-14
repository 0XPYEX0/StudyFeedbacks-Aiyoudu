package me.xpyex.software.feedback.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 单个学生的课时配置（对应 config/schedule/{真实姓名}_{studentId}.json）。
 * <p>
 * 模型刻意保持简单：<b>只维护"学生有没有来"</b>。
 * <ul>
 *     <li>{@code attendedDates}：来了的日期（计入已上课次），加课/补录直接加到这里</li>
 *     <li>{@code leaveDates}：没来的日期（不计入已上课次），请假/缺勤记在这里</li>
 * </ul>
 * 二者互斥（同一天只会出现在其中一边）。
 * <p>
 * {@code periods}（每周上课时段，可多个：周五晚上、周六上午…）用于分组、排课提示与自动补记。
 * 自动补记只发生在 {@code lastModify}（上次修改日）之后：把"已过完但未记录"的排课日补进 attended，
 * 免得一周没开软件就要手动补所有学生；而 {@code lastModify} 之前的历史完全由手动点选决定，
 * 中途调课时不会把旧/新时段的课往前补算（避免多算课时）。
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudentSchedule {
    /**
     * 可选的上课星期（中文），顺序即展示/排序顺序
     */
    public static final List<String> WEEK_DAYS = List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日");
    /**
     * 可选的时段（中文）
     */
    public static final List<String> TIME_SLOTS = List.of("上午", "下午", "晚上");

    private int studentId;
    /**
     * 冗余保存真实姓名，用于生成可读的文件名（config/schedule/{真实姓名}_{studentId}.json）
     */
    private String realName;
    /**
     * 计费期开始日期，ISO yyyy-MM-dd（仅作参考与展示）
     */
    private String startDate;
    /**
     * 上次修改日（ISO yyyy-MM-dd，改动上课时段/开始日时置为当天）。
     * <p>
     * 它是"自动补记"的起点：只对 {@code >= lastModify 且 < 今天} 的排课日自动补记；
     * 此前的历史既不自动修改也不自动补算，避免中途调课时把旧时段/新时段的课往前补出来。
     */
    private String lastModify;
    /**
     * 总课时
     */
    private int totalLessons = 30;
    /**
     * 上课时段组合（可多个：如 周五晚上、周六上午），仅用于分组与排课参考
     */
    private List<SchedulePeriod> periods = new ArrayList<>();
    /**
     * 没来的日期（请假/缺勤），不计入已上课次
     */
    private List<String> leaveDates = new ArrayList<>();
    /**
     * 来了的日期，计入已上课次
     */
    private List<String> attendedDates = new ArrayList<>();

    /**
     * 安全获取时段列表
     */
    public List<SchedulePeriod> getRealPeriods() {
        return periods == null ? List.of() : periods;
    }

    /**
     * 该生对应的分组名集合（去重），如 ["周五晚上","周六上午"]；无有效时段则返回空
     */
    public List<String> groupKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (SchedulePeriod p : getRealPeriods()) {
            String key = p.groupKey();
            if (!key.isBlank()) keys.add(key);
        }
        return new ArrayList<>(keys);
    }

    /**
     * 某天是否属于该生的排课日（星期匹配，且不早于开始日期）；仅用于界面提示
     */
    public boolean isScheduledOn(LocalDate date) {
        if (date == null) return false;
        LocalDate start = startDate == null ? null : LocalDate.parse(startDate);
        if (start != null && date.isBefore(start)) return false;
        for (SchedulePeriod p : getRealPeriods()) {
            if (p.getWeeklyDay() == null) continue;
            int idx = WEEK_DAYS.indexOf(p.getWeeklyDay().trim());
            if (idx >= 0 && date.getDayOfWeek().getValue() == idx + 1) return true;
        }
        return false;
    }

    /**
     * 已上课次数
     */
    public int attendedCount() {
        return attendedDates == null ? 0 : attendedDates.size();
    }

    /**
     * 剩余课次 = 总课时 - 已上课次数
     */
    public int remainingLessons() {
        return totalLessons - attendedCount();
    }

    /**
     * 单个上课时段："星期几 + 时段" 组合
     */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor(staticName = "of")
    public static class SchedulePeriod {
        private String weeklyDay;
        private String timeSlot;

        /**
         * 分组名，如 "周五晚上"
         */
        public String groupKey() {
            return (weeklyDay == null ? "" : weeklyDay) + (timeSlot == null ? "" : timeSlot);
        }
    }
}
