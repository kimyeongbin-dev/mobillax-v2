package MobilLax.Domain.Admin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminRecoveryCodeRepository extends JpaRepository<AdminRecoveryCode, Long> {

    List<AdminRecoveryCode> findByEmailAndUsedAtIsNull(String email);

    void deleteByEmail(String email);
}
