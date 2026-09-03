package com.piggyback.backend.domain.user;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    List<Guardian> findAllByUserId(Long userId);

    long countByUserId(Long userId);
}
