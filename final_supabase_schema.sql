-- =====================================================
-- FINAL SUPABASE SCHEMA FOR VASATEY APP
-- Cross-checked with all Kotlin models and database operations
-- =====================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Enable PostGIS for location features (if needed later)
CREATE EXTENSION IF NOT EXISTS postgis;

-- =====================================================
-- TABLE 1: user_profiles
-- Used in: SupabaseAuthHelper.kt and SupabaseDatabaseHelper.kt
-- =====================================================
CREATE TABLE public.user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    auth_user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    full_name TEXT NOT NULL,
    phone_number TEXT,
    emergency_contact TEXT,
    medical_info TEXT,
    blood_type TEXT,
    allergies TEXT,
    fcm_token TEXT,
    is_emergency_mode BOOLEAN DEFAULT FALSE,
    profile_image_url TEXT,
    last_known_latitude DOUBLE PRECISION,
    last_known_longitude DOUBLE PRECISION,
    last_location_update TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =====================================================
-- TABLE 2: user_settings  
-- Used in: SupabaseDatabaseHelper.kt (saveUserSettings, getUserSettings)
-- =====================================================
CREATE TABLE public.user_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    voice_detection_enabled BOOLEAN DEFAULT TRUE,
    voice_sensitivity REAL DEFAULT 0.7,
    wake_word TEXT DEFAULT 'hey vasatey',
    picovoice_access_key TEXT,
    auto_location_sharing BOOLEAN DEFAULT TRUE,
    emergency_auto_call BOOLEAN DEFAULT FALSE,
    notification_sound BOOLEAN DEFAULT TRUE,
    notification_vibration BOOLEAN DEFAULT TRUE,
    dark_mode BOOLEAN DEFAULT FALSE,
    language TEXT DEFAULT 'en',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =====================================================
-- TABLE 3: guardians
-- Used in: SupabaseDatabaseHelper.kt (addGuardian, getGuardians, deleteGuardian)  
-- =====================================================
CREATE TABLE public.guardians (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    guardian_name TEXT NOT NULL,
    guardian_phone TEXT NOT NULL,
    guardian_email TEXT,
    relationship TEXT,
    is_primary BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =====================================================
-- TABLE 4: alerts
-- Used in: SupabaseDatabaseHelper.kt (saveAlert, getUserAlerts)
-- =====================================================
CREATE TABLE public.alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    alert_type TEXT NOT NULL,
    severity TEXT DEFAULT 'medium',
    trigger_method TEXT NOT NULL,
    location_address TEXT,
    location_latitude DOUBLE PRECISION,
    location_longitude DOUBLE PRECISION,
    alert_message TEXT,
    is_resolved BOOLEAN DEFAULT FALSE,
    resolved_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- =====================================================
-- INDEXES FOR PERFORMANCE
-- =====================================================
CREATE INDEX idx_user_profiles_auth_user_id ON public.user_profiles(auth_user_id);
CREATE INDEX idx_user_profiles_email ON public.user_profiles(email);
CREATE INDEX idx_user_profiles_location ON public.user_profiles(last_known_latitude, last_known_longitude);
CREATE INDEX idx_user_settings_user_id ON public.user_settings(user_id);
CREATE INDEX idx_guardians_user_id ON public.guardians(user_id);
CREATE INDEX idx_guardians_is_primary ON public.guardians(is_primary);
CREATE INDEX idx_alerts_user_id ON public.alerts(user_id);
CREATE INDEX idx_alerts_location ON public.alerts(location_latitude, location_longitude);
CREATE INDEX idx_alerts_created_at ON public.alerts(created_at);
CREATE INDEX idx_alerts_is_resolved ON public.alerts(is_resolved);

-- =====================================================
-- AUTO-UPDATE TRIGGERS
-- =====================================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_user_profiles_updated_at BEFORE UPDATE ON public.user_profiles FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER update_user_settings_updated_at BEFORE UPDATE ON public.user_settings FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER update_guardians_updated_at BEFORE UPDATE ON public.guardians FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();
CREATE TRIGGER update_alerts_updated_at BEFORE UPDATE ON public.alerts FOR EACH ROW EXECUTE PROCEDURE update_updated_at_column();

-- =====================================================
-- ROW LEVEL SECURITY (RLS)
-- =====================================================
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.guardians ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.alerts ENABLE ROW LEVEL SECURITY;

-- Policy: Users can only access their own data
CREATE POLICY "Users can view own profile" ON public.user_profiles FOR SELECT USING (auth.uid() = auth_user_id);
CREATE POLICY "Users can insert own profile" ON public.user_profiles FOR INSERT WITH CHECK (auth.uid() = auth_user_id);
CREATE POLICY "Users can update own profile" ON public.user_profiles FOR UPDATE USING (auth.uid() = auth_user_id);

CREATE POLICY "Users can view own settings" ON public.user_settings FOR SELECT USING (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));
CREATE POLICY "Users can insert own settings" ON public.user_settings FOR INSERT WITH CHECK (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));
CREATE POLICY "Users can update own settings" ON public.user_settings FOR UPDATE USING (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));

CREATE POLICY "Users can view own guardians" ON public.guardians FOR SELECT USING (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));
CREATE POLICY "Users can insert own guardians" ON public.guardians FOR INSERT WITH CHECK (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));
CREATE POLICY "Users can update own guardians" ON public.guardians FOR UPDATE USING (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));
CREATE POLICY "Users can delete own guardians" ON public.guardians FOR DELETE USING (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));

CREATE POLICY "Users can view own alerts" ON public.alerts FOR SELECT USING (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));
CREATE POLICY "Users can insert own alerts" ON public.alerts FOR INSERT WITH CHECK (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));
CREATE POLICY "Users can update own alerts" ON public.alerts FOR UPDATE USING (user_id IN (SELECT id FROM public.user_profiles WHERE auth_user_id = auth.uid()));

-- =====================================================
-- CONSTRAINTS
-- =====================================================
ALTER TABLE public.guardians ADD CONSTRAINT chk_guardian_phone_format CHECK (guardian_phone ~ '^[+]?[0-9\s\-\(\)]+$');
ALTER TABLE public.user_profiles ADD CONSTRAINT chk_email_format CHECK (email ~ '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$');
ALTER TABLE public.alerts ADD CONSTRAINT chk_severity CHECK (severity IN ('low', 'medium', 'high', 'critical'));
ALTER TABLE public.user_settings ADD CONSTRAINT chk_voice_sensitivity CHECK (voice_sensitivity BETWEEN 0.0 AND 1.0);

-- =====================================================
-- SUMMARY OF TABLES USED BY YOUR APP:
-- =====================================================
-- 1. user_profiles - Main user data (SupabaseAuthHelper.kt, SupabaseDatabaseHelper.kt)
-- 2. user_settings - App settings (SupabaseDatabaseHelper.kt - saveUserSettings, getUserSettings)  
-- 3. guardians - Emergency contacts (SupabaseDatabaseHelper.kt - addGuardian, getGuardians, deleteGuardian)
-- 4. alerts - Emergency alerts (SupabaseDatabaseHelper.kt - saveAlert, getUserAlerts)
--
-- All column names match exactly with @SerialName annotations in Models.kt
-- All table names match exactly with .from("table_name") calls in your Kotlin code
-- =====================================================