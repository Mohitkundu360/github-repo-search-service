# Index benchmark: language filter + stars sort

Indicative only: one laptop, PostgreSQL 16 in Docker, warm cache, synthetic data.

## Setup
- Table `github_repositories`: 180 real rows plus 200,000 synthetic rows (`description = 'benchmark-seed'`), ids from 9,000,000,000 upward.
- Synthetic data: 10 languages, star counts skewed with `power(random(), 3)`.
- After seeding: `ANALYZE`. Total 200,198 rows, 15,780 match `lower(language) = 'java' AND stars >= 1000`.
- Each query ran twice. The second run is reported (first run in brackets).

## Queries
Page query (what `GET /api/github/repositories?language=java&minStars=1000&sortBy=stars&size=20` runs):

    EXPLAIN (ANALYZE, BUFFERS) SELECT * FROM github_repositories WHERE lower(language)='java' AND stars>=1000 ORDER BY stars DESC, id LIMIT 20;

Count query (behind `totalElements`):

    EXPLAIN (ANALYZE, BUFFERS) SELECT count(*) FROM github_repositories WHERE lower(language)='java' AND stars>=1000;

Index under test (migration V2):

    CREATE INDEX idx_repos_lang_stars ON github_repositories (lower(language), stars DESC, id);

## Results
| Query | Without index | With index |
|---|---|---|
| Page query | Parallel Seq Scan + top-N heapsort, 18.5 ms [26.0], 4,348 buffers | Index Scan, no sort, 0.13 ms [0.15], 23 buffers |
| Count query | Parallel Seq Scan, 22.2 ms [19.8], 4,220 buffers | Bitmap Index + Heap Scan, 7.4 ms [8.6], 4,292 buffers |

## Reading the numbers
- Page query: about 140x faster by execution time. The planner walks the index in order and stops after 20 rows, so there is no sort.
- Count query: about 3x. It still reads the heap pages of every matching row (similar buffer counts), so its cost grows with the number of matches. The page query gets the large win, the total count does not.
- Count timings vary by roughly 10 percent between runs.

## Limits
- Synthetic data, one machine, warm cache, two runs per query. Not a production benchmark.
- Deep offset pages were not measured.
- No index-only scan was observed for the count query.
