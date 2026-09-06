package me.xpyex.software.feedback.schedule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.xpyex.software.feedback.data.StudentSchedule;
import me.xpyex.software.feedback.packet.both.StudentInfo;

/**
 * 学生分组管理器：按每个学生的上课时段组合（周五晚上、周六上午…）归组。
 * <p>
 * 一个学生可以有多个时段 → 会同时出现在多个分组标签中。
 * 没有任何时段配置的学生归入 "未排课"。
 */
public class StudentGroupManager {
    public static final String UNGROUPED = "未排课";

    private StudentGroupManager() {
    }

    /** 该生所属的分组名列表（每个上课时段一个组名）；无配置或未排时段时为 ["未排课"] */
    public static List<String> groupKeysOf(int studentId) {
        StudentSchedule schedule = ScheduleManager.load(studentId);
        if (schedule == null) return List.of(UNGROUPED);
        List<String> keys = schedule.groupKeys();
        return keys.isEmpty() ? List.of(UNGROUPED) : keys;
    }

    /**
     * 按上课时段分组（组名："周五晚上" 等；无配置为 "未排课"）。
     * 返回有序 Map：周一~周日、同星期内 上午→下午→晚上，最后为未分组。
     *
     * @param students 要分组的学生
     * @return 组名 -> 学生列表（组内按年级、姓名排序）
     */
    public static Map<String, List<StudentInfo>> groupBy(List<StudentInfo> students) {
        Map<String, List<StudentInfo>> map = new LinkedHashMap<>();
        if (students == null) return map;

        for (StudentInfo s : students) {
            if (s == null) continue;
            for (String key : groupKeysOf(s.getStudentId())) {
                map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
            }
        }

        // 组名排序：周日 → 时段
        Comparator<String> groupOrder = Comparator.comparingInt(StudentGroupManager::rankOf);
        List<String> sortedGroups = new ArrayList<>(map.keySet());
        sortedGroups.sort(groupOrder);

        Map<String, List<StudentInfo>> ordered = new LinkedHashMap<>();
        Comparator<StudentInfo> studentOrder = Comparator
            .comparingInt(StudentInfo::getGrade)
            .thenComparing(StudentInfo::getRealName, Comparator.nullsLast(String::compareTo));
        for (String group : sortedGroups) {
            List<StudentInfo> list = map.get(group);
            list.sort(studentOrder);
            ordered.put(group, list);
        }
        return ordered;
    }

    /** 组的排序权重：每周几*3 + 时段序号；未分组排到最后 */
    private static int rankOf(String group) {
        if (group == null || UNGROUPED.equals(group)) return Integer.MAX_VALUE;
        for (int w = 0; w < StudentSchedule.WEEK_DAYS.size(); w++) {
            String week = StudentSchedule.WEEK_DAYS.get(w);
            if (group.startsWith(week)) {
                String rest = group.substring(week.length());
                int slot = StudentSchedule.TIME_SLOTS.indexOf(rest);
                if (slot >= 0) return w * StudentSchedule.TIME_SLOTS.size() + slot;
                return w * StudentSchedule.TIME_SLOTS.size();
            }
        }
        return Integer.MAX_VALUE - 1;
    }
}
