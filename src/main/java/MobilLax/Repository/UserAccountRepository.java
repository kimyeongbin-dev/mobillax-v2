/**
 * UserAccountRepository.java
 *
 * ✅ 파일 목적: 사용자 계정 정보(UserAccount 엔티티)를 MySQL DB와 연동하기 위한 JPA Repository 인터페이스입니다.
 *              Spring Data JPA를 통해 CRUD 및 이메일 기반 사용자 조회 기능을 제공합니다.
 *
 * 작성자: 김영빈
 * 마지막 수정일: 2025-05-11
 */

package MobilLax.Repository;

import MobilLax.Model.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/**
 * ✅ 인터페이스 설명:
 * Spring Data JPA를 사용하여 UserAccount 엔티티에 대한 기본 CRUD 기능을 자동 구현합니다.
 * 사용자 이메일을 기준으로 조회 및 중복 확인 기능을 제공합니다.
 *
 * JpaRepository<UserAccount, String> :
 *   - UserAccount: 대상 엔티티
 *   - String: 해당 엔티티의 기본 키 타입 (예: email 또는 id)
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, String> {

    /**
     * 이메일로 사용자 조회
     *
     * @param email 조회할 사용자의 이메일 주소
     * @return Optional<UserAccount> 해당 이메일이 존재할 경우 사용자 정보, 없으면 빈 Optional
     */
    Optional<UserAccount> findByEmail(String email);

    /**
     * 이메일 중복 확인
     *
     * @param email 중복 확인할 이메일 주소
     * @return boolean 해당 이메일이 DB에 이미 존재하면 true
     */
    boolean existsByEmail(String email);

    void deleteByEmail(String email);
}
