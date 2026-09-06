package me.xpyex.software.feedback.study;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import me.xpyex.software.feedback.data.StudyConfig;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学案配置管理器：读写 config/study/{真实姓名}_{studentId}.json。
 * <p>
 * 文件名含真实姓名便于人工识别；当学生改名后仍能通过 "_studentId.json" 后缀兜底找到原配置。
 */
public class StudyConfigManager {
    private static final Logger log = LoggerFactory.getLogger(StudyConfigManager.class.getSimpleName());
    /** 学案配置根目录 */
    public static final String DIR = "config/study/";

    private StudyConfigManager() {
    }

    /** 依据当前学生信息生成配置文件名（清理 Windows 非法字符） */
    public static File fileOf(StudentInfo student) {
        String name = sanitize(student.getRealName());
        return new File(DIR + name + "_" + student.getStudentId() + ".json");
    }

    public static File fileBySuffix(int studentId) {
        return new File(DIR + "_" + studentId + ".json");
    }

    private static String sanitize(String name) {
        if (name == null) name = "未命名";
        return name.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
    }

    /** 读取某学生的学案配置；不存在返回 null */
    public static StudyConfig load(StudentInfo student) {
        if (student == null) return null;
        File exact = fileOf(student);
        if (!exact.exists()) {
            // 学生改名后：扫目录用后缀兜底
            File dir = new File(DIR);
            File[] files = dir.exists() ? dir.listFiles() : null;
            if (files != null) {
                String suffix = "_" + student.getStudentId() + ".json";
                for (File f : files) {
                    if (f.getName().endsWith(suffix)) {
                        return doLoad(f, student);
                    }
                }
            }
            return null;
        }
        return doLoad(exact, student);
    }

    private static StudyConfig doLoad(File file, StudentInfo student) {
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            StudyConfig config = GsonUtil.parseObj(content, StudyConfig.class);
            if (config == null) return null;
            if (config.getTypeCountMap() == null) config.setTypeCountMap(new java.util.LinkedHashMap<>());
            // 以最新学生信息覆盖 realName / studentId
            config.setStudentId(student.getStudentId());
            config.setRealName(student.getRealName());
            return config;
        } catch (Exception e) {
            log.error("读取学案配置失败：{}", file.getPath(), e);
            return null;
        }
    }

    /** 保存学案配置（自动以最新学生信息修正文件名对应的 id/姓名） */
    public static boolean save(StudentInfo student, StudyConfig config) {
        if (student == null || config == null) return false;
        config.setStudentId(student.getStudentId());
        config.setRealName(student.getRealName());
        return save(config);
    }

    /** 按配置自身的 id/姓名落盘 */
    public static boolean save(StudyConfig config) {
        if (config == null) return false;
        File file = new File(DIR + sanitize(config.getRealName()) + "_" + config.getStudentId() + ".json");
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.exists() && !dir.mkdirs()) {
                log.error("创建目录失败：{}", dir.getPath());
                return false;
            }
            Files.writeString(file.toPath(), GsonUtil.toJsonStr(config, true), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            log.error("保存学案配置失败：{}", file.getPath(), e);
            return false;
        }
    }
}
