/**
 * BusApiProperties.java
 *
 * ✅ 파일 목적: application.properties 또는 application.yml에 정의된
 *              시외버스 API 관련 설정값들을 Lombok 기반으로 바인딩하는 설정 클래스입니다.
 *
 * 예시 설정:
 * bus.api.key=발급받은API인증키
 * bus.api.url.ctycode=https://api/ctycode
 * bus.api.url.terminal=https://api/terminal
 * bus.api.url.schedule=https://api/schedule
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ✅ 클래스 설명:
 * `bus.api` 접두어를 가진 설정 항목들을 자동으로 바인딩합니다.
 * - API 인증 키
 * - 도시 코드 / 터미널 목록 / 시간표 조회를 위한 URL 세트
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bus.api")
public class BusApiProperties {

    // 공공데이터포털에서 발급받은 인증 키
    private String key;

    // 시외버스 관련 각 API의 URL을 포함하는 내부 구성 객체
    private Url url;

    public String getKey() {
        this.key = key;
        return key;
    }

    /**
     * ✅ 내부 클래스: bus.api.url 하위 항목을 구성
     */
    @Getter
    @Setter
    public static class Url {
        private String ctycode;   // 도시 코드 조회 URL
        private String terminal;  // 터미널 목록 조회 URL
        private String schedule;  // 시간표 조회 URL
    }
}