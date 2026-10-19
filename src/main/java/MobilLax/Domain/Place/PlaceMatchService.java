package MobilLax.Domain.Place;

import MobilLax.Common.Time.KoreaTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * TMAP 구간의 출발·도착 장소를 TAGO 역·터미널·공항과 맞춘다
 *
 * 순서: 매칭 캐시 → 주소 비교(도로명·지번) → 거리 비교(1km) → 같은 이름 → 일치하면 캐시에 저장
 * - TAGO 목록은 필요한 교통수단·지역만 받아 기준표에 넣고, 주소는 후보가 될 때 도로명주소 API 로 채운다
 * - 카카오 결과(주소·좌표)는 비교에만 쓰고 저장하지 않는다
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceMatchService {

    /** 주소·거리 비교에 쓸 이름 후보 수 */
    private static final int MAX_CANDIDATES = 5;

    private final TransitPlaceRepository placeRepository;
    private final PlaceMatchRepository matchRepository;
    private final TagoPlaceClient tagoClient;
    private final JusoClient jusoClient;
    private final KakaoLocalClient kakaoClient;

    /** 매칭 결과 */
    public record MatchResult(String status, String method, Integer distanceMeters,
                              String tagoId, String tagoName, String roadAddr, String jibunAddr) {

        static MatchResult notFound() {
            return new MatchResult("NOT_FOUND", null, null, null, null, null, null);
        }

        static MatchResult of(String method, PlaceMatch match) {
            TransitPlace p = match.getPlace();
            return new MatchResult("MATCHED", method, match.getDistanceMeters(),
                    p.getTagoId(), p.getName(), p.getRoadAddr(), p.getJibunAddr());
        }
    }

    @Transactional
    public MatchResult match(TransitKind kind, String tmapName, double lat, double lon) {
        double keyLat = PlaceMatch.roundCoord(lat);
        double keyLon = PlaceMatch.roundCoord(lon);

        Optional<PlaceMatch> cached = matchRepository.findByKindAndTmapNameAndTmapLatAndTmapLon(kind, tmapName, keyLat, keyLon);
        if (cached.isPresent()) {
            if (!cached.get().isExpired(KoreaTime.now())) {
                return MatchResult.of("CACHE", cached.get());
            }
            // 24시간이 지난 TMAP 데이터는 쓰지 않는다: 지우고 다시 맞춘다
            matchRepository.delete(cached.get());
            matchRepository.flush();
        }
        if (!kakaoClient.isConfigured()) {
            log.warn("KAKAO_APPKEY 가 없어 장소 매칭을 건너뜁니다");
            return MatchResult.notFound();
        }

        Optional<KakaoAddress> tmapAddress = tmapAddress(lat, lon);
        String sido = tmapAddress.map(KakaoAddress::region1).orElse(null);
        List<TransitPlace> candidates = candidates(kind, tmapName, sido);

        // 1) 주소 비교
        if (tmapAddress.isPresent()) {
            for (TransitPlace place : candidates) {
                resolveAddress(place);
                if (PlaceMatching.sameAddress(place, tmapAddress.get())) {
                    return save(kind, tmapName, keyLat, keyLon, place, PlaceMatch.Method.ADDRESS, null);
                }
            }
        }

        // 2) 거리 비교 (공항·길가 승강장처럼 주소가 어긋나는 곳): 1km 안에서 이름이 더 비슷한 곳, 같으면 더 가까운 곳
        TransitPlace best = null;
        int bestScore = -1;
        double bestDistance = Double.MAX_VALUE;
        for (TransitPlace place : candidates) {
            OptionalDouble distance = distanceTo(place, lat, lon);
            if (distance.isEmpty() || distance.getAsDouble() > PlaceMatching.MAX_DISTANCE_METERS) {
                continue;
            }
            int score = PlaceMatching.nameScore(tmapName, place.getName());
            if (score > bestScore || (score == bestScore && distance.getAsDouble() < bestDistance)) {
                best = place;
                bestScore = score;
                bestDistance = distance.getAsDouble();
            }
        }
        if (best != null) {
            return save(kind, tmapName, keyLat, keyLon, best, PlaceMatch.Method.DISTANCE, (int) Math.round(bestDistance));
        }

        // 3) 주소·좌표를 모두 찾지 못한 길가 정류장: 같은 시도 안에 이름이 정확히 같은 곳이 하나뿐이면 그곳
        List<TransitPlace> sameName = candidates.stream()
                .filter(p -> PlaceMatching.nameScore(tmapName, p.getName()) == 3)
                .toList();
        if (sameName.size() == 1 && sameName.get(0).getCityName() != null) {
            return save(kind, tmapName, keyLat, keyLon, sameName.get(0), PlaceMatch.Method.NAME, null);
        }
        return MatchResult.notFound();
    }

    /**
     * TMAP 좌표와 후보 사이 거리 (저장하지 않는 카카오 좌표 사용)
     * 도로명주소 API 가 옛 건물을 줄 수 있어(예: 옛 경주역) 주소 좌표와 이름 검색 좌표 중 가까운 쪽을 쓴다
     */
    private OptionalDouble distanceTo(TransitPlace place, double lat, double lon) {
        return coordsOf(place).stream()
                .mapToDouble(c -> PlaceMatching.distanceMeters(lat, lon, c.lat(), c.lon()))
                .min();
    }

    private Optional<KakaoAddress> tmapAddress(double lat, double lon) {
        try {
            return kakaoClient.coordToAddress(lat, lon);
        } catch (RuntimeException e) {
            log.warn("카카오 좌표→주소 변환 실패: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private MatchResult save(TransitKind kind, String tmapName, double lat, double lon,
                             TransitPlace place, PlaceMatch.Method method, Integer distance) {
        PlaceMatch match = matchRepository.save(new PlaceMatch(kind, tmapName, lat, lon, place, method, distance));
        return MatchResult.of(method.name(), match);
    }

    /**
     * 이름이 비슷한 TAGO 장소 후보 (시도를 아는 교통수단은 같은 시도 안에서만)
     */
    private List<TransitPlace> candidates(TransitKind kind, String tmapName, String sido) {
        String sidoKey = PlaceMatching.sidoKey(sido);
        return loadPlaces(kind, sidoKey).stream()
                .filter(p -> p.getCityName() == null || sidoKey.isEmpty()
                        || PlaceMatching.sidoKey(p.getCityName()).equals(sidoKey))
                .filter(p -> PlaceMatching.nameScore(tmapName, p.getName()) > 0)
                .sorted(Comparator.comparingInt((TransitPlace p) -> PlaceMatching.nameScore(tmapName, p.getName())).reversed())
                .limit(MAX_CANDIDATES)
                .toList();
    }

    /**
     * 기준표에 없으면 TAGO 목록을 받아 넣는다 (열차역은 시도 단위, 나머지는 전체)
     */
    private List<TransitPlace> loadPlaces(TransitKind kind, String sidoKey) {
        List<TransitPlace> places = placeRepository.findByKind(kind);
        if (kind == TransitKind.TRAIN) {
            boolean loaded = places.stream().anyMatch(p -> PlaceMatching.sidoKey(p.getCityName()).equals(sidoKey));
            if (!loaded && !sidoKey.isEmpty()) {
                insertAll(kind, places, tagoClient.trainCities().stream()
                        .filter(c -> PlaceMatching.sidoKey(c.cityName()).equals(sidoKey))
                        .flatMap(c -> tagoClient.trainStations(c).stream())
                        .toList());
                places = placeRepository.findByKind(kind);
            }
            return places;
        }
        if (places.isEmpty()) {
            List<TagoPlaceClient.TagoPlace> fetched = switch (kind) {
                case EXPRESS_BUS -> tagoClient.expressTerminals();
                case INTERCITY_BUS -> tagoClient.intercityTerminals();
                case AIRPLANE -> tagoClient.airports();
                case TRAIN -> List.of();
            };
            insertAll(kind, places, fetched);
            places = placeRepository.findByKind(kind);
        }
        return places;
    }

    /** 기준표에 없는 TAGO 장소만 한 번에 저장 (시외버스 터미널은 4천 곳이 넘는다) */
    private void insertAll(TransitKind kind, List<TransitPlace> existing, List<TagoPlaceClient.TagoPlace> fetched) {
        Set<String> known = new HashSet<>();
        existing.forEach(p -> known.add(p.getTagoId()));
        List<TransitPlace> fresh = new ArrayList<>();
        for (TagoPlaceClient.TagoPlace t : fetched) {
            if (t.tagoId() != null && !t.tagoId().isBlank() && known.add(t.tagoId())) {
                fresh.add(new TransitPlace(kind, t.tagoId(), t.name(), t.cityName()));
            }
        }
        placeRepository.saveAll(fresh);
    }

    /**
     * 후보의 주소를 아직 모르면 도로명주소 API 로 찾아 기준표에 저장한다
     * 검색어: 이름 + 교통수단 말(역·고속버스터미널·터미널·공항) → 이름만
     */
    private void resolveAddress(TransitPlace place) {
        if (place.getAddressStatus() != TransitPlace.AddressStatus.UNRESOLVED || !jusoClient.isConfigured()) {
            return;
        }
        for (String keyword : searchKeywords(place)) {
            try {
                Optional<JusoAddress> found = jusoClient.search(keyword).stream()
                        .filter(a -> PlaceMatching.looksLikeKind(place.getKind(), a.buildingName()))
                        .filter(a -> place.getCityName() == null
                                || PlaceMatching.sidoKey(a.siNm()).equals(PlaceMatching.sidoKey(place.getCityName())))
                        .filter(a -> PlaceMatching.nameScore(a.buildingName(), place.getName()) > 0)
                        .findFirst();
                if (found.isPresent()) {
                    place.applyAddress(found.get());
                    placeRepository.save(place);
                    return;
                }
            } catch (RuntimeException e) {
                // 도로명주소 API 장애·검색어 오류는 이 후보만 건너뛰고, 다음에 다시 찾도록 상태를 바꾸지 않는다
                log.warn("도로명주소 검색 실패 ({} {}): {}", place.getKind(), place.getName(), e.getClass().getSimpleName());
                return;
            }
        }
        place.markAddressNotFound();
        placeRepository.save(place);
    }

    private static List<String> searchKeywords(TransitPlace place) {
        String name = place.getName();
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        for (String suffix : place.getKind().searchSuffixes()) {
            keywords.add(name.endsWith(suffix) ? name : name + suffix);
        }
        keywords.add(name);
        String shortName = PlaceMatching.normalizeName(name);
        for (String suffix : place.getKind().searchSuffixes()) {
            keywords.add(shortName + suffix);
        }
        return keywords.stream().filter(k -> k.length() >= 2).toList();
    }

    /**
     * 거리 비교용 좌표 (저장하지 않음): 주소로 찾은 좌표와 이름으로 찾은 좌표
     */
    private List<KakaoLocalClient.Coord> coordsOf(TransitPlace place) {
        List<KakaoLocalClient.Coord> coords = new ArrayList<>();
        try {
            if (place.getAddressStatus() == TransitPlace.AddressStatus.FOUND && place.getRoadAddr() != null) {
                kakaoClient.addressToCoord(place.getRoadAddr()).ifPresent(coords::add);
            }
            String suffix = place.getKind().searchSuffixes()[0];
            String keyword = place.getName().endsWith(suffix) ? place.getName() : place.getName() + " " + suffix;
            kakaoClient.keywordToCoord(keyword).ifPresent(coords::add);
        } catch (RuntimeException e) {
            log.warn("카카오 좌표 조회 실패 ({} {}): {}", place.getKind(), place.getName(), e.getClass().getSimpleName());
        }
        return coords;
    }
}
