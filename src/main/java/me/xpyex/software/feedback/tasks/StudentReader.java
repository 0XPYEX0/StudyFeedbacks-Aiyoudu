package me.xpyex.software.feedback.tasks;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.xpyex.software.feedback.packet.both.StudentInfo;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.packet.in.DataInfo;
import me.xpyex.software.feedback.packet.in.GroupInfo;
import me.xpyex.software.feedback.packet.out.SearchStudents;
import me.xpyex.software.feedback.util.AiyouduUtil;
import me.xpyex.software.feedback.util.GsonUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 学生信息读取器
 * 负责从系统读取所有有效学生（有 group 值）并保存在静态 Map 中
 */
public class StudentReader {
    private static final Logger log = LoggerFactory.getLogger(StudentReader.class.getSimpleName());

    /**
     * 存储所有有效学生的静态 Map
     * Key: 学生 ID
     * Value: 学生信息对象
     */
    private static final Map<Integer, StudentInfo> studentMap = new ConcurrentHashMap<>();
    private static final Map<String, Integer> groupIdByName = new ConcurrentHashMap<>();
    private static final String dataInfoUrl = AiyouduUtil.apiUrl + "organiztion/student/dataInfo?studentId={$id}";

    /**
     * 启动学生读取流程
     */
    public static void start() {
        log.info("========================================");
        log.info("   学生信息读取器启动");
        log.info("========================================");

        try {
            // 步骤 1: 检查 Token 是否存在
            if (!TokenGetter.hasToken()) {
                log.error("❌ 未检测到有效 Token！");
                log.error("请先执行【1】getToken 获取 Token");
                return;
            }

            // 步骤 2: 获取所有有 group 值的学生并存入 Map
            loadValidStudentsToMap();

            log.info("========================================");
            log.info("   学生读取完成！共保存 {} 个学生", studentMap.size());
            log.info("========================================");

            // 步骤 3: 检查并创建 print.json 配置文件
            checkAndCreatePrintConfig();

        } catch (Exception e) {
            log.error("读取学生过程中发生错误：", e);
        }
    }


    /**
     * 步骤 2: 获取所有有 group 值的学生并存入静态 Map
     */
    private static void loadValidStudentsToMap() {
        log.info(">>> 正在获取所有学生列表...");

        studentMap.clear();

        for (StudentInfo student : getAllStudents()) {
            if (student.getGroup() != null
                    && !student.getGroup().trim().isEmpty()
                    && !student.getGroup().contains("非正式")
                    && !student.getGroup().contains("体验")) {
                studentMap.put(student.getStudentId(), student);
                log.info("保存学生：{} [ID:{}] - 分组：{}",
                    student.getRealName(), student.getStudentId(), student.getGroup());
            } else {
                log.debug("跳过无分组的学生：{} [ID:{}]",
                    student.getRealName(), student.getStudentId());
            }
        }

        log.info("共找到 {} 个学生，其中 {} 个学生有 group 值并已保存",
            studentMap.size(), studentMap.size());
    }

    /**
     * 获取所有已保存的学生
     *
     * @return 学生 Map（只读视图）
     */
    public static Map<Integer, StudentInfo> copyStudents() {
        return Map.copyOf(studentMap);
    }

    /**
     * 根据学生 ID 获取单个学生信息
     *
     * @param studentId 学生 ID
     * @return 学生信息，如果不存在则返回 null
     */
    public static StudentInfo getStudentById(int studentId) {
        return studentMap.get(studentId);
    }

    /**
     * 获取已保存的学生数量
     *
     * @return 学生数量
     */
    public static int getStudentCount() {
        return studentMap.size();
    }

    /**
     * 检查是否已有学生数据
     *
     * @return 如果 Map 中有学生数据则返回 true
     */
    public static boolean hasStudents() {
        return !studentMap.isEmpty();
    }

    /**
     * 步骤 3: 检查并创建 print.json 配置文件
     */
    private static void checkAndCreatePrintConfig() {
        File configFile = new File("config/print.json");

        if (!configFile.exists()) {
            log.info(">>> config/print.json 不存在，正在创建默认配置文件...");

            try {
                // 创建父目录（如果不存在）
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }

                // 创建默认配置内容
                String defaultContent = "{\"name\": 2, \"name2\": 2}";

                // 写入文件
                try (FileWriter writer = new FileWriter(configFile)) {
                    writer.write(defaultContent);
                }

                log.info("√ 默认配置文件已创建：config/print.json");
                log.info("  内容：{}", defaultContent);

            } catch (IOException e) {
                log.error("创建配置文件失败：", e);
            }
        } else {
            log.debug("config/print.json 已存在，跳过创建");
        }
    }

    public static DataInfo getStudentData(int id) {
        AYDResponse obj = AYDResponse.of(AiyouduUtil.getUrlWithToken(dataInfoUrl.replace("{$id}", "" + id)));
        if (obj.isSuccess() && "成功".equals(obj.getMessage())) {
            return GsonUtil.getGson().fromJson(obj.getData(), DataInfo.class);
        }
        return null;
    }

    public static List<StudentInfo> getAllStudents() {
        ArrayList<StudentInfo> list = new ArrayList<>();
        AYDResponse obj = AYDResponse.of(AiyouduUtil.postUrlWithToken(SearchStudents.url, SearchStudents.of().setSize(100).toJsonStr(false)));
        if (obj.isSuccess()) {
            JsonArray students = obj.getDataAsJsonObject().getAsJsonArray("records");
            for (JsonElement student : students) {
                StudentInfo info = GsonUtil.getGson().fromJson(student, StudentInfo.class);
                AiyouduUtil.log.info("{} {} {}", info.getStudentId(), info.getRealName(), info.getGroup());
                list.add(
                    info.setDataInfo(getStudentData(info.getStudentId()))
                        .setGroupId(getGroupId(info.getGroup()))
                );
                try {
                    Thread.sleep(1500);  //等1.5秒
                } catch (InterruptedException e) {
                    Thread.currentThread().stop();
                    return list;
                }
            }
        }
        return list;
    }

    public static void freshGroups() {
        groupIdByName.clear();
        AYDResponse response = AYDResponse.of(AiyouduUtil.getUrlWithToken(GroupInfo.url));
        if (response.isSuccess()) {
            response.getData().getAsJsonArray().asList().stream()
                .map(e -> GsonUtil.getGson().fromJson(e, GroupInfo.class))
                .forEach(group -> groupIdByName.put(group.getGroupName(), group.getGroupId()));
        }
    }

    public static int getGroupId(String name) {
        if (groupIdByName.isEmpty()) freshGroups();
        return groupIdByName.getOrDefault(name, -1);
    }
}
