package MobilLax.Domain.Place;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransitPlaceRepository extends JpaRepository<TransitPlace, Long> {

    List<TransitPlace> findByKind(TransitKind kind);

    Optional<TransitPlace> findByKindAndTagoId(TransitKind kind, String tagoId);
}
