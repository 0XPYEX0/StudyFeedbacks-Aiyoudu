package me.xpyex.software.feedback.schedule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.experimental.ExtensionMethod;
import me.xpyex.software.feedback.data.StudentSchedule;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.in.StudentSignIn;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.GsonUtil;
import me.xpyex.software.feedback.util.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 课时配置管理器：读写 data/schedule/{真实姓名}_{studentId}.json。
 * <p>
 * 只保存「当前计费开始日期 + 总次数」；考勤（上课/请假）与月历已移除。
 */
@ExtensionMethod({AiyouduUtil.class, GsonUtil.class})
public class ScheduleManager {
    /**
     * 课时配置根目录
     */
    public static final String DIR = "data/schedule/";
    public static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Logger log = LoggerFactory.getLogger(ScheduleManager.class.getSimpleName());
    @Getter
    private static final Map<String, Integer> studentCount = new ConcurrentHashMap<>();  // 学生ID+日期, 剩余课时      避免一直远程get


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
            if (schedule.getTotalLessons() < 0) schedule.setTotalLessons(0);
            return schedule;
        } catch (Exception e) {
            log.error("读取课时配置失败：{}", file.getPath(), e);
            return null;
        }
    }

    /** 保存课时配置（目录不存在自动创建） */
    public static boolean save(StudentSchedule schedule) {
        if (schedule == null) return false;
        if (schedule.getTotalLessons() < 0) schedule.setTotalLessons(0);
        File file = fileFor(schedule.getRealName(), schedule.getStudentId());
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                log.error("创建目录失败：{}", dir.getPath());
                return false;
            }
            Files.writeString(file.toPath(), GsonUtil.toJsonStr(schedule, true), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            log.error("保存课时配置失败：{}", file.getPath(), e);
            return false;
        }
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

    // ==================== 剩余课次（数据来源待接入） ====================

    /**
     * 已上课次数。
     * <p>
     * 目前占位返回 0，因此「剩余课次」暂时等于总次数。
     *
     * @param schedule 学生课时配置
     * @return 已上课次数
     */
    public static int attendedCount(StudentSchedule schedule) {
        if (schedule.getStartDate() == null) return 0;

        LocalDate chargeDate = parseIso(schedule.getStartDate());
        return getStudyDaysAfterDate(schedule, chargeDate);
    }

    /**
     * 计算自从某日期后学生学习(打卡)了几天
     *
     * @param schedule 学生的信息相关
     * @param date 从该日期开始算
     * @return 上了几次课，或没上课为0
     */
    public static int getStudyDaysAfterDate(StudentSchedule schedule, LocalDate date) {
        String key = schedule.getStudentId() + " " + toIso(date);
        if (!studentCount.containsKey(key)) {
            Set<String> dates = new HashSet<>();
            LocalDate firstMonth = date.withDayOfMonth(1);  // 收费那个月开始算
            LocalDate lastMonth = LocalDate.now().withDayOfMonth(1);  // 到现在这个月的为止

            if (firstMonth.isAfter(LocalDate.now())) return 0;

            LocalDate computingMonth = firstMonth;
            while (!computingMonth.isAfter(lastMonth)) {  // 只要计算中的这个月份不在当月之后，就算
                TimeUtil.sleep(50);  // 每一次缓50ms，免得AYD死掉了
                AYDResponse response = StudentSignIn.url
                                           .replace("{$student}", schedule.getStudentId() + "")
                                           .replace("{$monthFirstDay}", toIso(computingMonth))  // 计算某个月的打卡天数
                                           .getUrlWithToken().toResponse();
                if (response.isSuccess() && response.dataIsJsonObject()) {
                    StudentSignIn studentSignIn = response.getDataAsJsonObject().parseObj(StudentSignIn.class);
                    if (studentSignIn.getSignINList() == null) {
                        computingMonth = computingMonth.plusMonths(1);  // 加一个月上去计算
                        continue;  // 检测的这个月没有打卡，就不用算了
                    }
                    studentSignIn.getSignINList().stream()
                        .filter(signIn -> signIn.getBronzeCount() + signIn.getSilverCount() + signIn.getGoldCount() > 0)  // 有打卡的日期
                        .filter(signIn -> !date.isAfter(parseIso(signIn.getDay())))  // 只要收费日期不在打卡期前面就可以，即可以在后面或者等同
                        .forEach(signIn -> dates.add(signIn.getDay()));
                }

                computingMonth = computingMonth.plusMonths(1);  // 加一个月上去计算
            }
            studentCount.put(key, dates.size());
        }
        return studentCount.get(key);
    }

    /** 剩余课次 = 总次数 − 已上课次数（已上课次数来源见 {@link #attendedCount}） */
    public static int remainingLessons(StudentSchedule schedule) {
        if (schedule == null) return 0;
        return schedule.getTotalLessons() - attendedCount(schedule);
    }
}
