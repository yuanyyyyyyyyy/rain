package rain.admin.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 照片静态资源：/photos/** 直接映射到前台 public/photos/（后端上传产物落盘处）。
 * 管理台(5175)的 img 因此无需 Vite 代理即可显示；前台构建也读同一目录。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String frontendPublicDir;

    public WebConfig(@Value("${app.storage.frontend-public-dir}") String frontendPublicDir) {
        this.frontendPublicDir = frontendPublicDir;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(frontendPublicDir, "photos").toUri().toString();
        registry.addResourceHandler("/photos/**").addResourceLocations(location);
    }
}
