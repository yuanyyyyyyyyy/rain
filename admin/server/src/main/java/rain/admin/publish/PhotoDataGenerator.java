package rain.admin.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import rain.admin.photo.Photo;
import rain.admin.photo.PhotoGroup;

/**
 * photo + photo_group 表 → 前台 content/photos.json。
 * 内容与代码分层：本生成器只写纯数据 JSON，转义交给 Jackson；
 * 派生逻辑（flatPlates / plateNo）留在 src/data/photos.js 适配层，不随发布改动。
 * 生成是确定性的：同数据产出同一文件（无时间戳），发布时间看 publish_log。
 */
@Component
public class PhotoDataGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Comparator<Photo> BY_ORDER =
            Comparator.comparing(Photo::getSortNo, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Photo::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    public String generate(List<Photo> photos, Photo hero, List<PhotoGroup> groups) {
        if (hero == null) throw new IllegalStateException("没有头图：请先在管理台设置头图再发布");
        if (groups == null || groups.isEmpty()) {
            throw new IllegalStateException("没有分组：请先在管理台「分组管理」创建分组再发布");
        }

        Set<String> keys = new LinkedHashSet<>();
        for (PhotoGroup g : groups) keys.add(g.getGroupKey());
        List<String> orphanKeys = photos.stream().map(Photo::getGroupName)
                .filter(k -> k == null || !keys.contains(k)).distinct().toList();
        if (!orphanKeys.isEmpty()) {
            throw new IllegalStateException("有照片属于已不存在的分组：" + orphanKeys
                    + "，请先在管理台把这些照片移到有效分组再发布");
        }

        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode heroNode = root.putObject("heroPhoto");
        heroNode.put("id", hero.getPid());
        heroNode.put("date", hero.getShotDate() == null ? "" : hero.getShotDate().toString());

        ArrayNode groupArr = root.putArray("photoGroups");
        ObjectNode dims = root.putObject("dims");
        for (PhotoGroup g : groups) {
            ObjectNode gn = groupArr.addObject();
            gn.put("name", g.getName());
            gn.put("desc", g.getDescription() == null ? "" : g.getDescription());
            ArrayNode plates = gn.putArray("plates");
            List<Photo> group = new ArrayList<>(photos.stream()
                    .filter(p -> g.getGroupKey().equals(p.getGroupName()))
                    .sorted(BY_ORDER).toList());
            for (Photo p : group) {
                ObjectNode pn = plates.addObject();
                pn.put("id", p.getPid());
                pn.put("date", p.getShotDate() == null ? "" : p.getShotDate().toString());
                if (p.getCaption() != null && !p.getCaption().isBlank()) pn.put("cap", p.getCaption());
                int h = p.getHeight() == null ? 0 : p.getHeight();
                int w = p.getWidth() == null ? 0 : p.getWidth();
                dims.put(p.getPid(), h > 0 ? (double) w / h : 0.0);
            }
        }
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n";
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("照片数据 JSON 序列化失败", e);
        }
    }
}
