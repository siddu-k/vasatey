-- Clean up duplicate user settings and add missing column
-- Run this in your Supabase SQL Editor

-- Step 1: Add the missing picovoice_access_key column if it doesn't exist
ALTER TABLE public.user_settings 
ADD COLUMN IF NOT EXISTS picovoice_access_key TEXT;

-- Step 2: Clean up duplicate user settings (keep only the most recent one per user)
DELETE FROM public.user_settings 
WHERE id NOT IN (
    SELECT DISTINCT ON (user_id) id
    FROM public.user_settings
    ORDER BY user_id, created_at DESC NULLS LAST, id DESC
);

-- Step 3: Add a unique constraint to prevent future duplicates
ALTER TABLE public.user_settings 
ADD CONSTRAINT unique_user_settings_per_user 
UNIQUE (user_id);

-- Step 4: Verify the cleanup
SELECT user_id, COUNT(*) as settings_count
FROM public.user_settings 
GROUP BY user_id
HAVING COUNT(*) > 1;

-- Step 5: Check final table structure
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'user_settings' 
AND table_schema = 'public'
ORDER BY ordinal_position;