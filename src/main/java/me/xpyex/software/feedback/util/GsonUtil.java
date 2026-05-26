package me.xpyex.software.feedback.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import lombok.Getter;

public class GsonUtil {
    @Getter()
    private static final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
    @Getter()
    private static final Gson prettyGson = gson.newBuilder().setPrettyPrinting().create();

    public static JsonObject parseJsonObj(String json) {
        return gson.fromJson(json, JsonObject.class);
    }

    public static String toJsonStr(Object obj, boolean pretty) {
        return (pretty ? prettyGson : gson).toJson(obj);
    }

    public static <T> T parseObj(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }
}
