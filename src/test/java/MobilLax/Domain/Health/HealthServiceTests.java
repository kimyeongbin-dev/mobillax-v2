package MobilLax.Domain.Health;

import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 보안 회귀 테스트 S7 — 헬스체크 판정 (DB 장애, 연결 시도 제한)
 */
class HealthServiceTests {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-18T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Test
    void dbErrorIsDownAndDoesNotEscape() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.getConnection())
                .thenThrow(new SQLException("Access denied for user 'mobillax'@'db' (using password: YES)"));
        HealthService health = new HealthService(dataSource, clock);

        assertThat(health.isDbUp()).isFalse();
        assertThat(health.isUp()).isFalse();
    }

    @Test
    void invalidConnectionIsDown() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(false);

        assertThat(new HealthService(dataSource, clock).isDbUp()).isFalse();
    }

    @Test
    void anonymousFloodTriesDbAtMostOncePerFiveSeconds() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        HealthService health = new HealthService(dataSource, clock);

        for (int i = 0; i < 100; i++) {
            assertThat(health.isDbUp()).isTrue();
        }
        verify(dataSource, times(1)).getConnection();

        now.set(now.get().plus(HealthService.DB_CACHE));
        health.isDbUp();
        verify(dataSource, times(2)).getConnection();
    }

    @Test
    void uncheckedExternalApiIsNotDownButFailedOneIs() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        HealthService health = new HealthService(dataSource, clock);

        assertThat(health.isUp()).isTrue();
        health.record(HealthService.Component.TAGO, false);
        assertThat(health.isUp()).isFalse();
        health.record(HealthService.Component.TAGO, true);
        assertThat(health.isUp()).isTrue();
    }
}
