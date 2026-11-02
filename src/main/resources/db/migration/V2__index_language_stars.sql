-- Serves list queries filtered by language and sorted by stars:
-- WHERE lower(language) = ? ORDER BY stars DESC, id
CREATE INDEX idx_repos_lang_stars ON github_repositories (lower(language), stars DESC, id);
