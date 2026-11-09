package rain.admin.photo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 启动时补齐存量照片的网格缩略图（640px，幂等）；前台网格用它降加载重量，Lightbox 仍用 1600px。 */
@Configuration
public class ThumbsBackfillRunner {

    private static final Logger log = LoggerFactory.getLogger(ThumbsBackfillRunner.class);

    @Bean
    CommandLineRunner backfillThumbs(PhotoService photoService) {
        return args -> {
            int n = photoService.backfillThumbs();
            if (n > 0) log.info("缩略图回填完成：{} 张 → public/photos/thumbs/", n);
        };
    }
}
