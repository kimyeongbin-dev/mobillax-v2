package MobilLax.Domain.Place;

import MobilLax.Common.Time.KoreaTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 매칭 캐시: 비교해서 일치한 TMAP 장소(이름·좌표) ↔ TAGO 장소 쌍
 * - 같은 TMAP 장소가 다시 나오면 카카오·도로명주소 API 를 부르지 않고 이 표로 바로 찾는다
 * - 좌표는 소수점 4자리(약 10m)로 맞춰 키로 쓴다
 * - TMAP 대중교통 API 약관: 얻은 데이터는 저장 후 24시간 이상 쓸 수 없다 → 24시간이 지난 쌍은 쓰지 않고 지운다
 */
// enum 은 MySQL ENUM 이 아닌 varchar 로 둔다 (값을 추가해도 ddl-auto=update 로 컬럼이 바뀌지 않는 문제 방지)
@Entity
@Getter
@NoArgsConstructor
@Table(name = "place_match",
        uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "tmapName", "tmapLat", "tmapLon"}))
public class PlaceMatch {

    /** ADDRESS: 주소 일치, DISTANCE: 1km 안, NAME: 같은 시도 안 유일한 같은 이름 */
    public enum Method { ADDRESS, DISTANCE, NAME }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private TransitKind kind;

    @Column(nullable = false, length = 100)
    private String tmapName;

    @Column(nullable = false)
    private double tmapLat;

    @Column(nullable = false)
    private double tmapLon;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private TransitPlace place;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private Method method;

    /** 거리로 매칭한 경우 TMAP 좌표와의 거리(m) */
    private Integer distanceMeters;

    private LocalDateTime matchedAt;

    public PlaceMatch(TransitKind kind, String tmapName, double tmapLat, double tmapLon,
                      TransitPlace place, Method method, Integer distanceMeters) {
        this.kind = kind;
        this.tmapName = tmapName;
        this.tmapLat = tmapLat;
        this.tmapLon = tmapLon;
        this.place = place;
        this.method = method;
        this.distanceMeters = distanceMeters;
        this.matchedAt = KoreaTime.now();
    }

    /** TMAP 데이터를 저장해 둘 수 있는 최대 시간 (TMAP 대중교통 API 이용약관) */
    public static final Duration RETENTION = Duration.ofHours(24);

    /** 저장한 지 24시간이 지났는지 */
    public boolean isExpired(LocalDateTime now) {
        return matchedAt == null || matchedAt.isBefore(now.minus(RETENTION));
    }

    /** 캐시 키로 쓰는 좌표 (소수점 4자리) */
    public static double roundCoord(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
