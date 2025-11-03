-- ========================================
-- VASATEY APP - DROP ALL TABLES
-- ========================================
-- This will delete ALL tables from your database

-- Drop all tables with CASCADE (removes everything)
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;

-- Grant permissions back to authenticated users
GRANT USAGE ON SCHEMA public TO authenticated;
GRANT CREATE ON SCHEMA public TO authenticated;