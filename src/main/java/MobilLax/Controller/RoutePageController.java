package MobilLax.Controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoutePageController {
    @GetMapping("/route")
    public String routepage() {
        return "route";
    }
}
