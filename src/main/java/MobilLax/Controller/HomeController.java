/**
 * HomeController.java
 *
 * ✅ 파일 목적: 애플리케이션의 기본 루트 경로(‘/’, ‘/home’) 요청을 처리하며, 메인 홈 화면 뷰를 반환합니다.
 *              별도 로직 없이 templates/home.html 페이지로 연결됩니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * ✅ 클래스 설명:
 * 루트 경로 또는 "/home" 경로로 접근 시 home 화면을 반환하는 간단한 컨트롤러입니다.
 * 주로 인사말, 소개, 로그인 링크 등을 포함하는 메인 페이지로 사용됩니다.
 */
@Controller
public class HomeController {

  /**
   * ✅ 홈 화면 렌더링
   *
   * "/" 또는 "/home" 요청이 들어오면 templates/home.html 페이지를 렌더링합니다.
   *
   * @param model View에 데이터를 전달할 때 사용하는 Model 객체 (현재 사용 안 함)
   * @return home 페이지 (resources/templates/home.html)
   */
  @GetMapping({"/", "/home"})
  public String home(Model model, HttpServletRequest request) {
    model.addAttribute("remoteUser", request.getRemoteUser());
    return "home";
  }
}
