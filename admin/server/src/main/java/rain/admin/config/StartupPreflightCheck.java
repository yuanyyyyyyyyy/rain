package rain.admin.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 启动预检（2026-11-10 审计）：发布链路的前提条件在启动时核一遍，缺什么当场 WARN，
 * 而不是等用户点「一键发布」时才炸出一个模糊报错。只告警不阻断启动——检查项缺失不代表
 * 内容编辑 / 收件箱不可用（例如没装 git 只影响发布推送一步）。
 */
@Configuration
public class StartupPreflightCheck {

    private static final Logger log = LoggerFactory.getLogger(StartupPreflightCheck.class);

    @Bean
    CommandLineRunner preflightCheck(@Value("${app.storage.frontend-public-dir}") String frontendPublicDir,
                                     @Value("${app.jwt.secret}") String jwtSecret) {
        return args -> {
            Path root = Path.of(frontendPublicDir).getParent();
            checkGit(root);
            checkNpm();
            checkStorage(frontendPublicDir);
            checkJwtSecret(jwtSecret);
        };
    }

    /** 发布要 git commit/push：仓库不可用 = 发布必失败 */
    private void checkGit(Path root) {
        if (root == null) return;
        try {
            Process p = new ProcessBuilder("git", "rev-parse", "--is-inside-work-tree")
                    .directory(root.toFile()).redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                warn("git 查询超时（发布推送会失败）");
                return;
            }
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.exitValue() != 0 || !"true".equals(out)) {
                warn("前台目录不是 git 仓库（" + root + "），发布无法 commit/push");
            }
        } catch (Exception e) {
            warn("git 不可用（" + e.getClass().getSimpleName() + "），发布无法 commit/push——发布链路会走到构建为止");
        }
    }

    /** 发布要 npm run build：Windows 下 npm 是 .cmd，需经 cmd /c 调起（与 PublishService.runBuild 同口径） */
    private void checkNpm() {
        String[] cmd = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? new String[] {"cmd", "/c", "npm", "--version"}
                : new String[] {"npm", "--version"};
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                warn("npm 查询超时（发布构建会失败）");
                return;
            }
            if (p.exitValue() != 0) warn("npm 不可用，发布无法执行前台构建");
        } catch (Exception e) {
            warn("npm 不可用（" + e.getClass().getSimpleName() + "），发布无法执行前台构建");
        }
    }

    /** 照片/截图/内容文件都落在前台 public 下：目录不可写 = 上传与发布写回都会失败 */
    private void checkStorage(String frontendPublicDir) {
        Path dir = Path.of(frontendPublicDir);
        if (!Files.isDirectory(dir)) {
            warn("app.storage.frontend-public-dir 不存在：" + frontendPublicDir + "（照片上传/发布写回会失败）");
        } else if (!Files.isWritable(dir)) {
            warn("前台 public 目录不可写：" + frontendPublicDir);
        }
    }

    /**
     * jjwt hmacShaKeyFor 按密钥长度选算法：≥64 字节 HS512、≥48 HS384、≥32 HS256。
     * README 口径是 HS384——secret 32-47 字节时不报错但降级 HS256，提前说明避免口径误会。
     */
    private void checkJwtSecret(String secret) {
        int len = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (len < 32) {
            warn("JWT secret 少于 32 字节（当前 " + len + "），签名强度不足，请换 openssl rand -hex 32 生成");
        } else if (len < 48) {
            log.warn("JWT secret {} 字节：实际使用 HS256（README 口径 HS384 需要 ≥48 字节）", len);
        }
    }

    private void warn(String msg) {
        log.warn("[预检] {}", msg);
    }
}
