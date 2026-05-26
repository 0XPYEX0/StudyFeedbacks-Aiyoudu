package me.xpyex.software.feedback.util;

import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 通用配置管理器
 * 支持 JSON 格式配置文件的读取和写入
 */
public class ConfigManager {
    private static final Logger log = LoggerFactory.getLogger(ConfigManager.class);

    /**
     * 加载配置文件并返回 JsonObject
     *
     * @param configName 配置文件名（不含扩展名），如 "print" 表示 config/print.json
     * @return JsonObject，如果文件不存在或解析失败则返回空的 JsonObject
     */
    public static JsonObject loadConfig(String configName) {
        String configFilePath = "config/" + configName + ".json";
        File configFile = new File(configFilePath);

        if (!configFile.exists()) {
            log.debug("配置文件 {} 不存在，将返回空配置", configFilePath);
            return new JsonObject();
        }

        try {
            String content = Files.readString(Paths.get(configFilePath), StandardCharsets.UTF_8);
            JsonObject configData = GsonUtil.parseJsonObj(content);
            log.debug("配置文件加载成功：{}", configFilePath);
            return configData;
        } catch (IOException e) {
            log.error("读取配置文件失败：{}", configFilePath, e);
            return new JsonObject();
        } catch (Exception e) {
            log.error("解析配置文件时发生异常：{}", configFilePath, e);
            return new JsonObject();
        }
    }

    /**
     * 保存配置到文件
     *
     * @param configName 配置文件名（不含扩展名），如 "print" 表示 config/print.json
     * @param configData 要保存的 JsonObject
     * @return 是否保存成功
     */
    public static boolean saveConfig(String configName, JsonObject configData) {
        String configFilePath = "config/" + configName + ".json";
        try {
            // 确保目录存在
            File configFile = new File(configFilePath);
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            // 写入文件
            String jsonStr = GsonUtil.toJsonStr(configData, true);
            Files.writeString(Paths.get(configFilePath), jsonStr, StandardCharsets.UTF_8);
            log.debug("配置文件保存成功：{}", configFilePath);
            return true;
        } catch (IOException e) {
            log.error("保存配置文件失败：{}", configFilePath, e);
            return false;
        }
    }

    /**
     * 从 JsonObject 中获取整数配置值
     *
     * @param configData   JsonObject
     * @param key          配置键
     * @param defaultValue 默认值
     * @return 配置值
     */
    public static int getInt(JsonObject configData, String key, int defaultValue) {
        if (configData.has(key)) {
            return configData.get(key).getAsInt();
        }
        return defaultValue;
    }

    /**
     * 向 JsonObject 中设置整数配置值
     *
     * @param configData JsonObject
     * @param key        配置键
     * @param value      配置值
     */
    public static void setInt(JsonObject configData, String key, int value) {
        configData.addProperty(key, value);
    }
}
