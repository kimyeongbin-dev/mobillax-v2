package MobilLax.Domain.Place;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlaceMatchRepository extends JpaRepository<PlaceMatch, Long> {

    Optional<PlaceMatch> findByKindAndTmapNameAndTmapLatAndTmapLon(
            TransitKind kind, String tmapName, double tmapLat, double tmapLon);
}
