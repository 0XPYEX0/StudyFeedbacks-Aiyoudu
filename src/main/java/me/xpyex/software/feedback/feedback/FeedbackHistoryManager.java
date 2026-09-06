package me.xpyex.software.feedback.feedback;

import com.google.gson.JsonObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import me.xpyex.software.feedback.data.StudentFeedbackHistory;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.util.ConfigManager;
import me.xpyex.software.feedback.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 历史反馈管理器：读写 config/feedback/{真实姓名}_{studentId}.json。
 * <p>
 * 每个学生按日期追加保存若干条历史反馈；发送给 AI 前取"最近 N 条"合并。
 * 全局自定义的 N 值存放在 config/ai.json 的 historyCount 字段。
 */
public class FeedbackHistoryManager {
    private static final Logger log = LoggerFactory.getLogger(FeedbackHistoryManager.class.getSimpleName());
    /** 历史反馈存储目录 */
    public static final String DIR = "config/feedback/";
    /** 全局配置文件名（不含扩展名）：config/ai.json */
    public static final String GLOBAL_CONFIG = "ai";
    /** 全局配置中的字段名 */
    public static final String GLOBAL_KEY = "historyCount";
    /** 默认合并条数 */
    public static final int DEFAULT_COUNT = 3;

    private FeedbackHistoryManager() {
    }

    // ==================== 文件读写 ====================

    private static File fileFor(StudentInfo student) {
        return fileFor(student.getRealName(), student.getStudentId());
    }

    private static File fileFor(String realName, int studentId) {
        String name = sanitize(realName);
        // 姓名未知时也以 _id.json 命名，便于按后缀兜底扫描
        return new File(DIR + (name.isEmpty() ? "" : name + "_") + studentId + ".json");
    }

    private static String sanitize(String name) {
        if (name == null) return "";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }

    /** 读取某学生的历史反馈；无文件返回 null（不自动建空文件） */
    public static StudentFeedbackHistory load(StudentInfo student) {
        if (student == null) return null;
        return loadByStudentId(student.getStudentId());
    }

    /** 按学生 ID 读取（精确文件名 → 目录后缀兜底）；无文件返回 null */
    public static StudentFeedbackHistory loadByStudentId(int studentId) {
        File dir = new File(DIR);
        File[] files = dir.exists() ? dir.listFiles() : null;
        if (files != null) {
            String suffix = "_" + studentId + ".json";
            for (File f : files) {
                if (f.getName().endsWith(suffix)) {
                    return doLoad(f, studentId);
                }
            }
        }
        return null;
    }

    /** 读取或返回空历史（studentId 已设，realName 未知） */
    private static StudentFeedbackHistory loadOrEmpty(int studentId) {
        StudentFeedbackHistory history = loadByStudentId(studentId);
        if (history == null) {
            history = StudentFeedbackHistory.of().setStudentId(studentId);
        }
        if (history.getRecords() == null) history.setRecords(new ArrayList<>());
        return history;
    }

    private static StudentFeedbackHistory doLoad(File file, int studentId) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            StudentFeedbackHistory history = GsonUtil.parseObj(content, StudentFeedbackHistory.class);
            if (history == null) return null;
            history.setStudentId(studentId);
            if (history.getRecords() == null) history.setRecords(new ArrayList<>());
            history.getRecords().sort(Comparator.comparing(r -> r.getDate() == null ? "" : r.getDate()));
            return history;
        } catch (Exception e) {
            log.error("读取历史反馈失败：{}", file.getPath(), e);
            return null;
        }
    }

    /** 保存历史反馈；文件名以最新姓名生成，同时保留 _id 后缀可扫描 */
    public static boolean save(StudentFeedbackHistory history) {
        if (history == null) return false;
        File file = fileFor(history.getRealName(), history.getStudentId());
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                log.error("创建目录失败：{}", dir.getPath());
                return false;
            }
            Files.writeString(file.toPath(), GsonUtil.toJsonStr(history, true), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            log.error("保存历史反馈失败：{}", file.getPath(), e);
            return false;
        }
    }

    // ==================== 业务操作 ====================

    /** 追加一条历史反馈并保存；records 按日期升序排列 */
    public static boolean append(StudentInfo student, String date, String text) {
        if (student == null || text == null || text.isBlank()) return false;
        StudentFeedbackHistory history = loadOrEmpty(student.getStudentId());
        history.setRealName(student.getRealName());
        history.getRecords().add(StudentFeedbackHistory.Record.of()
                                          .setDate(date == null ? "" : date)
                                          .setText(text.trim()));
        history.getRecords().sort(Comparator.comparing(r -> r.getDate() == null ? "" : r.getDate()));
        return save(history);
    }

    /** 删除指定下标的记录（下标基于按日期升序后的列表），并保存 */
    public static boolean deleteAt(StudentInfo student, int index) {
        if (student == null || index < 0) return false;
        StudentFeedbackHistory history = loadByStudentId(student.getStudentId());
        if (history == null || index >= history.getRecords().size()) return false;
        history.getRecords().remove(index);
        history.setRealName(student.getRealName());
        return save(history);
    }

    /** 取最近 n 条（按日期从新到旧），n <= 0 时返回空 */
    public static List<StudentFeedbackHistory.Record> recent(int studentId, int n) {
        StudentFeedbackHistory history = loadByStudentId(studentId);
        if (history == null || n <= 0) return List.of();
        List<StudentFeedbackHistory.Record> list = new ArrayList<>(history.getRecords());
        list.sort(Comparator.comparing((StudentFeedbackHistory.Record r) -> r.getDate() == null ? "" : r.getDate()).reversed());
        return list.subList(0, Math.min(n, list.size()));
    }

    /** 把最近若干条历史反馈拼成可读块（带日期），供合并进 AI 文本 */
    public static String recentBlock(int studentId, int n) {
        List<StudentFeedbackHistory.Record> recent = recent(studentId, n);
        if (recent.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (StudentFeedbackHistory.Record r : recent) {
            sb.append("[").append(r.getDate() == null ? "未知日期" : r.getDate()).append("]\n")
              .append(r.getText() == null ? "" : r.getText().trim())
              .append("\n\n");
        }
        return sb.toString().trim();
    }

    // ==================== 全局条数设置 ====================

    /** 读取全局"最近 N 条"设置；未配置时用默认值 */
    public static int getGlobalCount() {
        JsonObject cfg = ConfigManager.loadConfig(GLOBAL_CONFIG);
        if (cfg.has(GLOBAL_KEY)) {
            try {
                return Math.max(0, cfg.get(GLOBAL_KEY).getAsInt());
            } catch (Exception e) {
                log.error("解析全局历史条数失败：", e);
            }
        }
        return DEFAULT_COUNT;
    }

    /** 保存全局"最近 N 条"设置到 config/ai.json */
    public static void setGlobalCount(int n) {
        JsonObject cfg = new JsonObject();
        cfg.addProperty(GLOBAL_KEY, Math.max(0, n));
        ConfigManager.saveConfig(GLOBAL_CONFIG, cfg);
        System.out.println("已保存全局设置：合并最近 " + Math.max(0, n) + " 条历史反馈");
    }
}
