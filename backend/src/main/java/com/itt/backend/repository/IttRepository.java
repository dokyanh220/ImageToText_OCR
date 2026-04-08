package com.itt.backend.repository;

import com.itt.backend.entity.IttHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IttRepository extends JpaRepository<IttHistory, Long> {
}