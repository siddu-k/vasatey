-- ========================================
-- VASATEY APP - COMPLETE DATABASE RESET
-- ========================================
-- This script completely resets the Supabase database
-- Run this in Supabase SQL Editor to start fresh

-- ========================================
-- 1. DROP ALL EXISTING TABLES AND FUNCTIONS
-- ========================================

-- Drop all triggers and tables with CASCADE to handle dependencies
DO $$ 
DECLARE
    r RECORD;
BEGIN
    -- Drop all triggers on existing tables
    FOR r IN (SELECT schemaname, tablename, triggername 
              FROM pg_triggers 
              WHERE schemaname = 'public' 
              AND triggername LIKE '%updated_at%')
    LOOP
        EXECUTE 'DROP TRIGGER IF EXISTS ' || quote_ident(r.triggername) || 
                ' ON ' || quote_ident(r.schemaname) || '.' || quote_ident(r.tablename) || ' CASCADE';
    END LOOP;
    
    -- Drop specific tables that might exist
    DROP TABLE IF EXISTS alerts CASCADE;
    DROP TABLE IF EXISTS guardians CASCADE;
    DROP TABLE IF EXISTS user_settings CASCADE;
    DROP TABLE IF EXISTS user_profiles CASCADE;
    DROP TABLE IF EXISTS users CASCADE; -- In case there's an old 'users' table
    
EXCEPTION
    WHEN others THEN
        RAISE NOTICE 'Error during cleanup: %', SQLERRM;
END $$;

-- Drop functions with CASCADE to remove dependencies
DROP FUNCTION IF EXISTS update_updated_at_column() CASCADE;
DROP FUNCTION IF EXISTS get_user_profile_by_auth_id(UUID) CASCADE;
DROP FUNCTION IF EXISTS create_default_user_settings(UUID) CASCADE;
DROP FUNCTION IF EXISTS reset_user_data(TEXT) CASCADE;
DROP FUNCTION IF EXISTS check_database_health() CASCADE;

-- Drop extensions if they exist (will recreate)
-- Note: Only drop if no other apps are using them
-- DROP EXTENSION IF EXISTS "uuid-ossp";
-- DROP EXTENSION IF EXISTS postgis;

-- ========================================
-- 2. ENABLE REQUIRED EXTENSIONS
-- ========================================

-- Enable UUID generation
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable PostGIS for location services
CREATE EXTENSION IF NOT EXISTS postgis;

-- ========================================
-- 3. CREATE FRESH TABLES
-- ========================================

-- User Profiles Table
CREATE TABLE user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    auth_user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT UNIQUE NOT NULL,
    full_name TEXT NOT NULL,
    phone_number TEXT,
    emergency_contact TEXT,
    medical_info TEXT,
    blood_type TEXT,
    allergies TEXT,
    current_location GEOGRAPHY(POINT, 4326),
    location_accuracy FLOAT,
    location_timestamp TIMESTAMPTZ,
    fcm_token TEXT,
    is_emergency_mode BOOLEAN DEFAULT false,
    profile_image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT valid_email CHECK (email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$'),
    CONSTRAINT valid_phone CHECK (phone_number IS NULL OR length(phone_number) >= 10),
    CONSTRAINT valid_blood_type CHECK (blood_type IS NULL OR blood_type IN ('A+', 'A-', 'B+', 'B-', 'AB+', 'AB-', 'O+', 'O-'))
);

-- Guardians Table
CREATE TABLE guardians (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES user_profiles(id) ON DELETE CASCADE,
    guardian_name TEXT NOT NULL,
    guardian_phone TEXT NOT NULL,
    guardian_email TEXT,
    relationship TEXT,
    is_primary BOOLEAN DEFAULT false,
    is_active BOOLEAN DEFAULT true,
    notification_preferences JSONB DEFAULT '{"sms": true, "email": true, "call": false}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT valid_guardian_phone CHECK (length(guardian_phone) >= 10),
    CONSTRAINT valid_guardian_email CHECK (guardian_email IS NULL OR guardian_email ~* '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$')
);

-- Alerts Table
CREATE TABLE alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES user_profiles(id) ON DELETE CASCADE,
    alert_type TEXT NOT NULL,
    severity TEXT DEFAULT 'medium',
    trigger_method TEXT NOT NULL,
    location GEOGRAPHY(POINT, 4326),
    location_address TEXT,
    alert_message TEXT,
    voice_recording_url TEXT,
    image_url TEXT,
    video_url TEXT,
    is_resolved BOOLEAN DEFAULT false,
    resolved_at TIMESTAMPTZ,
    resolved_by TEXT,
    resolution_notes TEXT,
    guardian_notifications JSONB DEFAULT '[]',
    emergency_services_notified BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT valid_alert_type CHECK (alert_type IN ('emergency', 'panic', 'medical', 'safety', 'test')),
    CONSTRAINT valid_severity CHECK (severity IN ('low', 'medium', 'high', 'critical')),
    CONSTRAINT valid_trigger_method CHECK (trigger_method IN ('voice', 'manual', 'automatic', 'gesture', 'schedule'))
);

-- User Settings Table
CREATE TABLE user_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES user_profiles(id) ON DELETE CASCADE UNIQUE,
    voice_detection_enabled BOOLEAN DEFAULT true,
    voice_sensitivity FLOAT DEFAULT 0.7,
    wake_word TEXT DEFAULT 'hey vasatey',
    auto_location_sharing BOOLEAN DEFAULT true,
    location_sharing_frequency INTEGER DEFAULT 30,
    emergency_auto_call BOOLEAN DEFAULT false,
    emergency_call_delay INTEGER DEFAULT 10,
    notification_sound BOOLEAN DEFAULT true,
    notification_vibration BOOLEAN DEFAULT true,
    dark_mode BOOLEAN DEFAULT false,
    language TEXT DEFAULT 'en',
    privacy_mode BOOLEAN DEFAULT false,
    data_retention_days INTEGER DEFAULT 90,
    backup_enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    CONSTRAINT valid_sensitivity CHECK (voice_sensitivity >= 0.1 AND voice_sensitivity <= 1.0),
    CONSTRAINT valid_frequency CHECK (location_sharing_frequency >= 5 AND location_sharing_frequency <= 3600),
    CONSTRAINT valid_call_delay CHECK (emergency_call_delay >= 5 AND emergency_call_delay <= 60),
    CONSTRAINT valid_retention CHECK (data_retention_days >= 30 AND data_retention_days <= 365)
);

-- ========================================
-- 4. CREATE INDEXES FOR PERFORMANCE
-- ========================================

-- User Profiles indexes
CREATE INDEX idx_user_profiles_auth_user_id ON user_profiles(auth_user_id);
CREATE INDEX idx_user_profiles_email ON user_profiles(email);
CREATE INDEX idx_user_profiles_phone ON user_profiles(phone_number);
CREATE INDEX idx_user_profiles_emergency_mode ON user_profiles(is_emergency_mode);
CREATE INDEX idx_user_profiles_location ON user_profiles USING GIST(current_location);

-- Guardians indexes
CREATE INDEX idx_guardians_user_id ON guardians(user_id);
CREATE INDEX idx_guardians_primary ON guardians(user_id, is_primary) WHERE is_primary = true;
CREATE INDEX idx_guardians_active ON guardians(user_id, is_active) WHERE is_active = true;

-- Alerts indexes
CREATE INDEX idx_alerts_user_id ON alerts(user_id);
CREATE INDEX idx_alerts_created_at ON alerts(created_at DESC);
CREATE INDEX idx_alerts_type_severity ON alerts(alert_type, severity);
CREATE INDEX idx_alerts_unresolved ON alerts(user_id, is_resolved) WHERE is_resolved = false;
CREATE INDEX idx_alerts_location ON alerts USING GIST(location);

-- User Settings indexes
CREATE INDEX idx_user_settings_user_id ON user_settings(user_id);

-- ========================================
-- 5. CREATE UPDATED_AT TRIGGER FUNCTION
-- ========================================

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ========================================
-- 6. CREATE TRIGGERS
-- ========================================

CREATE TRIGGER update_user_profiles_updated_at
    BEFORE UPDATE ON user_profiles
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_guardians_updated_at
    BEFORE UPDATE ON guardians
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_alerts_updated_at
    BEFORE UPDATE ON alerts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_settings_updated_at
    BEFORE UPDATE ON user_settings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ========================================
-- 7. ROW LEVEL SECURITY (RLS) POLICIES
-- ========================================

-- Enable RLS on all tables
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE guardians ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;

-- User Profiles Policies
CREATE POLICY "Users can view their own profile" ON user_profiles
    FOR SELECT USING (auth.uid() = auth_user_id);

CREATE POLICY "Users can insert their own profile" ON user_profiles
    FOR INSERT WITH CHECK (auth.uid() = auth_user_id);

CREATE POLICY "Users can update their own profile" ON user_profiles
    FOR UPDATE USING (auth.uid() = auth_user_id);

CREATE POLICY "Users can delete their own profile" ON user_profiles
    FOR DELETE USING (auth.uid() = auth_user_id);

-- Guardians Policies
CREATE POLICY "Users can view their own guardians" ON guardians
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = guardians.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

CREATE POLICY "Users can insert their own guardians" ON guardians
    FOR INSERT WITH CHECK (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = guardians.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

CREATE POLICY "Users can update their own guardians" ON guardians
    FOR UPDATE USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = guardians.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

CREATE POLICY "Users can delete their own guardians" ON guardians
    FOR DELETE USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = guardians.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

-- Alerts Policies
CREATE POLICY "Users can view their own alerts" ON alerts
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = alerts.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

CREATE POLICY "Users can insert their own alerts" ON alerts
    FOR INSERT WITH CHECK (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = alerts.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

CREATE POLICY "Users can update their own alerts" ON alerts
    FOR UPDATE USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = alerts.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

-- User Settings Policies
CREATE POLICY "Users can view their own settings" ON user_settings
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = user_settings.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

CREATE POLICY "Users can insert their own settings" ON user_settings
    FOR INSERT WITH CHECK (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = user_settings.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

CREATE POLICY "Users can update their own settings" ON user_settings
    FOR UPDATE USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = user_settings.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

-- ========================================
-- 8. CREATE UTILITY FUNCTIONS
-- ========================================

-- Function to get user profile by auth ID
CREATE OR REPLACE FUNCTION get_user_profile_by_auth_id(auth_id UUID)
RETURNS TABLE (
    id UUID,
    email TEXT,
    full_name TEXT,
    phone_number TEXT,
    emergency_contact TEXT,
    medical_info TEXT,
    blood_type TEXT,
    allergies TEXT,
    current_location GEOGRAPHY,
    fcm_token TEXT,
    is_emergency_mode BOOLEAN,
    profile_image_url TEXT,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        up.id,
        up.email,
        up.full_name,
        up.phone_number,
        up.emergency_contact,
        up.medical_info,
        up.blood_type,
        up.allergies,
        up.current_location,
        up.fcm_token,
        up.is_emergency_mode,
        up.profile_image_url,
        up.created_at,
        up.updated_at
    FROM user_profiles up
    WHERE up.auth_user_id = auth_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to create default user settings
CREATE OR REPLACE FUNCTION create_default_user_settings(profile_user_id UUID)
RETURNS UUID AS $$
DECLARE
    settings_id UUID;
BEGIN
    INSERT INTO user_settings (user_id)
    VALUES (profile_user_id)
    RETURNING id INTO settings_id;
    
    RETURN settings_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ========================================
-- 9. GRANT NECESSARY PERMISSIONS
-- ========================================

-- Grant usage on schema
GRANT USAGE ON SCHEMA public TO authenticated;

-- Grant permissions on tables
GRANT ALL ON user_profiles TO authenticated;
GRANT ALL ON guardians TO authenticated;
GRANT ALL ON alerts TO authenticated;
GRANT ALL ON user_settings TO authenticated;

-- Grant permissions on sequences
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO authenticated;

-- ========================================
-- RESET COMPLETE!
-- ========================================

-- Verify tables were created
SELECT 'DATABASE RESET COMPLETE! Tables created:' as status;
SELECT table_name, table_type 
FROM information_schema.tables 
WHERE table_schema = 'public' 
AND table_name IN ('user_profiles', 'guardians', 'alerts', 'user_settings')
ORDER BY table_name;