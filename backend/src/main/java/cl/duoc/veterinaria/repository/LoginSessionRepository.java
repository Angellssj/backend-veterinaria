package cl.duoc.veterinaria.repository;

import cl.duoc.veterinaria.model.LoginSession;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface LoginSessionRepository extends JpaRepository<LoginSession, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LoginSession s where s.id = :id")
    Optional<LoginSession> findForUpdate(@Param("id") String id);

    boolean existsByIdAndUser_IdAndRevokedFalseAndExpiresAtAfter(String id, Long userId, java.time.Instant now);
}
