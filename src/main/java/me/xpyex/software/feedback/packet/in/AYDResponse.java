package me.xpyex.software.feedback.packet.in;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.util.GsonUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class AYDResponse {
    private int code;
    private String message;
    private JsonElement data;

    public static AYDResponse of(String json) {
        return GsonUtil.parseObj(json, AYDResponse.class);
    }

    public boolean isSuccess() {
        return code == 200 && "成功".equals(message);
    }

    public JsonObject getDataAsJsonObject() {
        return dataIsJsonObject() ? data.getAsJsonObject() : new JsonObject();
    }

    public boolean dataIsJsonObject() {
        return data.isJsonObject();
    }
}
