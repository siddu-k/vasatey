-- ========================================
-- VASATEY APP - WORKING SQL SCHEMA
-- ========================================
-- Run this AFTER dropping all tables to create working schema

-- ========================================
-- 1. ENABLE EXTENSIONS
-- ========================================
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS postgis;

-- ========================================
-- 2. CREATE TABLES
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
    fcm_token TEXT,
    is_emergency_mode BOOLEAN DEFAULT false,
    profile_image_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
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
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
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
    is_resolved BOOLEAN DEFAULT false,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- User Settings Table
CREATE TABLE user_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES user_profiles(id) ON DELETE CASCADE UNIQUE,
    voice_detection_enabled BOOLEAN DEFAULT true,
    voice_sensitivity FLOAT DEFAULT 0.7,
    wake_word TEXT DEFAULT 'hey vasatey',
    auto_location_sharing BOOLEAN DEFAULT true,
    emergency_auto_call BOOLEAN DEFAULT false,
    notification_sound BOOLEAN DEFAULT true,
    notification_vibration BOOLEAN DEFAULT true,
    dark_mode BOOLEAN DEFAULT false,
    language TEXT DEFAULT 'en',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ========================================
-- 3. CREATE INDEXES
-- ========================================
CREATE INDEX idx_user_profiles_auth_user_id ON user_profiles(auth_user_id);
CREATE INDEX idx_user_profiles_email ON user_profiles(email);
CREATE INDEX idx_guardians_user_id ON guardians(user_id);
CREATE INDEX idx_alerts_user_id ON alerts(user_id);
CREATE INDEX idx_user_settings_user_id ON user_settings(user_id);

-- ========================================
-- 4. CREATE TRIGGER FUNCTION
-- ========================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ========================================
-- 5. CREATE TRIGGERS
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
-- 6. ENABLE ROW LEVEL SECURITY
-- ========================================
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE guardians ENABLE ROW LEVEL SECURITY;
ALTER TABLE alerts ENABLE ROW LEVEL SECURITY;
ALTER TABLE user_settings ENABLE ROW LEVEL SECURITY;

-- ========================================
-- 7. CREATE RLS POLICIES
-- ========================================

-- User Profiles Policies
CREATE POLICY "Users can manage their own profile" ON user_profiles
    USING (auth.uid() = auth_user_id);

-- Guardians Policies
CREATE POLICY "Users can manage their own guardians" ON guardians
    USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = guardians.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

-- Alerts Policies
CREATE POLICY "Users can manage their own alerts" ON alerts
    USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = alerts.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

-- User Settings Policies
CREATE POLICY "Users can manage their own settings" ON user_settings
    USING (
        EXISTS (
            SELECT 1 FROM user_profiles 
            WHERE user_profiles.id = user_settings.user_id 
            AND user_profiles.auth_user_id = auth.uid()
        )
    );

-- ========================================
-- 8. GRANT PERMISSIONS
-- ========================================
GRANT USAGE ON SCHEMA public TO authenticated;
GRANT ALL ON user_profiles TO authenticated;
GRANT ALL ON guardians TO authenticated;
GRANT ALL ON alerts TO authenticated;
GRANT ALL ON user_settings TO authenticated;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO authenticated;

-- ========================================
-- SCHEMA COMPLETE!
-- ========================================
SELECT 'WORKING SCHEMA CREATED SUCCESSFULLY!' as status;
SELECT table_name FROM information_schema.tables 
WHERE table_schema = 'public' 
ORDER BY table_name;