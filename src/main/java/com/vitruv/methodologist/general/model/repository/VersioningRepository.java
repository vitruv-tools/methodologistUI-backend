package com.vitruv.methodologist.general.model.repository;

import com.vitruv.methodologist.general.model.Versioning;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface VersioningRepository extends CrudRepository<Versioning, Long> {
  Optional<Versioning> findTopByAppNameOrderByIdDesc(String name);
}
