package MobilLax.Domain.Place;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 매칭 캐시 정리: TMAP 대중교통 API 약관상 TMAP 에서 얻은 데이터(장소 이름·좌표)는 저장 후 24시간 이상 쓸 수 없다
 * - 매시 정각마다 24시간이 지난 TMAP↔TAGO 쌍을 지운다 (TAGO 기준표는 공공데이터라 남긴다)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceMatchCleanup {

    private final PlaceMatchRepository matchRepository;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpired() {
        long deleted = matchRepository.deleteByMatchedAtBefore(LocalDateTime.now().minus(PlaceMatch.RETENTION));
        if (deleted > 0) {
            log.info("24시간이 지난 장소 매칭 캐시 {}건 삭제", deleted);
        }
    }
}
