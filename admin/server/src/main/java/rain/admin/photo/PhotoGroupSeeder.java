package rain.admin.photo;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 分组初始数据 + 存量照片日期来源回填。
 * 只在 photo_group 为空时插入三组（与现有 photos.js 文案一致）；用户删光分组后重启会重新兜底，
 * 避免前台 photoGroups 为空导致摄影页白屏。
 */
@Configuration
public class PhotoGroupSeeder {

    @Bean
    CommandLineRunner seedPhotoGroups(PhotoGroupRepo groupRepo, PhotoRepo photoRepo) {
        return args -> {
            if (groupRepo.count() == 0) {
                groupRepo.saveAll(List.of(
                        group("STREET", "STREET / 街道与城市",
                                "电线、塔架、招牌与夜色里的街区——城市基础设施的几何秩序。", 1),
                        group("LIGHT", "LIGHT / 光线与氛围",
                                "黄昏、蓝调时刻、灯与影——一天中颜色最接近克莱因蓝的那些分钟。", 2),
                        group("DAILY", "DAILY / 日常与人",
                                "雪地的猫、夜市的灯、做手工的人——值得注意的日常瞬间。", 3)));
            }
            // 存量照片：来源未知，但保留当前展示值为「手填」候选，随时可切回，不丢已有日期
            for (Photo p : photoRepo.findAll()) {
                if (p.getDateSource() != null) continue;
                p.setUploadDate(p.getCreatedAt() == null ? LocalDate.now() : p.getCreatedAt().toLocalDate());
                p.setManualDate(p.getShotDate());
                p.setDateSource("legacy");
                photoRepo.save(p);
            }
        };
    }

    private static PhotoGroup group(String key, String name, String desc, int sortNo) {
        PhotoGroup g = new PhotoGroup();
        g.setGroupKey(key);
        g.setName(name);
        g.setDescription(desc);
        g.setSortNo(sortNo);
        return g;
    }
}
