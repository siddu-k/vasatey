-- =====================================================
-- VASATEY APP - SUPABASE DATABASE SCHEMA
-- Emergency Detection & Alert System
-- =====================================================

-- Enable necessary extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- =====================================================
-- 1. USERS TABLE (UserProfile model)
-- =====================================================
CREATE TABLE public.users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL DEFAULT '',
    fcm_token TEXT,
    security_questions JSONB DEFAULT '{}',
    guardians TEXT[] DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Add RLS (Row Level Security)
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- Users can only see their own data
CREATE POLICY "Users can view own profile" ON public.users
    FOR SELECT USING (auth.uid() = id);

CREATE POLICY "Users can update own profile" ON public.users
    FOR UPDATE USING (auth.uid() = id);

CREATE POLICY "Users can insert own profile" ON public.users
    FOR INSERT WITH CHECK (auth.uid() = id);

-- =====================================================
-- 2. USER_SETTINGS TABLE (UserSettings model)
-- =====================================================
CREATE TABLE public.user_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    wake_word VARCHAR(50) DEFAULT 'help-me',
    access_key TEXT DEFAULT '',
    listening_enabled BOOLEAN DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Ensure one settings record per user
    UNIQUE(user_id)
);

ALTER TABLE public.user_settings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can manage own settings" ON public.user_settings
    FOR ALL USING (
        user_id = auth.uid() OR 
        user_id IN (SELECT id FROM public.users WHERE auth.uid() = id)
    );

-- =====================================================
-- 3. GUARDIANS TABLE (Guardian model)
-- =====================================================
CREATE TABLE public.guardians (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    email VARCHAR(255) NOT NULL,
    name VARCHAR(255) DEFAULT '',
    phone VARCHAR(20),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    
    -- Prevent duplicate guardian relationships
    UNIQUE(user_id, email)
);

ALTER TABLE public.guardians ENABLE ROW LEVEL SECURITY;

-- Users can manage their own guardians
CREATE POLICY "Users can manage own guardians" ON public.guardians
    FOR ALL USING (
        user_id = auth.uid() OR 
        user_id IN (SELECT id FROM public.users WHERE auth.uid() = id)
    );

-- Guardians can view alerts sent to them
CREATE POLICY "Guardians can view their alerts" ON public.guardians
    FOR SELECT USING (
        email IN (
            SELECT email FROM public.users WHERE id = auth.uid()
        )
    );

-- =====================================================
-- 4. ALERTS TABLE (Alert model)
-- =====================================================
CREATE TABLE public.alerts (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES public.users(id) ON DELETE CASCADE,
    user_name VARCHAR(255) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    mobile_number VARCHAR(20) DEFAULT '',
    latitude DOUBLE PRECISION DEFAULT 0.0,
    longitude DOUBLE PRECISION DEFAULT 0.0,
    location GEOGRAPHY(POINT),  -- PostGIS point for efficient location queries
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    message TEXT DEFAULT '',
    status VARCHAR(20) DEFAULT 'active',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create spatial index for location queries
CREATE INDEX alerts_location_idx ON public.alerts USING GIST (location);

-- Create indexes for common queries
CREATE INDEX alerts_user_id_idx ON public.alerts(user_id);
CREATE INDEX alerts_timestamp_idx ON public.alerts(timestamp DESC);
CREATE INDEX alerts_status_idx ON public.alerts(status);

ALTER TABLE public.alerts ENABLE ROW LEVEL SECURITY;

-- Users can view their own alerts
CREATE POLICY "Users can view own alerts" ON public.alerts
    FOR SELECT USING (
        user_id = auth.uid() OR 
        user_id IN (SELECT id FROM public.users WHERE auth.uid() = id)
    );

-- Users can create their own alerts
CREATE POLICY "Users can create own alerts" ON public.alerts
    FOR INSERT WITH CHECK (
        user_id = auth.uid() OR 
        user_id IN (SELECT id FROM public.users WHERE auth.uid() = id)
    );

-- Guardians can view alerts from users they guard
CREATE POLICY "Guardians can view alerts from protected users" ON public.alerts
    FOR SELECT USING (
        user_email IN (
            SELECT u.email 
            FROM public.users u
            JOIN public.guardians g ON g.user_id = u.id
            WHERE g.email IN (
                SELECT email FROM public.users WHERE id = auth.uid()
            )
        )
    );

-- =====================================================
-- 5. NOTIFICATION_LOGS TABLE (for FCM tracking)
-- =====================================================
CREATE TABLE public.notification_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    alert_id UUID REFERENCES public.alerts(id) ON DELETE CASCADE,
    recipient_email VARCHAR(255) NOT NULL,
    fcm_token TEXT,
    notification_type VARCHAR(50) DEFAULT 'emergency_alert',
    status VARCHAR(20) DEFAULT 'sent', -- sent, delivered, failed
    sent_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    delivered_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT
);

CREATE INDEX notification_logs_alert_id_idx ON public.notification_logs(alert_id);
CREATE INDEX notification_logs_recipient_idx ON public.notification_logs(recipient_email);

-- =====================================================
-- 6. FUNCTIONS AND TRIGGERS
-- =====================================================

-- Function to automatically update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply updated_at triggers
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_user_settings_updated_at BEFORE UPDATE ON public.user_settings
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Function to automatically create location point from lat/lng
CREATE OR REPLACE FUNCTION update_alert_location()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL THEN
        NEW.location = ST_MakePoint(NEW.longitude, NEW.latitude)::geography;
    END IF;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_alerts_location BEFORE INSERT OR UPDATE ON public.alerts
    FOR EACH ROW EXECUTE FUNCTION update_alert_location();

-- Function to automatically create user settings when user is created
CREATE OR REPLACE FUNCTION create_user_settings()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.user_settings (user_id, wake_word, listening_enabled)
    VALUES (NEW.id, 'help-me', true);
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER create_user_settings_trigger AFTER INSERT ON public.users
    FOR EACH ROW EXECUTE FUNCTION create_user_settings();

-- =====================================================
-- 7. VIEWS FOR COMMON QUERIES
-- =====================================================

-- View for user dashboard with alert counts
CREATE VIEW user_dashboard AS
SELECT 
    u.id,
    u.name,
    u.email,
    us.listening_enabled,
    us.wake_word,
    COUNT(DISTINCT g.id) as guardian_count,
    COUNT(DISTINCT a.id) as alert_count,
    MAX(a.created_at) as last_alert_at
FROM public.users u
LEFT JOIN public.user_settings us ON us.user_id = u.id
LEFT JOIN public.guardians g ON g.user_id = u.id
LEFT JOIN public.alerts a ON a.user_id = u.id
GROUP BY u.id, u.name, u.email, us.listening_enabled, us.wake_word;

-- View for guardian notifications
CREATE VIEW guardian_alerts AS
SELECT 
    a.*,
    g.email as guardian_email,
    g.name as guardian_name
FROM public.alerts a
JOIN public.guardians g ON g.user_id = a.user_id
WHERE a.status = 'active'
ORDER BY a.created_at DESC;

-- =====================================================
-- 8. SAMPLE DATA FUNCTIONS
-- =====================================================

-- Function to get nearby alerts (within radius in meters)
CREATE OR REPLACE FUNCTION get_nearby_alerts(
    center_lat DOUBLE PRECISION,
    center_lng DOUBLE PRECISION,
    radius_meters INTEGER DEFAULT 5000
)
RETURNS TABLE (
    id UUID,
    user_name VARCHAR(255),
    user_email VARCHAR(255),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    distance_meters DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        a.id,
        a.user_name,
        a.user_email,
        a.latitude,
        a.longitude,
        ST_Distance(
            a.location,
            ST_MakePoint(center_lng, center_lat)::geography
        ) as distance_meters,
        a.created_at
    FROM public.alerts a
    WHERE a.location IS NOT NULL
    AND ST_DWithin(
        a.location,
        ST_MakePoint(center_lng, center_lat)::geography,
        radius_meters
    )
    AND a.status = 'active'
    ORDER BY distance_meters;
END;
$$ language 'plpgsql';

-- =====================================================
-- 9. INDEXES FOR PERFORMANCE
-- =====================================================

-- Additional indexes for better performance
CREATE INDEX users_email_idx ON public.users(email);
CREATE INDEX users_fcm_token_idx ON public.users(fcm_token) WHERE fcm_token IS NOT NULL;
CREATE INDEX guardians_user_id_email_idx ON public.guardians(user_id, email);
CREATE INDEX user_settings_user_id_idx ON public.user_settings(user_id);

-- =====================================================
-- 10. SECURITY POLICIES FOR API ACCESS
-- =====================================================

-- Allow authenticated users to read public user info (for guardian lookup)
CREATE POLICY "Authenticated users can lookup users by email" ON public.users
    FOR SELECT USING (
        auth.role() = 'authenticated' AND 
        (email IS NOT NULL OR id = auth.uid())
    );

-- =====================================================
-- 11. COMMENTS FOR DOCUMENTATION
-- =====================================================

COMMENT ON TABLE public.users IS 'User profiles and authentication data';
COMMENT ON TABLE public.user_settings IS 'User preferences for voice detection and alerts';
COMMENT ON TABLE public.guardians IS 'Emergency contacts for each user';
COMMENT ON TABLE public.alerts IS 'Emergency alerts with location data';
COMMENT ON TABLE public.notification_logs IS 'FCM notification delivery tracking';

COMMENT ON COLUMN public.alerts.location IS 'PostGIS geography point for efficient spatial queries';
COMMENT ON COLUMN public.users.security_questions IS 'JSON object with security question answers';
COMMENT ON COLUMN public.users.guardians IS 'Array of guardian email addresses (legacy field)';

-- =====================================================
-- SCHEMA COMPLETE
-- =====================================================