package me.xpyex.software.feedback.packet.out;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import me.xpyex.software.feedback.util.AiyouduUtil;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class PrintMerge {
    public static final String url = AiyouduUtil.apiUrl + "student/study/printMerge";
    private final List<Integer> printIds = new ArrayList<>();

    public PrintMerge add(int id) {
        printIds.add(id);
        return this;
    }

    public PrintMerge addAll(Collection<Integer> collection) {
        printIds.addAll(collection);
        return this;
    }
}
