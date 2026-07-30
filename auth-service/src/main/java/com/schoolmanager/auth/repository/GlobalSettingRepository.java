package com.schoolmanager.auth.repository;

import com.schoolmanager.auth.entity.GlobalSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface GlobalSettingRepository extends JpaRepository<GlobalSetting, UUID> {
}
