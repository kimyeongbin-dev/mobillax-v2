package MobilLax.Domain.Cart;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class CartController {

    @GetMapping("/cart")
    public String cart() {
        return "cart"; // cart.html 템플릿
    }
}
