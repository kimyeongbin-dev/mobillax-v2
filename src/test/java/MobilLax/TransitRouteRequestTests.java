package MobilLax;

import MobilLax.Domain.Route.TransitRouteRequest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 경로 검색 요청의 출발 일시 검증 (오늘부터 14일 안, yyyyMMddHHmm)
 */
class TransitRouteRequestTests {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 15);

    private static TransitRouteRequest at(String departAt) {
        return new TransitRouteRequest(126.97, 37.55, 129.04, 35.11, departAt);
    }

    @Test
    void allowsMissingDepartAtAsCurrentTime() {
        assertThat(at(null).isDepartAtValid(TODAY)).isTrue();
        assertThat(at("").isDepartAtValid(TODAY)).isTrue();
        assertThat(at(null).hasDepartAt()).isFalse();
    }

    @Test
    void allowsDatesWithinFourteenDays() {
        assertThat(at("202610150000").isDepartAtValid(TODAY)).isTrue();
        assertThat(at("202610221430").isDepartAtValid(TODAY)).isTrue();
        assertThat(at("202610292359").isDepartAtValid(TODAY)).isTrue();
    }

    @Test
    void rejectsPastAndTooFarDates() {
        assertThat(at("202610142359").isDepartAtValid(TODAY)).isFalse();
        assertThat(at("202610300000").isDepartAtValid(TODAY)).isFalse();
    }

    @Test
    void rejectsMalformedOrImpossibleDates() {
        assertThat(at("2026-10-15 10:00").isDepartAtValid(TODAY)).isFalse();
        assertThat(at("20261015").isDepartAtValid(TODAY)).isFalse();
        assertThat(at("202602301000").isDepartAtValid(TODAY)).isFalse();
        assertThat(at("202610152460").isDepartAtValid(TODAY)).isFalse();
    }
}
