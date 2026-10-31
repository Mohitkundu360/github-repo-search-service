package com.example.github;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;

public interface GithubRepositoryEntityRepository
        extends JpaRepository<GithubRepositoryEntity, Long>, JpaSpecificationExecutor<GithubRepositoryEntity> {

    List<GithubRepositoryEntity> findByGithubIdIn(Collection<Long> githubIds);
}
