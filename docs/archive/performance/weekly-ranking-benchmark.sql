-- Status: Archived
-- Audience: Engineers, Codex
-- Source of Truth: No
-- Last Reviewed: 2026-06-20

\echo 'weekly ranking benchmark: setup'

SET client_min_messages TO warning;

CREATE INDEX IF NOT EXISTS idx_weekly_user_stats_score_ranking
    ON weekly_user_stats (week_start_date, tier_score DESC, total_distance_km DESC, user_id ASC);

CREATE INDEX IF NOT EXISTS idx_weekly_user_stats_distance_ranking
    ON weekly_user_stats (week_start_date, total_distance_km DESC, tier_score DESC, user_id ASC);

TRUNCATE TABLE
    record_outbox_events,
    weekly_user_stats,
    weekly_tiers,
    running_records,
    oauth_client,
    auth_tokens,
    users
RESTART IDENTITY CASCADE;

\echo 'weekly ranking benchmark: seed users'

INSERT INTO users (
    id,
    status,
    nickname,
    gender,
    birth,
    height,
    weight,
    created_at,
    updated_at
)
SELECT
    gs,
    'NORMAL',
    'runner_' || gs,
    CASE gs % 3 WHEN 0 THEN 'F' WHEN 1 THEN 'M' ELSE 'O' END,
    DATE '1990-01-01' + ((gs % 6000)::int),
    150 + (gs % 45),
    45 + (gs % 55),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generate_series(1, 10000) AS gs;

SELECT setval(pg_get_serial_sequence('users', 'id'), 10000, true);

\echo 'weekly ranking benchmark: seed running records'

WITH generated_records AS (
    SELECT
        gs,
        ((gs - 1) % 10000) + 1 AS user_id,
        ROUND((3.0 + ((gs % 120)::numeric / 10.0)), 3) AS distance_km,
        240 + (gs % 181) AS avg_pace_sec_per_km,
        TIMESTAMP '2026-04-06 00:00:00'
            + ((gs % 7) * INTERVAL '1 day')
            + ((gs % 86400) * INTERVAL '1 second') AS start_at
    FROM generate_series(1, 100000) AS gs
)
INSERT INTO running_records (
    user_id,
    source,
    distance_km,
    duration_sec,
    avg_pace_sec_per_km,
    avg_heart_rate,
    calories_kcal,
    start_at,
    end_at,
    created_at,
    updated_at
)
SELECT
    user_id,
    CASE gs % 2 WHEN 0 THEN 'IOS' ELSE 'ANDROID' END,
    distance_km::double precision,
    ROUND(distance_km * avg_pace_sec_per_km)::int,
    avg_pace_sec_per_km,
    120 + (gs % 60),
    200 + (gs % 600),
    start_at,
    start_at + (ROUND(distance_km * avg_pace_sec_per_km)::int * INTERVAL '1 second'),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM generated_records;

\echo 'weekly ranking benchmark: build weekly_user_stats'

WITH record_scores AS (
    SELECT
        r.user_id,
        r.distance_km,
        r.duration_sec,
        r.avg_pace_sec_per_km,
        ROUND((
            (6.0 / (r.avg_pace_sec_per_km / 60.0))
            * CASE
                WHEN r.distance_km < 3.0 THEN 0.80
                WHEN r.distance_km < 5.0 THEN 0.90
                WHEN r.distance_km < 10.0 THEN 1.00
                WHEN r.distance_km < 21.0975 THEN 1.10
                WHEN r.distance_km < 42.195 THEN 1.20
                ELSE 1.30
            END
        )::numeric, 2) AS record_tier_score
    FROM running_records r
    WHERE r.start_at >= TIMESTAMP '2026-04-06 00:00:00'
      AND r.start_at < TIMESTAMP '2026-04-13 00:00:00'
)
INSERT INTO weekly_user_stats (
    user_id,
    week_start_date,
    run_count,
    total_distance_km,
    total_duration_sec,
    weighted_pace_sum,
    avg_pace_sec_per_km,
    tier_score_sum,
    tier_score,
    created_at,
    updated_at
)
SELECT
    user_id,
    DATE '2026-04-06',
    COUNT(*)::int,
    ROUND(SUM(distance_km)::numeric, 3),
    SUM(duration_sec)::int,
    ROUND(SUM(distance_km * avg_pace_sec_per_km)::numeric, 3),
    COALESCE(ROUND(SUM(distance_km * avg_pace_sec_per_km) / NULLIF(SUM(distance_km), 0))::int, 0),
    ROUND(SUM(record_tier_score)::numeric, 2),
    COALESCE(ROUND(AVG(record_tier_score)::numeric, 2), 0),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM record_scores
GROUP BY user_id;

ANALYZE users;
ANALYZE running_records;
ANALYZE weekly_user_stats;

\echo 'weekly ranking benchmark: row counts'

SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'running_records', COUNT(*) FROM running_records
UNION ALL
SELECT 'weekly_user_stats', COUNT(*) FROM weekly_user_stats
ORDER BY table_name;

\echo 'weekly ranking benchmark: raw running_records score ranking'

EXPLAIN (ANALYZE, BUFFERS)
WITH record_scores AS (
    SELECT
        r.user_id,
        r.distance_km,
        r.duration_sec,
        r.avg_pace_sec_per_km,
        ROUND((
            (6.0 / (r.avg_pace_sec_per_km / 60.0))
            * CASE
                WHEN r.distance_km < 3.0 THEN 0.80
                WHEN r.distance_km < 5.0 THEN 0.90
                WHEN r.distance_km < 10.0 THEN 1.00
                WHEN r.distance_km < 21.0975 THEN 1.10
                WHEN r.distance_km < 42.195 THEN 1.20
                ELSE 1.30
            END
        )::numeric, 2) AS record_tier_score
    FROM running_records r
    JOIN users u ON u.id = r.user_id
    WHERE r.start_at >= TIMESTAMP '2026-04-06 00:00:00'
      AND r.start_at < TIMESTAMP '2026-04-13 00:00:00'
      AND u.status <> 'WITHDRAWN'
      AND u.nickname IS NOT NULL
)
SELECT
    user_id,
    COUNT(*)::int AS run_count,
    ROUND(SUM(distance_km)::numeric, 3) AS total_distance_km,
    COALESCE(ROUND(AVG(record_tier_score)::numeric, 2), 0) AS tier_score
FROM record_scores
GROUP BY user_id
ORDER BY tier_score DESC, total_distance_km DESC, user_id ASC
LIMIT 100;

\echo 'weekly ranking benchmark: weekly_user_stats score ranking'

EXPLAIN (ANALYZE, BUFFERS)
SELECT s.*
FROM weekly_user_stats s
JOIN users u ON u.id = s.user_id
WHERE s.week_start_date = DATE '2026-04-06'
  AND s.run_count > 0
  AND u.status <> 'WITHDRAWN'
  AND u.nickname IS NOT NULL
ORDER BY s.tier_score DESC, s.total_distance_km DESC, s.user_id ASC
LIMIT 100;

\echo 'weekly ranking benchmark: raw running_records distance ranking'

EXPLAIN (ANALYZE, BUFFERS)
WITH record_scores AS (
    SELECT
        r.user_id,
        r.distance_km,
        ROUND((
            (6.0 / (r.avg_pace_sec_per_km / 60.0))
            * CASE
                WHEN r.distance_km < 3.0 THEN 0.80
                WHEN r.distance_km < 5.0 THEN 0.90
                WHEN r.distance_km < 10.0 THEN 1.00
                WHEN r.distance_km < 21.0975 THEN 1.10
                WHEN r.distance_km < 42.195 THEN 1.20
                ELSE 1.30
            END
        )::numeric, 2) AS record_tier_score
    FROM running_records r
    JOIN users u ON u.id = r.user_id
    WHERE r.start_at >= TIMESTAMP '2026-04-06 00:00:00'
      AND r.start_at < TIMESTAMP '2026-04-13 00:00:00'
      AND u.status <> 'WITHDRAWN'
      AND u.nickname IS NOT NULL
)
SELECT
    user_id,
    COUNT(*)::int AS run_count,
    ROUND(SUM(distance_km)::numeric, 3) AS total_distance_km,
    COALESCE(ROUND(AVG(record_tier_score)::numeric, 2), 0) AS tier_score
FROM record_scores
GROUP BY user_id
ORDER BY total_distance_km DESC, tier_score DESC, user_id ASC
LIMIT 100;

\echo 'weekly ranking benchmark: weekly_user_stats distance ranking'

EXPLAIN (ANALYZE, BUFFERS)
SELECT s.*
FROM weekly_user_stats s
JOIN users u ON u.id = s.user_id
WHERE s.week_start_date = DATE '2026-04-06'
  AND s.run_count > 0
  AND u.status <> 'WITHDRAWN'
  AND u.nickname IS NOT NULL
ORDER BY s.total_distance_km DESC, s.tier_score DESC, s.user_id ASC
LIMIT 100;

\echo 'weekly ranking benchmark: my ranking target user'

SELECT
    user_id,
    week_start_date,
    run_count,
    total_distance_km,
    tier_score
FROM weekly_user_stats
WHERE user_id = 5000
  AND week_start_date = DATE '2026-04-06';

\echo 'weekly ranking benchmark: my ranking stats row lookup'

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM weekly_user_stats
WHERE user_id = 5000
  AND week_start_date = DATE '2026-04-06';

\echo 'weekly ranking benchmark: my score rank count'

EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM weekly_user_stats s
JOIN users u ON u.id = s.user_id
WHERE s.week_start_date = DATE '2026-04-06'
  AND s.run_count > 0
  AND u.status <> 'WITHDRAWN'
  AND u.nickname IS NOT NULL
  AND (
      s.tier_score > 1.10
      OR (s.tier_score = 1.10 AND s.total_distance_km > 74.000)
      OR (
          s.tier_score = 1.10
          AND s.total_distance_km = 74.000
          AND s.user_id < 5000
      )
  );

\echo 'weekly ranking benchmark: my distance rank count'

EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM weekly_user_stats s
JOIN users u ON u.id = s.user_id
WHERE s.week_start_date = DATE '2026-04-06'
  AND s.run_count > 0
  AND u.status <> 'WITHDRAWN'
  AND u.nickname IS NOT NULL
  AND (
      s.total_distance_km > 74.000
      OR (s.total_distance_km = 74.000 AND s.tier_score > 1.10)
      OR (
          s.total_distance_km = 74.000
          AND s.tier_score = 1.10
          AND s.user_id < 5000
      )
  );
