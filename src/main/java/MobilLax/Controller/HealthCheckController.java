/**
 * HealthCheckController.java
 *
 * ✅ 파일 목적: 시스템 상태 점검용 컨트롤러로, DB 연결 상태를 확인하는 헬스 체크(Health Check) API를 제공합니다.
 *              운영 중 DB 연결 문제가 있는지 간단히 확인할 수 있습니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * ✅ 클래스 설명:
 * DB 연결 상태를 확인할 수 있는 REST API를 제공하는 헬스 체크용 컨트롤러입니다.
 * 운영 서버에서 DB와의 연결 유무를 빠르게 확인하고, 알림 시스템 등과 연동할 수 있습니다.
 */
@RestController
@RequestMapping("/health")
public class HealthCheckController {

    @Autowired
    private DataSource dataSource;

    /**
     * ✅ DB 연결 헬스 체크 API
     *
     * DB 커넥션을 시도하고 연결 여부를 확인하여 성공 또는 실패 메시지를 반환합니다.
     * 커넥션이 유효하면 "DB 연결 성공!" 반환, 실패하거나 예외 발생 시 메시지 포함하여 오류 반환.
     *
     * @return 연결 상태에 따른 텍스트 메시지
     *
     * 예시 호출:
     * GET /health/db
     * → "DB 연결 성공!" 또는 "DB 연결 실패!", "DB 연결 오류: (에러 메시지)"
     */
    @GetMapping("/db")
    public String checkDbConnection() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1) ? "DB 연결 성공!" : "DB 연결 실패!";
        } catch (SQLException e) {
            return "DB 연결 오류: " + e.getMessage();
        }
    }
}
