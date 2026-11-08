package rain.admin.publish;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import rain.admin.photo.Photo;

/**
 * photo 表 → 前台 src/data/photos.js 全文。
 * 分组名/描述为固定文案（管理台不开放建组，新增分组需改此处）；dims 保持 W / H 分数表达式（排版精度友好）。
 * 生成是确定性的：同数据产出同一文件（无时间戳），发布时间看 publish_log。
 */
@Component
public class PhotoDataGenerator {

    private static final Map<String, String[]> GROUP_META = Map.of(
            "STREET", new String[] {"STREET / 街道与城市", "电线、塔架、招牌与夜色里的街区——城市基础设施的几何秩序。"},
            "LIGHT", new String[] {"LIGHT / 光线与氛围", "黄昏、蓝调时刻、灯与影——一天中颜色最接近克莱因蓝的那些分钟。"},
            "DAILY", new String[] {"DAILY / 日常与人", "雪地的猫、夜市的灯、做手工的人——值得注意的日常瞬间。"});

    private static final List<String> GROUP_ORDER = List.of("STREET", "LIGHT", "DAILY");

    public String generate(List<Photo> photos, Photo hero) {
        if (hero == null) throw new IllegalStateException("没有头图：请先在管理台设置头图再发布");
        // dims 输出顺序对齐手写版：按组序（STREET→LIGHT→DAILY）+ 组内排序
        List<Photo> ordered = new java.util.ArrayList<>();
        for (String key : GROUP_ORDER) {
            photos.stream().filter(p -> key.equals(p.getGroupName()))
                    .sorted(java.util.Comparator.comparing(Photo::getSortNo).thenComparing(Photo::getId))
                    .forEach(ordered::add);
        }
        StringBuilder sb = new StringBuilder();
        // 不写生成时间戳：保证同数据产出同一文件（可重现构建，git diff 干净）；发布时间看 publish_log
        sb.append("// ============================================\n");
        sb.append("// 摄影页数据 — 由 rain 管理台发布生成\n");
        sb.append("// 原图 → 网页版对应关系见 docs/素材/照片清单-photos-manifest-20261108.json\n");
        sb.append("// 分组依据 2026-11-08 拼图审阅：街道与城市 / 光线与氛围 / 日常与人\n");
        sb.append("// ============================================\n\n");
        sb.append("const P = (id, date) => ({ id, date })\n\n");
        sb.append("export const heroPhoto = { id: '").append(hero.getPid())
                .append("', date: '").append(hero.getShotDate() == null ? "" : hero.getShotDate()).append("' }\n\n");
        sb.append("export const photoGroups = [\n");

        for (String key : GROUP_ORDER) {
            String[] meta = GROUP_META.get(key);
            List<Photo> group = photos.stream().filter(p -> key.equals(p.getGroupName()))
                    .sorted(java.util.Comparator.comparing(Photo::getSortNo).thenComparing(Photo::getId)).toList();
            sb.append("  {\n    name: '").append(meta[0]).append("',\n");
            sb.append("    desc: '").append(meta[1]).append("',\n");
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

    private static String plateEntry(Photo p) {
        String date = p.getShotDate() == null ? "" : p.getShotDate().toString();
        return "P('" + p.getPid() + "', '" + date + "'),";
    }
}
