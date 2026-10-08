package com.parkingwatch.backend.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Repositorio de funcionarios. */
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

  Optional<AppUser> findByUsername(String username);

  boolean existsByUsername(String username);

  List<AppUser> findAllByOrderByUsernameAsc();
}
