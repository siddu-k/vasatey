-- ========================================
-- VASATEY APP - TEST DATA SEEDING
-- ========================================
-- Optional: Run this after the fresh reset to add test data
-- Only use this for development/testing purposes

-- ========================================
-- CLEAR AUTH USERS (if needed)
-- ========================================
-- WARNING: This will delete all authentication users
-- Only run if you want to completely start fresh
-- Uncomment the following lines if needed:

-- DELETE FROM auth.users WHERE email LIKE '%test%' OR email LIKE '%demo%';

-- ========================================
-- INSERT TEST USERS
-- ========================================
-- Note: You'll need to create these users through Supabase Auth first
-- Then update the auth_user_id values below with actual UUIDs

-- Example test user profiles (replace UUIDs with actual auth user IDs)
-- INSERT INTO user_profiles (
--     auth_user_id,
--     email,
--     full_name,
--     phone_number,
--     emergency_contact,
--     medical_info,
--     blood_type
-- ) VALUES
-- (
--     '00000000-0000-0000-0000-000000000001', -- Replace with actual auth user ID
--     'test@vasatey.com',
--     'Test User',
--     '+1234567890',
--     '+0987654321',
--     'No known medical conditions',
--     'O+'
-- ),
-- (
--     '00000000-0000-0000-0000-000000000002', -- Replace with actual auth user ID
--     'demo@vasatey.com',
--     'Demo User',
--     '+1111111111',
--     '+2222222222',
--     'Diabetes, hypertension',
--     'A+'
-- );

-- ========================================
-- MANUAL TEST PROCEDURE
-- ========================================

/*
TO TEST THE FRESH DATABASE:

1. Run the fresh reset script first:
   - Copy and paste supabase_fresh_reset.sql into Supabase SQL Editor
   - Execute the entire script

2. Create a test user through your app:
   - Open the Vasatey app
   - Try to sign up with a new email
   - This should now work without "user already exists" error

3. Verify the user was created:
   SELECT * FROM auth.users ORDER BY created_at DESC LIMIT 5;
   SELECT * FROM user_profiles ORDER BY created_at DESC LIMIT 5;

4. Test login:
   - Try logging in with the email you just created
   - Should work without any errors

5. Test app functionality:
   - Add guardians
   - Update profile
   - Test emergency features
   - Check all data is being saved properly

6. Check data integrity:
   SELECT 
       up.email,
       up.full_name,
       COUNT(g.id) as guardian_count,
       COUNT(a.id) as alert_count,
       us.voice_detection_enabled
   FROM user_profiles up
   LEFT JOIN guardians g ON g.user_id = up.id
   LEFT JOIN alerts a ON a.user_id = up.id
   LEFT JOIN user_settings us ON us.user_id = up.id
   GROUP BY up.id, us.voice_detection_enabled
   ORDER BY up.created_at DESC;
*/

-- ========================================
-- CLEAN UP FUNCTIONS (if needed)
-- ========================================

-- Function to completely reset user data (use with caution)
CREATE OR REPLACE FUNCTION reset_user_data(user_email TEXT)
RETURNS TEXT AS $$
DECLARE
    user_auth_id UUID;
    user_profile_id UUID;
    result_message TEXT;
BEGIN
    -- Get the auth user ID
    SELECT id INTO user_auth_id 
    FROM auth.users 
    WHERE email = user_email;
    
    IF user_auth_id IS NULL THEN
        RETURN 'User not found with email: ' || user_email;
    END IF;
    
    -- Get the profile ID
    SELECT id INTO user_profile_id 
    FROM user_profiles 
    WHERE auth_user_id = user_auth_id;
    
    IF user_profile_id IS NOT NULL THEN
        -- Delete related data (cascading deletes will handle this)
        DELETE FROM user_profiles WHERE id = user_profile_id;
        result_message := 'Deleted profile and related data for: ' || user_email;
    ELSE
        result_message := 'No profile found for: ' || user_email;
    END IF;
    
    -- Optionally delete the auth user too
    -- DELETE FROM auth.users WHERE id = user_auth_id;
    -- result_message := result_message || '. Also deleted auth user.';
    
    RETURN result_message;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to check database health
CREATE OR REPLACE FUNCTION check_database_health()
RETURNS TABLE (
    table_name TEXT,
    record_count BIGINT,
    last_activity TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT 'user_profiles'::TEXT, COUNT(*)::BIGINT, MAX(updated_at)
    FROM user_profiles
    UNION ALL
    SELECT 'guardians'::TEXT, COUNT(*)::BIGINT, MAX(updated_at)
    FROM guardians
    UNION ALL
    SELECT 'alerts'::TEXT, COUNT(*)::BIGINT, MAX(updated_at)
    FROM alerts
    UNION ALL
    SELECT 'user_settings'::TEXT, COUNT(*)::BIGINT, MAX(updated_at)
    FROM user_settings;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ========================================
-- EXAMPLE USAGE
-- ========================================

/*
-- Check database health after reset
SELECT * FROM check_database_health();

-- If you need to reset a specific user's data:
-- SELECT reset_user_data('problem_user@example.com');

-- To see all users and their data:
SELECT 
    au.email as auth_email,
    up.email as profile_email,
    up.full_name,
    up.created_at,
    COUNT(g.id) as guardians,
    COUNT(a.id) as alerts
FROM auth.users au
LEFT JOIN user_profiles up ON up.auth_user_id = au.id
LEFT JOIN guardians g ON g.user_id = up.id
LEFT JOIN alerts a ON a.user_id = up.id
GROUP BY au.id, up.id
ORDER BY au.created_at DESC;
*/