ALTER TABLE public.tier_grade
    ADD COLUMN display_name varchar(50),
    ADD COLUMN default_profile_image varchar(500);

UPDATE public.tier_grade tg
SET display_name = t.display_name,
    default_profile_image = t.default_profile_image
FROM public.tier t
WHERE tg.tier_code = t.tier_code;

INSERT INTO public.tier_grade (
    tier_code,
    display_name,
    grade,
    min_score,
    max_score,
    default_profile_image
)
SELECT
    t.tier_code,
    t.display_name,
    'B',
    t.min_score,
    t.max_score,
    t.default_profile_image
FROM public.tier t
WHERE t.tier_code = 'TURTLE'
ON CONFLICT (tier_code, grade) DO UPDATE
SET display_name = EXCLUDED.display_name,
    min_score = EXCLUDED.min_score,
    max_score = EXCLUDED.max_score,
    default_profile_image = EXCLUDED.default_profile_image;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.tier t
        WHERE NOT EXISTS (
            SELECT 1
            FROM public.tier_grade tg
            WHERE tg.tier_code = t.tier_code
        )
    ) THEN
        RAISE EXCEPTION 'tier_grade metadata is missing for at least one tier_code';
    END IF;
END $$;

ALTER TABLE public.tier_grade
    ALTER COLUMN display_name SET NOT NULL,
    ALTER COLUMN default_profile_image SET NOT NULL;
