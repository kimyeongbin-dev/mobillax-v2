package MobilLax.Domain.Place;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 기준표: TAGO 역·터미널·공항과 그 주소 (도로명주소 API 로 채움)
 * - 공공데이터만 저장한다. 카카오 검색 결과(주소·좌표)는 약관상 저장하지 않는다
 * - 주소는 비교할 수 있게 도로명(시군구·도로명·건물번호)과 지번(시군구·읍면동·번지)을 나눠 둔다
 */
// enum 은 MySQL ENUM 이 아닌 varchar 로 둔다 (값을 추가해도 ddl-auto=update 로 컬럼이 바뀌지 않는 문제 방지)
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "transit_place",
        uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "tagoId"}))
public class TransitPlace {

    public enum AddressStatus { UNRESOLVED, FOUND, NOT_FOUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private TransitKind kind;

    @Column(nullable = false, length = 40)
    private String tagoId;

    @Column(nullable = false, length = 100)
    private String name;

    /** TAGO 가 주는 시도 (열차역·시외버스 터미널만) */
    @Column(length = 40)
    private String cityName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20)")
    private AddressStatus addressStatus = AddressStatus.UNRESOLVED;

    private String buildingName;
    private String roadAddr;
    private String jibunAddr;

    // 도로명 주소 비교용
    private String sggNm;
    private String roadName;
    private Integer buildingMainNo;
    private Integer buildingSubNo;

    // 지번 주소 비교용
    private String emdNm;
    private Integer lotMainNo;
    private Integer lotSubNo;

    private LocalDateTime updatedAt;

    public TransitPlace(TransitKind kind, String tagoId, String name, String cityName) {
        this.kind = kind;
        this.tagoId = tagoId;
        this.name = name;
        this.cityName = cityName;
        this.updatedAt = LocalDateTime.now();
    }

    public void applyAddress(JusoAddress address) {
        this.addressStatus = AddressStatus.FOUND;
        this.buildingName = address.buildingName();
        this.roadAddr = address.roadAddr();
        this.jibunAddr = address.jibunAddr();
        this.sggNm = address.sggNm();
        this.roadName = address.roadName();
        this.buildingMainNo = address.buildingMainNo();
        this.buildingSubNo = address.buildingSubNo();
        this.emdNm = address.emdNm();
        this.lotMainNo = address.lotMainNo();
        this.lotSubNo = address.lotSubNo();
        this.updatedAt = LocalDateTime.now();
    }

    public void markAddressNotFound() {
        this.addressStatus = AddressStatus.NOT_FOUND;
        this.updatedAt = LocalDateTime.now();
    }
}
