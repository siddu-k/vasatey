-- ========================================
-- VASATEY APP - ULTRA SIMPLE RESET
-- ========================================
-- Just run these 2 lines to delete everything:

-- Delete all users (this will cascade delete all related data)
DELETE FROM auth.users;

-- Verify everything is gone
SELECT 'All data deleted!' as status, COUNT(*) as remaining_users FROM auth.users;