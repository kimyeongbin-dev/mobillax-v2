package MobilLax.Controller;

import MobilLax.Model.RouteResponse;
import MobilLax.Service.RouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/route")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @GetMapping("/detail")
    public RouteResponse getRoute(@RequestParam String type) {
        return routeService.getRoute(type);
    }
}