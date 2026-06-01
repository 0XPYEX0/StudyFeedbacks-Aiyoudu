package me.xpyex.software.feedback.packet.both;

import lombok.Data;
import me.xpyex.software.feedback.util.GsonUtil;

@Data
public class AYDPacket {
    public String toJsonStr(boolean pretty) {
        return GsonUtil.toJsonStr(this, pretty);
    }
}
