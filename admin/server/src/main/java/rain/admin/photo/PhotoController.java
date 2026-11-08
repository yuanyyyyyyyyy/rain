package rain.admin.photo;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
public class PhotoController {

    private final PhotoService service;
    private final PhotoRepo repo;

    public PhotoController(PhotoService service, PhotoRepo repo) {
        this.service = service;
        this.repo = repo;
    }

    public record UploadParams(@NotBlank String groupName, LocalDate shotDate) {}

    /** 全量列表（按分组、组内排序号） */
    @GetMapping
    public List<Photo> list() {
        return repo.findAllByOrderByGroupNameAscSortNoAscIdAsc();
    }

    /** 上传：multipart 字段 file + groupName（必填）+ shotDate（可空） */
    @PostMapping("/upload")
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
                                    @RequestParam("groupName") String groupName,
                                    @RequestParam(value = "shotDate", required = false) LocalDate shotDate) {
        try {
            PhotoService.UploadResult r = service.upload(file, shotDate, groupName);
            Photo p = r.photo();
            return ResponseEntity.ok(Map.of(
                    "id", p.getId(),
                    "pid", p.getPid(),
                    "width", p.getWidth(),
                    "height", p.getHeight(),
                    "snippet", r.snippet()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "上传失败：" + e.getMessage()));
        }
    }
}
