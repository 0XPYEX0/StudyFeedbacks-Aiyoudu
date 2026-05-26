package me.xpyex.software.feedback.packet.in;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class SinglePanel {
    private String title;
    private String content;
    private String amount = "";
    private String rate = "";
    private String difficulty = "";
}
