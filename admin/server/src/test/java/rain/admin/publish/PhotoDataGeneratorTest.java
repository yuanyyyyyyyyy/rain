package rain.admin.publish;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import rain.admin.photo.Photo;
import rain.admin.photo.PhotoGroup;

/** content/photos.json 的结构契约：动态分组、组内顺序、图注原样保留、dims 宽高比、头图与孤儿保护 */
class PhotoDataGeneratorTest {

    private final PhotoDataGenerator gen = new PhotoDataGenerator();
    private static final ObjectMapper M = new ObjectMapper();

    private static final List<PhotoGroup> GROUPS = List.of(
            group("STREET", "STREET / 街道与城市", "电线、塔架、招牌与夜色里的街区。"),
            group("LIGHT", "LIGHT / 光线与氛围", "黄昏、蓝调时刻、灯与影。"),
            group("DAILY", "DAILY / 日常与人", "值得注意的日常瞬间。"));

    private static PhotoGroup group(String key, String name, String desc) {
        PhotoGroup g = new PhotoGroup();
        g.setGroupKey(key);
        g.setName(name);
        g.setDescription(desc);
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

    private static JsonNode parse(String json) {
        try {
            return M.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("生成物不是合法 JSON: " + json, e);
        }
    }

    @Test
    void generatesPureJsonWithGroupOrderAndDims() {
        List<Photo> photos = List.of(
                photo("p01", "STREET", 1, 1600, 902, LocalDate.of(2023, 7, 24)),
                photo("p02", "LIGHT", 2, 902, 1600, LocalDate.of(2025, 10, 29)),
                photo("p03", "DAILY", 3, 1600, 902, null));
        Photo hero = photos.get(1);
        hero.setIsHero(true);
        String json = gen.generate(photos, hero, GROUPS);

        JsonNode root = parse(json);
        assertEquals("p02", root.path("heroPhoto").path("id").asText());
        assertEquals("2025-10-29", root.path("heroPhoto").path("date").asText());
        JsonNode groups = root.path("photoGroups");
        assertEquals(3, groups.size());
        assertEquals("STREET / 街道与城市", groups.get(0).path("name").asText());
        assertEquals("LIGHT / 光线与氛围", groups.get(1).path("name").asText());
        assertEquals("DAILY / 日常与人", groups.get(2).path("name").asText());
        // plates 是对象数组；无图注时不含 cap 键，空日期是空串
        assertEquals("p01", groups.get(0).path("plates").get(0).path("id").asText());
        assertEquals("2023-07-24", groups.get(0).path("plates").get(0).path("date").asText());
        assertFalse(groups.get(0).path("plates").get(0).has("cap"));
        assertEquals("", groups.get(2).path("plates").get(0).path("date").asText());
        // 宽高比是数值（JSON 不能写表达式），与 JS 里的 1600 / 902 等价
        assertEquals(1600.0 / 902.0, root.path("dims").path("p01").asDouble(), 1e-12);
        assertEquals(902.0 / 1600.0, root.path("dims").path("p02").asDouble(), 1e-12);
        assertFalse(json.contains("export const"));
    }

    @Test
    void keepsCaptionVerbatimWithoutManualEscaping() {
        Photo withCap = photo("p01", "STREET", 1, 1600, 902, LocalDate.of(2023, 7, 24));
        withCap.setCaption("雨后 It's fine, \\涩谷\n第二行");
        JsonNode root = parse(gen.generate(List.of(withCap), withCap, GROUPS));
        // 引号、反斜杠、换行由 Jackson 转义，解析后原样回来——不再依赖手写 escape()
        assertEquals("雨后 It's fine, \\涩谷\n第二行",
                root.path("photoGroups").get(0).path("plates").get(0).path("cap").asText());
    }

    @Test
    void platesFollowGroupSortOrder() {
        var photos = new ArrayList<Photo>();
        for (int i = 1; i <= 6; i++) photos.add(photo("p0" + i, "STREET", i, 1600, 902, null));
        JsonNode root = parse(gen.generate(photos, photos.get(0), GROUPS));
        JsonNode plates = root.path("photoGroups").get(0).path("plates");
        assertEquals(6, plates.size());
        for (int i = 0; i < 6; i++) assertEquals("p0" + (i + 1), plates.get(i).path("id").asText());
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
        JsonNode root = parse(gen.generate(List.of(p), p, List.of(custom)));
        assertEquals("PORTRAIT / 人像", root.path("photoGroups").get(0).path("name").asText());
        assertEquals("It's 一段含引号的描述", root.path("photoGroups").get(0).path("desc").asText());
        assertFalse(root.toString().contains("STREET / 街道与城市"));
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
