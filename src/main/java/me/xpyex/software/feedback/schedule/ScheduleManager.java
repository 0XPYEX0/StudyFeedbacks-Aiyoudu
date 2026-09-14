package me.xpyex.software.feedback.schedule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.xpyex.software.feedback.data.StudentSchedule;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 课时配置管理器：读写 config/schedule/{真实姓名}_{studentId}.json。
 * <p>
 * 只维护"学生有没有来"：
 * <ul>
 *     <li>来了 → 记入 attendedDates（加课/补录同此）</li>
 *     <li>没来 → 记入 leaveDates（请假/缺勤）</li>
 *     <li>二者互斥，同一天只会在其中一边</li>
 * </ul>
 * 自动补记：只把 {@code lastModify}（上次修改日）之后、今天之前的排课日补进 attended，
 * 这样一周没开软件也不用手动补；{@code lastModify} 之前的历史保持原样、绝不回溯改写，
 * 以免中途调课时把旧时段/新时段的课往前补算成"多算课时"。
 */
public class ScheduleManager {
    /**
     * 课时配置根目录
     */
    public static final String DIR = "data/schedule/";
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Logger log = LoggerFactory.getLogger(ScheduleManager.class.getSimpleName());

    // ==================== 文件读写（{真实姓名}_{studentId}.json） ====================

    private static String sanitize(String name) {
        if (name == null) return "";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }

    private static File fileFor(String realName, int studentId) {
        String name = sanitize(realName);
        return new File(DIR + (name.isEmpty() ? "" : name + "_") + studentId + ".json");
    }

    /**
     * 在目录中定位某学生的课时配置文件（按 "_studentId.json" 后缀）；不存在返回 null
     */
    private static File locate(int studentId) {
        File dir = new File(DIR);
        File[] files = dir.exists() ? dir.listFiles() : null;
        if (files == null) return null;
        String suffix = "_" + studentId + ".json";
        for (File f : files) {
            if (f.getName().endsWith(suffix)) return f;
        }
        return null;
    }

    public static boolean exists(int studentId) {
        return locate(studentId) != null;
    }

    /**
     * 读取课时配置；文件不存在或解析失败返回 null
     */
    public static StudentSchedule load(int studentId) {
        File file = locate(studentId);
        if (file == null) return null;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            StudentSchedule schedule = GsonUtil.parseObj(content, StudentSchedule.class);
            if (schedule == null) return null;
            schedule.setStudentId(studentId);
            // 若文件内没存姓名，从文件名前缀推断（张三_442428.json -> 张三）
            if (schedule.getRealName() == null || schedule.getRealName().isBlank()) {
                String name = file.getName();
                int idx = name.lastIndexOf('_');
                if (idx > 0) {
                    schedule.setRealName(name.substring(0, idx));
                }
            }
            normalize(schedule);
            return schedule;
        } catch (Exception e) {
            log.error("读取课时配置失败：{}", file.getPath(), e);
            return null;
        }
    }

    /**
     * 保存课时配置（目录不存在自动创建；顺手删除旧版 {studentId}.json 测试残留）
     */
    public static boolean save(StudentSchedule schedule) {
        if (schedule == null) return false;
        normalize(schedule);
        File file = fileFor(schedule.getRealName(), schedule.getStudentId());
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                log.error("创建目录失败：{}", dir.getPath());
                return false;
            }
            Files.writeString(file.toPath(), GsonUtil.toJsonStr(schedule, true), StandardCharsets.UTF_8);
            // 旧命名 {studentId}.json 不再使用，删除以免残留
            File legacy = new File(DIR + schedule.getStudentId() + ".json");
            if (legacy.exists() && !legacy.equals(file)) {
                Files.deleteIfExists(legacy.toPath());
            }
            return true;
        } catch (Exception e) {
            log.error("保存课时配置失败：{}", file.getPath(), e);
            return false;
        }
    }

    /**
     * 字段归一化：列表非空、去重、升序；保证"来了/没来"互斥（以 attended 优先）
     */
    private static void normalize(StudentSchedule s) {
        s.setPeriods(s.getPeriods() == null ? new ArrayList<>() : s.getPeriods());
        List<String> attended = normalizeUnique(s.getAttendedDates());
        List<String> leave = normalizeUnique(s.getLeaveDates());
        leave.removeAll(attended); // 同一天不会既来又没来
        s.setAttendedDates(attended);
        s.setLeaveDates(leave);
        if (s.getTotalLessons() < 0) s.setTotalLessons(0);
    }

    private static List<String> normalizeUnique(List<String> raw) {
        Set<String> set = new LinkedHashSet<>();
        if (raw != null) {
            for (String d : raw) {
                if (d != null && !d.isBlank()) set.add(d.trim());
            }
        }
        List<String> list = new ArrayList<>(set);
        list.sort(String::compareTo);
        return list;
    }

    // ==================== 日期辅助 ====================

    public static String toIso(LocalDate date) {
        return ISO.format(date);
    }

    public static LocalDate parseIso(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDate.parse(text.trim(), ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * 中文星期 -> DayOfWeek（周一~周日），无法识别返回 null
     */
    public static DayOfWeek toDayOfWeek(String weekChinese) {
        if (weekChinese == null) return null;
        int idx = StudentSchedule.WEEK_DAYS.indexOf(weekChinese.trim());
        if (idx < 0) return null;
        return DayOfWeek.of(idx + 1); // DayOfWeek.MONDAY=1
    }

    // ==================== 自动补记（仅 lastModify 之后） ====================

    /**
     * 自动补记并保存：把 lastModify 之后、今天之前的排课日补进 attended（跳过已标"没来"的日期）。
     * 只做新增，绝不删除或改写任何已有记录。
     */
    public static void autoFillAndSave(StudentSchedule s) {
        if (s == null) return;
        autoFill(s, LocalDate.now());
        save(s);
    }

    /**
     * 对一批学生自动补记（顺带写入最新姓名，保证文件名正确）
     */
    public static void recomputeAllStudents(Collection<? extends StudentInfo> students) {
        if (students == null) return;
        for (StudentInfo student : students) {
            if (student == null) continue;
            StudentSchedule s = load(student.getStudentId());
            if (s != null) {
                s.setRealName(student.getRealName());
                autoFillAndSave(s);
            }
        }
    }

    /**
     * 自动补记：扫描 [lastModify, 今天) 区间内所有排课日（不早于 startDate），
     * 只要当天没有标"没来"就补进 attended。lastModify 为空时不做任何自动补记。
     */
    public static void autoFill(StudentSchedule s, LocalDate today) {
        if (s == null || today == null) return;
        LocalDate anchor = parseIso(s.getLastModify());
        if (anchor == null) return; // 没有"上次修改日"就不自动推算
        LocalDate start = parseIso(s.getStartDate());

        List<String> attended = normalizeUnique(s.getAttendedDates());
        Set<String> attendedSet = new LinkedHashSet<>(attended);
        Set<String> leave = new LinkedHashSet<>(normalizeUnique(s.getLeaveDates()));

        for (LocalDate d = anchor; d.isBefore(today); d = d.plusDays(1)) {
            if (start != null && d.isBefore(start)) continue;
            if (!s.isScheduledOn(d)) continue;      // 不是排课日
            String iso = toIso(d);
            if (leave.contains(iso)) continue;       // 已标"没来"
            attendedSet.add(iso);                    // 只新增
        }

        List<String> result = new ArrayList<>(attendedSet);
        result.sort(String::compareTo);
        s.setAttendedDates(result);
    }

    // ==================== 来了 / 没来（过去、今天、未来都可点选） ====================

    /**
     * 标记"来了"：加入 attendedDates，并从 leaveDates 中移除（互斥）。重复点选即取消该标记。
     *
     * @return 是否发生了变更
     */
    public static boolean toggleAttended(StudentSchedule s, LocalDate date) {
        if (s == null || date == null) return false;
        String iso = toIso(date);
        if (s.getAttendedDates() == null) s.setAttendedDates(new ArrayList<>());
        if (s.getLeaveDates() == null) s.setLeaveDates(new ArrayList<>());
        boolean already = s.getAttendedDates().remove(iso);
        if (!already) {
            s.getAttendedDates().add(iso);
            s.getLeaveDates().remove(iso);
            s.getAttendedDates().sort(String::compareTo);
        }
        return true;
    }

    /**
     * 标记"没来"：加入 leaveDates，并从 attendedDates 中移除（互斥）。重复点选即取消该标记。
     *
     * @return 是否发生了变更
     */
    public static boolean toggleLeave(StudentSchedule s, LocalDate date) {
        if (s == null || date == null) return false;
        String iso = toIso(date);
        if (s.getAttendedDates() == null) s.setAttendedDates(new ArrayList<>());
        if (s.getLeaveDates() == null) s.setLeaveDates(new ArrayList<>());
        boolean already = s.getLeaveDates().remove(iso);
        if (!already) {
            s.getLeaveDates().add(iso);
            s.getAttendedDates().remove(iso);
            s.getLeaveDates().sort(String::compareTo);
        }
        return true;
    }
}
