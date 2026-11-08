package rain.admin.stats;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface VisitLogRepo extends JpaRepository<VisitLog, Long> {

    /** 按天聚合 PV（近 N 天，含当天） */
    @Query("select function('date_format', v.createdAt, '%Y-%m-%d') as d, count(v) from VisitLog v " +
            "where v.createdAt >= :since group by d order by d desc")
    List<Object[]> countByDaySince(LocalDateTime since);

    /** 热门页面（全部历史 Top N） */
    @Query("select v.path, count(v) from VisitLog v group by v.path order by count(v) desc")
    List<Object[]> topPaths();

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    /** UV：按 ipHash 去重 */
    @Query("select count(distinct v.ipHash) from VisitLog v where v.createdAt >= :since")
    long countUvSince(LocalDateTime since);
}
