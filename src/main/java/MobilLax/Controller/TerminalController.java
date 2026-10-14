/**
 * TerminalController.java
 *
 * ✅ 파일 목적: 시외버스 터미널 목록을 조회하는 외부 API를 호출하는 HTTP GET 엔드포인트를 제공합니다.
 *              클라이언트는 터미널 이름을 입력하여 관련 데이터를 XML로 받아볼 수 있습니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Controller;

import MobilLax.Service.TerminalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * ✅ 클래스 설명:
 * 시외버스 터미널 정보를 조회하는 REST API 컨트롤러입니다.
 * 터미널 이름을 기준으로 외부 공공 API를 호출하고, XML 응답을 그대로 반환합니다.
 * WebFlux 기반이므로 비동기 방식으로 처리됩니다.
 */
@RestController
public class TerminalController {

    private final TerminalService terminalService;

    /**
     * 생성자 주입
     * @param terminalService 터미널 정보 조회를 위한 서비스 클래스
     */
    public TerminalController(TerminalService terminalService) {
        this.terminalService = terminalService;
    }

    /**
     * ✅ 터미널 목록 조회 API
     *
     * @param name 검색할 터미널 이름 (예: "동서울")
     * @return Mono<String> 공공데이터포털에서 수신한 XML 형식의 터미널 정보
     *
     * 예시 호출:
     * GET /api/terminals?name=동서울
     * Response-Type: application/xml
     */
    @GetMapping(value = "/api/terminals", produces = "application/xml")
    public Mono<String> getTerminalList(@RequestParam String name) {
        return terminalService.getTerminalList(name);
    }
}
