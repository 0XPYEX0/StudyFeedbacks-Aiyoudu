package me.xpyex.software.feedback.packet.both;

import lombok.Data;
import me.xpyex.software.feedback.packet.in.AYDResponse;
import me.xpyex.software.feedback.util.GsonUtil;

@Data
public abstract class AYDPacket {
    public String toJsonStr(boolean pretty) {
        return GsonUtil.toJsonStr(this, pretty);
    }

    public AYDResponse sendToUrl() {
        throw new UnsupportedOperationException("该方法体未实现");
    }
}
