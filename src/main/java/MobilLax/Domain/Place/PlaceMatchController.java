package MobilLax.Domain.Place;

import MobilLax.Config.InputRules;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * TMAP 장거리 구간의 출발·도착 장소를 TAGO 역·터미널·공항과 맞추는 API
 * - 상세 화면이 장거리 구간마다 호출한다. v1.6 에서 TAGO 편 조회에 이 결과(TAGO ID)를 쓴다
 */
@Slf4j
@RestController
@RequestMapping("/api/place")
@RequiredArgsConstructor
public class PlaceMatchController {

    /** 한 번에 맞출 수 있는 장소 수 (경로 하나의 장거리 구간 출발·도착) */
    static final int MAX_POINTS = 20;

    private final PlaceMatchService matchService;

    /**
     * @param mode  TMAP 구간 mode (TRAIN, EXPRESSBUS, AIRPLANE)
     * @param type  TMAP 구간 type (EXPRESSBUS 의 22 시외, 23 고속 구분)
     * @param route TMAP 노선명 (type 이 없을 때 고속·시외 구분 보조)
     * @param name  TMAP 장소 이름 (구간 start.name / end.name)
     */
    public record MatchPoint(
            @NotNull @Pattern(regexp = InputRules.PAYABLE_MODE) String mode,
            @Min(0) @Max(999) Integer type,
            @Size(max = 60) @Pattern(regexp = InputRules.PLAIN_TEXT) String route,
            @NotBlank @Size(max = 40) @Pattern(regexp = InputRules.PLAIN_TEXT) String name,
            // 국내 좌표만 (TMAP 이 주는 장소). 이름·좌표는 캐시 키로 저장되므로 길이·범위를 제한한다 (S10)
            @DecimalMin("33.0") @DecimalMax("39.0") double lat,
            @DecimalMin("124.0") @DecimalMax("132.0") double lon) {
    }

    public record MatchRequest(@NotEmpty @Size(max = MAX_POINTS) List<@NotNull @Valid MatchPoint> points) {
    }

    public record MatchResponse(String kind, String kindLabel, PlaceMatchService.MatchResult result) {
    }

    @PostMapping("/match")
    public ResponseEntity<List<MatchResponse>> match(@Valid @RequestBody MatchRequest request) {
        List<MatchPoint> points = request == null || request.points() == null ? List.of() : request.points();
        if (points.isEmpty() || points.size() > MAX_POINTS) {
            return ResponseEntity.badRequest().build();
        }

        List<MatchResponse> responses = new ArrayList<>();
        for (MatchPoint point : points) {
            TransitKind kind = TransitKind.fromTmapLeg(point.mode(), point.type(), point.route());
            if (kind == null || point.name() == null || point.name().isBlank()) {
                responses.add(new MatchResponse(null, null, null));
                continue;
            }
            PlaceMatchService.MatchResult result;
            try {
                result = matchService.match(kind, point.name().trim(), point.lat(), point.lon());
            } catch (RuntimeException e) {
                // 외부 API 장애는 해당 장소만 "찾지 못함"으로 돌려준다 (요청 URL·키는 로그에 남기지 않음)
                log.warn("장소 매칭 실패 ({} {}): {}", kind, point.name(), e.getClass().getSimpleName());
                result = new PlaceMatchService.MatchResult("ERROR", null, null, null, null, null, null);
            }
            responses.add(new MatchResponse(kind.name(), kind.label(), result));
        }
        return ResponseEntity.ok(responses);
    }
}
