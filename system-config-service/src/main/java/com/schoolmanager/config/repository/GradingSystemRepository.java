package com.schoolmanager.config.repository;

import com.schoolmanager.config.entity.GradingSystem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GradingSystemRepository extends JpaRepository<GradingSystem, UUID> {
}
