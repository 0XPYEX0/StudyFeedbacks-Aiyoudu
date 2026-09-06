package me.xpyex.software.feedback.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * 单个学生的课时配置（对应 config/schedule/{studentId}.json）。
 * <p>
 * 一个学生可以一周上多个时段：每个时段是一个 {@link SchedulePeriod}（"星期几 + 时段"的组合），
 * 例如 周五晚上 + 周六上午。分组/上课统计都基于这些时段的集合。
 * <p>
 * 约定：
 * <ul>
 *     <li>startDate 为计费期/排课开始日期（ISO yyyy-MM-dd）</li>
 *     <li>weeklyDay 与 timeSlot 均保存中文（如 "周五"、"晚上"），便于直接拼接分组名 "周五晚上"</li>
 *     <li>leaveDates / extraDates 内的日期为 ISO yyyy-MM-dd 字符串（去重）</li>
 *     <li>attendedDates 为已上课日期（去重，仅记录早于今天的真实/按排课推算的历史），历史不可删除</li>
 * </ul>
 */
@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class StudentSchedule {
    /** 可选的上课星期（中文），顺序即展示/排序顺序 */
    public static final List<String> WEEK_DAYS = List.of("周一", "周二", "周三", "周四", "周五", "周六", "周日");
    /** 可选的时段（中文） */
    public static final List<String> TIME_SLOTS = List.of("上午", "下午", "晚上");

    private int studentId;
    /** 冗余保存真实姓名，用于生成可读的文件名（config/schedule/{真实姓名}_{studentId}.json） */
    private String realName;
    /** 计费期开始日期，ISO yyyy-MM-dd */
    private String startDate;
    /**
     * 课时配置"生效变更日"（ISO yyyy-MM-dd，可为空）。
     * 用于：中途修改上课时段后，新时段从该日起算，<b>不去自动改动此前的已上课历史</b>；
     * 空值时表示从未改过时段，自动从 startDate 补齐。
     */
    private String updateDate;
    /** 总课时 */
    private int totalLessons = 30;
    /** 上课时段组合（可多个：如 周五晚上、周六上午） */
    private List<SchedulePeriod> periods = new ArrayList<>();
    /** 请假日期 */
    private List<String> leaveDates = new ArrayList<>();
    /** 加课日期 */
    private List<String> extraDates = new ArrayList<>();
    /** 已上课日期（去重升序） */
    private List<String> attendedDates = new ArrayList<>();

    /** 安全获取时段列表 */
    public List<SchedulePeriod> getRealPeriods() {
        return periods == null ? List.of() : periods;
    }

    /** 该生对应的分组名集合（去重），如 ["周五晚上","周六上午"]；无有效时段则返回空 */
    public List<String> groupKeys() {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        for (SchedulePeriod p : getRealPeriods()) {
            String key = (p.getWeeklyDay() == null ? "" : p.getWeeklyDay())
                + (p.getTimeSlot() == null ? "" : p.getTimeSlot());
            if (!key.isBlank()) keys.add(key);
        }
        return new ArrayList<>(keys);
    }

    /** 已上课次数 */
    public int attendedCount() {
        return attendedDates == null ? 0 : attendedDates.size();
    }

    /** 剩余课次 = 总课时 - 已上课次数 */
    public int remainingLessons() {
        return totalLessons - attendedCount();
    }

    /** 单个上课时段："星期几 + 时段" 组合 */
    @Data
    @Accessors(chain = true)
    @NoArgsConstructor(staticName = "of")
    public static class SchedulePeriod {
        private String weeklyDay;
        private String timeSlot;

        /** 分组名，如 "周五晚上" */
        public String groupKey() {
            return (weeklyDay == null ? "" : weeklyDay) + (timeSlot == null ? "" : timeSlot);
        }
    }
}
