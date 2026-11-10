package rain.admin.publish;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import rain.admin.photo.Photo;
import rain.admin.photo.PhotoGroup;

/**
 * photo + photo_group 表 → 前台 src/data/photos.js 全文。
 * 分组的展示名/描述/顺序全部来自管理台「分组管理」，不再写死；dims 保持 W / H 分数表达式（排版精度友好）。
 * 生成是确定性的：同数据产出同一文件（无时间戳），发布时间看 publish_log。
 */
@Component
public class PhotoDataGenerator {

    private static final Comparator<Photo> BY_ORDER =
            Comparator.comparing(Photo::getSortNo).thenComparing(Photo::getId);

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

        // dims 输出顺序对齐前台排版：按分组顺序 + 组内排序
        List<Photo> ordered = new ArrayList<>();
        for (PhotoGroup g : groups) {
            photos.stream().filter(p -> g.getGroupKey().equals(p.getGroupName()))
                    .sorted(BY_ORDER).forEach(ordered::add);
        }

        StringBuilder sb = new StringBuilder();
        // 不写生成时间戳：保证同数据产出同一文件（可重现构建，git diff 干净）；发布时间看 publish_log
        sb.append("// ============================================\n");
        sb.append("// 摄影页数据 — 由 rain 管理台发布生成\n");
        sb.append("// 原图 → 网页版对应关系见 docs/素材/照片清单-photos-manifest-20261108.json\n");
        sb.append("// 分组名称、描述与顺序由管理台「02 照片 → 分组管理」维护\n");
        sb.append("// ============================================\n\n");
        sb.append("const P = (id, date, cap) => ({ id, date, ...(cap ? { cap } : {}) })\n\n");
        sb.append("export const heroPhoto = { id: '").append(hero.getPid())
                .append("', date: '").append(hero.getShotDate() == null ? "" : hero.getShotDate()).append("' }\n\n");
        sb.append("export const photoGroups = [\n");

        for (PhotoGroup g : groups) {
            List<Photo> group = photos.stream().filter(p -> g.getGroupKey().equals(p.getGroupName()))
                    .sorted(BY_ORDER).toList();
            sb.append("  {\n    name: '").append(jsQuote(g.getName())).append("',\n");
            sb.append("    desc: '").append(jsQuote(g.getDescription() == null ? "" : g.getDescription())).append("',\n");
            sb.append("    plates: [\n");
            for (int i = 0; i < group.size(); i++) {
                if (i % 4 == 0) sb.append("      "); // 行首缩进仅在新行出现
                sb.append(plateEntry(group.get(i)));
                sb.append((i + 1) % 4 == 0 || i == group.size() - 1 ? "\n" : " ");
            }
            sb.append("    ],\n  },\n");
        }
        sb.append("]\n\n");
        sb.append("// 全部照片平铺（Lightbox 翻页用，按分组顺序）\n");
        sb.append("export const flatPlates = photoGroups.flatMap((g) => g.plates)\n\n");
        sb.append("// 宽高比 w/h（发布时按 photo 表实际尺寸生成），对齐行排版用\n");
        sb.append("export const dims = {\n");
        for (int i = 0; i < ordered.size(); i++) {
            Photo p = ordered.get(i);
            if (i % 5 == 0) sb.append("  ");
            sb.append(p.getPid()).append(": ").append(p.getWidth()).append(" / ").append(p.getHeight()).append(",");
            sb.append((i + 1) % 5 == 0 || i == ordered.size() - 1 ? "\n" : " ");
        }
        sb.append("}\n\n");
        sb.append("export const plateNo = (idx) => `PLATE.${String(idx + 1).padStart(3, '0')}`\n");
        return sb.toString();
    }

    /** 分组文案由用户填写，必须转义成合法的单引号 JS 字符串 */
    private static String jsQuote(String s) {
        return s.replace("\\", "\\\\").replace("'", "\\'")
                .replace("\r", " ").replace("\n", " ");
    }

    private static String plateEntry(Photo p) {
        String date = p.getShotDate() == null ? "" : p.getShotDate().toString();
        // 图注可空；引号转义保证生成物是合法 JS（' 与 \ 之外的其他字符原样输出）
        if (p.getCaption() == null || p.getCaption().isBlank()) {
            return "P('" + p.getPid() + "', '" + date + "'),";
        }
        String cap = p.getCaption().replace("\\", "\\\\").replace("'", "\\'");
        return "P('" + p.getPid() + "', '" + date + "', '" + cap + "'),";
    }
}
