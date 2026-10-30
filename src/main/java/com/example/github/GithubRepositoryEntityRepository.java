package com.example.github;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GithubRepositoryEntityRepository extends JpaRepository<GithubRepositoryEntity, Long> {

    List<GithubRepositoryEntity> findByGithubIdIn(Collection<Long> githubIds);

    List<GithubRepositoryEntity> findByNameContainingIgnoreCase(String name, Sort sort);
}
