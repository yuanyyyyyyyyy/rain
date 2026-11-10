package rain.admin.publish;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import rain.admin.photo.Photo;
import rain.admin.photo.PhotoGroup;

/** photos.js 生成的结构与前台排版算法的契约：动态分组、每行 4 条、dims 分数表达式、头图 */
class PhotoDataGeneratorTest {

    private final PhotoDataGenerator gen = new PhotoDataGenerator();

    /** 分组来自库表（管理台可增删改排序），不再写死在生成器里 */
    private static final List<PhotoGroup> GROUPS = List.of(
            group("STREET", "STREET / 街道与城市", "电线、塔架、招牌与夜色里的街区。"),
            group("LIGHT", "LIGHT / 光线与氛围", "黄昏、蓝调时刻、灯与影。"),
            group("DAILY", "DAILY / 日常与人", "值得注意的日常瞬间。"));

    private static PhotoGroup group(String key, String name, String desc) {
        PhotoGroup g = new PhotoGroup();
        g.setGroupKey(key);
        g.setName(name);
        g.setDescription(desc);
        // 生成器按传入列表顺序输出，不读 sortNo（PublishService 已按 sortNo 排好）
        return g;
    }

    private Photo photo(String pid, String group, int sortNo, int w, int h, LocalDate date) {
        Photo p = new Photo();
        p.setPid(pid);
        p.setGroupName(group);
        p.setSortNo(sortNo);
        p.setWidth(w);
        p.setHeight(h);
        p.setShotDate(date);
        return p;
    }

    @Test
    void generatesFullFileWithGroupOrderAndFractionDims() {
        List<Photo> photos = List.of(
                photo("p01", "STREET", 1, 1600, 902, LocalDate.of(2023, 7, 24)),
                photo("p02", "LIGHT", 1, 902, 1600, LocalDate.of(2025, 10, 29)),
                photo("p03", "DAILY", 1, 1600, 902, null));
        Photo hero = photos.get(1);
        hero.setIsHero(true);
        String js = gen.generate(photos, hero, GROUPS);

        assertTrue(js.startsWith("// ============================================"));
        assertTrue(js.contains("由 rain 管理台发布生成"));
        assertTrue(js.contains("export const heroPhoto = { id: 'p02', date: '2025-10-29' }"));
        assertTrue(js.contains("name: 'STREET / 街道与城市'"));
        assertTrue(js.contains("name: 'LIGHT / 光线与氛围'"));
        assertTrue(js.contains("name: 'DAILY / 日常与人'"));
        // dims 保持 W / H 分数表达式（不是小数）
        assertTrue(js.contains("p01: 1600 / 902,"));
        assertTrue(js.contains("p02: 902 / 1600,"));
        // 空日期输出空串；caption 缺省时不带第三参
        assertTrue(js.contains("P('p03', ''),"));
        // 每行 4 条 plates（本例每组 1 条，行首缩进 6 空格）
        assertTrue(js.contains("      P('p01', '2023-07-24'),"));
        assertTrue(js.contains("const P = (id, date, cap) => ({ id, date, ...(cap ? { cap } : {}) })"));
        assertTrue(js.endsWith("export const plateNo = (idx) => `PLATE.${String(idx + 1).padStart(3, '0')}`\n"));
    }

    @Test
    void emitsCaptionWithEscaping() {
        Photo withCap = photo("p01", "STREET", 1, 1600, 902, LocalDate.of(2023, 7, 24));
        withCap.setCaption("雨后 It's fine, \\涩谷");
        String js = gen.generate(List.of(withCap), withCap, GROUPS);
        assertTrue(js.contains("P('p01', '2023-07-24', '雨后 It\\'s fine, \\\\涩谷'),"));
    }

    @Test
    void wrapsFourPlatesPerLine() {
        var photos = new java.util.ArrayList<Photo>();
        for (int i = 1; i <= 6; i++) {
            photos.add(photo("p0" + i, "STREET", i, 1600, 902, null));
        }
        String js = gen.generate(photos, photos.get(0), GROUPS);
        String platesBlock = js.substring(js.indexOf("plates: ["), js.indexOf("],"));
        long lines = platesBlock.lines().filter(l -> l.contains("P('p")).count();
        assertEquals(2, lines); // 6 条 → 每行 4 条 → 2 行
    }

    @Test
    void refusesWithoutHero() {
        List<Photo> photos = List.of(photo("p01", "STREET", 1, 1600, 902, null));
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> gen.generate(photos, null, GROUPS));
        assertTrue(e.getMessage().contains("头图"));
    }

    @Test
    void usesGroupsFromDatabase() {
        PhotoGroup custom = new PhotoGroup();
        custom.setGroupKey("PORTRAIT");
        custom.setName("PORTRAIT / 人像");
        custom.setDescription("It's 一段含引号的描述");
        custom.setSortNo(1);
        Photo p = photo("p01", "PORTRAIT", 1, 1600, 902, null);
        String js = gen.generate(List.of(p), p, List.of(custom));
        assertTrue(js.contains("name: 'PORTRAIT / 人像'"));
        assertTrue(js.contains("desc: 'It\\'s 一段含引号的描述'"));
        assertFalse(js.contains("STREET / 街道与城市"));
    }

    @Test
    void refusesOrphanGroupInsteadOfDroppingPhotos() {
        Photo p = photo("p01", "GONE", 1, 1600, 902, null);
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> gen.generate(List.of(p), p, GROUPS));
        assertTrue(e.getMessage().contains("已不存在的分组"));
        assertTrue(e.getMessage().contains("GONE"));
    }
}
