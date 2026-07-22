package com.schoolmanager.auth.repository;

import com.schoolmanager.auth.entity.FeatureModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeatureModuleRepository extends JpaRepository<FeatureModule, UUID> {
    Optional<FeatureModule> findByCode(String code);
}
