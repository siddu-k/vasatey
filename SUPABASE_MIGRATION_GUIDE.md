# Vasatey Migration to Supabase - Setup Guide

## 🎉 Migration Complete!

Your Vasatey app has been successfully migrated from Firebase to Supabase for authentication and database operations. Firebase Messaging is retained for push notifications.

## 🛠️ Setup Required

### 1. Create Supabase Project

1. Go to [https://supabase.com](https://supabase.com)
2. Create a new account or sign in
3. Create a new project
4. Note your project URL and anon key from Project Settings > API

### 2. Database Schema Setup

Execute these SQL commands in your Supabase SQL Editor:

```sql
-- Enable RLS (Row Level Security)
ALTER TABLE IF EXISTS public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE IF EXISTS public.alerts ENABLE ROW LEVEL SECURITY;

-- Create users table
CREATE TABLE IF NOT EXISTS public.users (
    id UUID REFERENCES auth.users(id) PRIMARY KEY,
    email VARCHAR UNIQUE NOT NULL,
    username VARCHAR NOT NULL,
    mobile_number VARCHAR NOT NULL,
    fcm_token VARCHAR,
    security_questions JSONB NOT NULL,
    guardians TEXT[] DEFAULT '{}',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create alerts table
CREATE TABLE IF NOT EXISTS public.alerts (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    user_email VARCHAR NOT NULL,
    user_name VARCHAR NOT NULL,
    mobile_number VARCHAR,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    status VARCHAR DEFAULT 'active'
);

-- RLS Policies for users table
CREATE POLICY "Users can view own data" ON public.users
    FOR SELECT USING (auth.uid() = id);

CREATE POLICY "Users can insert own data" ON public.users
    FOR INSERT WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update own data" ON public.users
    FOR UPDATE USING (auth.uid() = id);

-- RLS Policies for alerts table
CREATE POLICY "Users can view own alerts" ON public.alerts
    FOR SELECT USING (auth.uid() = user_id);

CREATE POLICY "Users can insert own alerts" ON public.alerts
    FOR INSERT WITH CHECK (auth.uid() = user_id);

-- Create function to update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create trigger for users table
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON public.users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
```

### 3. Update Supabase Configuration

In `SupabaseClient.kt`, replace the placeholder values:

```kotlin
private const val SUPABASE_URL = "YOUR_SUPABASE_PROJECT_URL"
private const val SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY"
```

With your actual Supabase project URL and anon key.

## 🔄 Migration Changes Made

### ✅ Dependencies Updated
- Removed Firebase Auth and Firestore dependencies
- Added Supabase dependencies (gotrue-kt, postgrest-kt, storage-kt)
- Added Kotlin serialization support
- Kept Firebase Messaging for push notifications

### ✅ Authentication Migrated
- `LoginActivity` - Now uses Supabase Auth
- `SignupActivity` - Now uses Supabase Auth with user profile creation
- `MainActivity` - Updated logout functionality
- `SplashActivity` - Updated user session check

### ✅ Database Operations Migrated
- `ListeningService` - Alert saving and guardian management
- `AlertsFragment` - Alert retrieval and display
- `GuardianSettingsFragment` - Guardian management
- FCM token management integrated with Supabase

### ✅ New Helper Classes Created
- `SupabaseClient.kt` - Centralized Supabase configuration
- `SupabaseAuthHelper.kt` - Authentication operations
- `SupabaseDatabaseHelper.kt` - Database operations

## 🧪 Testing Checklist

1. **Authentication**
   - [ ] User registration works
   - [ ] User login works
   - [ ] User logout works
   - [ ] Session persistence works

2. **Guardian Management**
   - [ ] Add guardian functionality
   - [ ] Remove guardian functionality
   - [ ] Guardian list display

3. **Emergency Alerts**
   - [ ] Voice detection triggers alerts
   - [ ] Location is captured and sent
   - [ ] Guardians receive notifications
   - [ ] Alerts are saved to database

4. **Alerts History**
   - [ ] Alert history displays correctly
   - [ ] Alert details navigation works

## 🚀 Deployment Notes

- Keep your `google-services.json` file for Firebase Messaging
- Update your Vercel notification service if needed to handle the new data structure
- Test thoroughly in development before production deployment
- Consider data migration from Firebase if you have existing users

## 📱 App Structure Preserved

The migration maintained your existing app structure:
- All Activities and Fragments remain unchanged in functionality
- UI/UX remains identical
- Voice detection and notification systems unchanged
- Only the backend data layer was migrated

Your Vasatey emergency detection app is now powered by Supabase! 🎉