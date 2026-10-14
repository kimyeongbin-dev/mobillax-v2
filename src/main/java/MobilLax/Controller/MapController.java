package MobilLax.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MapController {

    @GetMapping("/route.html")
    public String showLoginForm() {
        return "route"; // templates/route.html
    }
}
