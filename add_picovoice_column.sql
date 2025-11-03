-- Add missing picovoice_access_key column to user_settings table
-- Run this in your Supabase SQL Editor

ALTER TABLE public.user_settings 
ADD COLUMN IF NOT EXISTS picovoice_access_key TEXT;

-- Verify the column was added
SELECT column_name, data_type, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'user_settings' 
AND table_schema = 'public'
ORDER BY ordinal_position;