package rain.admin.photo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 分组 CRUD。key 是 photo.group_name 的外键，创建后不可改；改名只改展示名与描述。 */
@Service
public class PhotoGroupService {

    private static final Pattern KEY = Pattern.compile("^[A-Z][A-Z0-9_]{0,19}$");

    private final PhotoGroupRepo groupRepo;
    private final PhotoRepo photoRepo;

    public PhotoGroupService(PhotoGroupRepo groupRepo, PhotoRepo photoRepo) {
        this.groupRepo = groupRepo;
        this.photoRepo = photoRepo;
    }

    public record GroupInput(String groupKey, String name, String description) {}
    public record GroupUpdate(String name, String description) {}

    public List<PhotoGroup> list() {
        return groupRepo.findAllByOrderBySortNoAscIdAsc();
    }

    /** 上传/编辑/拖拽时统一校验：分组必须在库里存在 */
    public void requireGroup(String groupKey) {
        if (groupKey == null || !groupRepo.existsByGroupKey(groupKey)) {
            throw new IllegalArgumentException("分组不存在：" + groupKey + "（请在「分组管理」中先创建）");
        }
    }

    @Transactional
    public PhotoGroup create(GroupInput in) {
        String key = normalizeKey(in.groupKey());
        if (groupRepo.existsByGroupKey(key)) throw new IllegalArgumentException("分组 key 已存在：" + key);
        if (in.name() == null || in.name().isBlank()) throw new IllegalArgumentException("显示名不能为空");
        PhotoGroup g = new PhotoGroup();
        g.setGroupKey(key);
        g.setName(in.name().trim());
        g.setDescription(trimToNull(in.description()));
        g.setSortNo(list().stream().mapToInt(PhotoGroup::getSortNo).max().orElse(0) + 1);
        return groupRepo.save(g);
    }

    @Transactional
    public PhotoGroup update(Long id, GroupUpdate in) {
        PhotoGroup g = groupRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分组不存在：" + id));
        if (in.name() == null || in.name().isBlank()) throw new IllegalArgumentException("显示名不能为空");
        g.setName(in.name().trim());
        g.setDescription(trimToNull(in.description()));
        return groupRepo.save(g);
    }

    /** 非空分组禁止删除：照片是用户资产，绝不级联删图，也不静默移到别的组 */
    @Transactional
    public void delete(Long id) {
        PhotoGroup g = groupRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("分组不存在：" + id));
        long n = photoRepo.countByGroupName(g.getGroupKey());
        if (n > 0) {
            throw new IllegalArgumentException("「" + g.getName() + "」内还有 " + n + " 张照片，请先把照片拖到其它分组再删除");
        }
        groupRepo.delete(g);
    }

    /** 整体重排：必须提交全部分组 id，避免漏传导致顺序错乱 */
    @Transactional
    public List<PhotoGroup> reorder(List<Long> ids) {
        List<PhotoGroup> all = list();
        if (ids == null || ids.size() != all.size()) {
            throw new IllegalArgumentException("排序参数必须包含全部分组");
        }
        Set<Long> existing = all.stream().map(PhotoGroup::getId).collect(Collectors.toSet());
        if (!new HashSet<>(ids).equals(existing)) {
            throw new IllegalArgumentException("排序参数包含不存在的分组");
        }
        for (PhotoGroup g : all) g.setSortNo(ids.indexOf(g.getId()) + 1);
        return groupRepo.saveAll(all);
    }

    private static String normalizeKey(String raw) {
        if (raw == null) throw new IllegalArgumentException("分组 key 不能为空");
        String key = raw.trim().toUpperCase();
        if (!KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("分组 key 只能是大写字母/数字/下划线，且以字母开头（≤20 位）");
        }
        return key;
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
