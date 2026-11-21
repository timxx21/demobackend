package com.example.demo.repository;

import com.example.demo.dto.CheckResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface CheckResultRepository extends JpaRepository<CheckResult, Long> {
    List<CheckResult> findByTaskId(UUID taskId);
    long countByTaskId(UUID taskId); // Понадобится, чтобы понять, все ли сайты проверены!
}
