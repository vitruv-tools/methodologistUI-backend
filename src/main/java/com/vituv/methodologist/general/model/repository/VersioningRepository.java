package com.vituv.methodologist.general.model.repository;


import com.vituv.methodologist.general.model.Versioning;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface VersioningRepository extends CrudRepository<Versioning, Long> {
    Optional<Versioning> findTopByAppNameOrderByIdDesc(String name);
}