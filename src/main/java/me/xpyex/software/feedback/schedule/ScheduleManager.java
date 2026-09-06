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
import me.xpyex.software.feedback.data.StudentSchedule.SchedulePeriod;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 课时配置管理器：读写 config/schedule/{真实姓名}_{studentId}.json。
 * <p>
 * 一个学生一周可有多个上课时段（如 周五晚上 + 周六上午）。
 * <p>
 * 已上课的自动推算原则（过去不会被自动改写）：
 * <ul>
 *     <li>历史 attendedDates（早于今天）一旦生成即冻结，仅在你在月历上点选请假/加课时增删；</li>
 *     <li>从未改过时段的排课，从 startDate 起按各时段每周固定日补齐；</li>
 *     <li>中途修改时段后，写入 updateDate=修改当天，<b>新时段自修改日起算、不去自动改动此前的旧记录</b>，
 *         旧时段的已上课保持原样，过去的误差你在月历里手动修正即可（点选过去日期补请假/加课）；</li>
 *     <li>已上课 = (各时段自生效日起每周固定日到昨天) - 请假 ∪ 加课(≤今天)</li>
 * </ul>
 */
public class ScheduleManager {
    private static final Logger log = LoggerFactory.getLogger(ScheduleManager.class.getSimpleName());
    /** 课时配置根目录 */
    public static final String DIR = "config/schedule/";
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private ScheduleManager() {
        // 工具类，禁止实例化
    }

    // ==================== 文件读写（{真实姓名}_{studentId}.json） ====================

    private static String sanitize(String name) {
        if (name == null) return "";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }

    private static File fileFor(String realName, int studentId) {
        String name = sanitize(realName);
        return new File(DIR + (name.isEmpty() ? "" : name + "_") + studentId + ".json");
    }

    /** 在目录中定位某学生的课时配置文件（按 "_studentId.json" 后缀）；不存在返回 null */
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

    /** 读取课时配置；文件不存在或解析失败返回 null */
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

    /** 保存课时配置（目录不存在自动创建；顺手删除旧版 {studentId}.json 测试残留） */
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

    /** 字段归一化：列表非空、去重、升序 */
    private static void normalize(StudentSchedule s) {
        s.setPeriods(s.getPeriods() == null ? new ArrayList<>() : s.getPeriods());
        s.setLeaveDates(normalizeUnique(s.getLeaveDates()));
        s.setExtraDates(normalizeUnique(s.getExtraDates()));
        s.setAttendedDates(normalizeUnique(s.getAttendedDates()));
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

    /** 中文星期 -> DayOfWeek（周一~周日），无法识别返回 null */
    public static DayOfWeek toDayOfWeek(String weekChinese) {
        if (weekChinese == null) return null;
        int idx = StudentSchedule.WEEK_DAYS.indexOf(weekChinese.trim());
        if (idx < 0) return null;
        return DayOfWeek.of(idx + 1); // DayOfWeek.MONDAY=1
    }

    // ==================== attendedDates 重算 ====================

    /**
     * 按今天重算 attendedDates 并保存。历史冻结、只追加不回溯，
     * 请假剔除、加课补录。
     */
    public static void recomputeAndSave(StudentSchedule s) {
        if (s == null) return;
        recompute(s, LocalDate.now());
        save(s);
    }

    /** 对一批学生重算并保存（自动带上最新姓名以写对文件名） */
    public static void recomputeAllStudents(Collection<? extends StudentInfo> students) {
        if (students == null) return;
        for (StudentInfo student : students) {
            if (student == null) continue;
            StudentSchedule s = load(student.getStudentId());
            if (s != null) {
                s.setRealName(student.getRealName());
                recomputeAndSave(s);
            }
        }
    }

    private static void recompute(StudentSchedule s, LocalDate today) {
        Set<String> leave = new LinkedHashSet<>(s.getLeaveDates() == null ? List.of() : s.getLeaveDates());

        // 1) 历史（早于今天）冻结保留，请假剔除
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String iso : normalizeUnique(s.getAttendedDates())) {
            LocalDate d = parseIso(iso);
            if (d != null && d.isBefore(today) && !leave.contains(iso)) {
                result.add(iso);
            }
        }

        LocalDate start = parseIso(s.getStartDate());
        // 生效日：中途改过时段则从 updateDate 起算，否则从 startDate 补齐
        LocalDate effFrom = start;
        LocalDate update = parseIso(s.getUpdateDate());
        if (update != null && update.isAfter(effFrom)) {
            effFrom = update;
        }

        // 2) 各时段自生效日起到昨天的每周固定上课日
        if (start != null) {
            for (SchedulePeriod p : s.getRealPeriods()) {
                DayOfWeek dow = toDayOfWeek(p.getWeeklyDay());
                if (dow == null) continue;
                for (LocalDate d = effFrom; d.isBefore(today); d = d.plusDays(1)) {
                    if (d.getDayOfWeek() == dow && !leave.contains(toIso(d))) {
                        result.add(toIso(d));
                    }
                }
            }
        }

        // 3) 加课补录（≤今天、不早于开始日期、不在请假）
        if (s.getExtraDates() != null) {
            for (String iso : s.getExtraDates()) {
                LocalDate d = parseIso(iso);
                if (d != null && !d.isAfter(today) && (start == null || !d.isBefore(start))
                        && !leave.contains(iso)) {
                    result.add(iso);
                }
            }
        }

        List<String> list = new ArrayList<>(result);
        list.sort(String::compareTo);
        s.setAttendedDates(list);
    }

    // ==================== 请假 / 加课（过去日期也可修改） ====================

    /**
     * 切换请假日期。过去日期也可点选，用于纠正"学生实际没来但系统按排课记成有来"。
     * 请假与加课互斥：标为请假时自动从加课中移除该日。
     *
     * @return 是否发生了变更
     */
    public static boolean toggleLeave(StudentSchedule s, LocalDate date) {
        if (s == null || date == null) return false;
        String iso = toIso(date);
        if (s.getLeaveDates() == null) s.setLeaveDates(new ArrayList<>());
        boolean already = s.getLeaveDates().remove(iso);
        if (!already) {
            s.getLeaveDates().add(iso);
            if (s.getExtraDates() != null) s.getExtraDates().remove(iso);
            s.getLeaveDates().sort(String::compareTo);
        }
        return true;
    }

    /**
     * 切换加课日期。过去日期也可点选，用于补录"学生实际来了但不在固定排课里"。
     * 加课与请假互斥：标为加课时自动从请假中移除该日。
     *
     * @return 是否发生了变更
     */
    public static boolean toggleExtra(StudentSchedule s, LocalDate date) {
        if (s == null || date == null) return false;
        String iso = toIso(date);
        if (s.getExtraDates() == null) s.setExtraDates(new ArrayList<>());
        boolean already = s.getExtraDates().remove(iso);
        if (!already) {
            s.getExtraDates().add(iso);
            if (s.getLeaveDates() != null) s.getLeaveDates().remove(iso);
            s.getExtraDates().sort(String::compareTo);
        }
        return true;
    }
}
