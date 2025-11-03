# Vasatey Supabase Schema - API Reference

## 📋 Overview
This document shows how your Kotlin data models map to the Supabase database schema.

---

## 🗄️ Table Mappings

### 1. UserProfile ↔ `users` table

**Kotlin Model:**
```kotlin
data class UserProfile(
    val id: String? = null,           // UUID
    val name: String = "",           // VARCHAR(255)
    val email: String = "",          // VARCHAR(255) UNIQUE
    val fcmToken: String? = null,    // TEXT
    val securityQuestions: Map<String, String> = mapOf(), // JSONB
    val guardians: List<String> = listOf(),              // TEXT[]
    val createdAt: String? = null    // TIMESTAMP
)
```

**Database Columns:**
- `id` → UUID PRIMARY KEY
- `email` → VARCHAR(255) UNIQUE NOT NULL  
- `name` → VARCHAR(255) NOT NULL DEFAULT ''
- `fcm_token` → TEXT
- `security_questions` → JSONB DEFAULT '{}'
- `guardians` → TEXT[] DEFAULT '{}'
- `created_at` → TIMESTAMP WITH TIME ZONE
- `updated_at` → TIMESTAMP WITH TIME ZONE

---

### 2. UserSettings ↔ `user_settings` table

**Kotlin Model:**
```kotlin
data class UserSettings(
    val userId: String = "",         // UUID REFERENCES users(id)
    val wakeWord: String = "help-me", // VARCHAR(50)
    val accessKey: String = "",      // TEXT
    val listeningEnabled: Boolean = true, // BOOLEAN
    val createdAt: String? = null,   // TIMESTAMP
    val updatedAt: String? = null    // TIMESTAMP
)
```

**Database Columns:**
- `id` → UUID PRIMARY KEY
- `user_id` → UUID REFERENCES users(id) ON DELETE CASCADE
- `wake_word` → VARCHAR(50) DEFAULT 'help-me'
- `access_key` → TEXT DEFAULT ''
- `listening_enabled` → BOOLEAN DEFAULT true
- `created_at` → TIMESTAMP WITH TIME ZONE
- `updated_at` → TIMESTAMP WITH TIME ZONE

---

### 3. Guardian ↔ `guardians` table

**Kotlin Model:**
```kotlin
data class Guardian(
    val id: String? = null,          // UUID
    val userId: String = "",         // UUID REFERENCES users(id)
    val email: String = "",          // VARCHAR(255)
    val name: String = "",           // VARCHAR(255)
    val phone: String? = null,       // VARCHAR(20)
    val createdAt: String? = null    // TIMESTAMP
)
```

**Database Columns:**
- `id` → UUID PRIMARY KEY
- `user_id` → UUID REFERENCES users(id) ON DELETE CASCADE
- `email` → VARCHAR(255) NOT NULL
- `name` → VARCHAR(255) DEFAULT ''
- `phone` → VARCHAR(20)
- `created_at` → TIMESTAMP WITH TIME ZONE

---

### 4. Alert ↔ `alerts` table

**Kotlin Model:**
```kotlin
data class Alert(
    val id: String? = null,          // UUID
    val user_id: String = "",        // UUID REFERENCES users(id)
    val user_name: String = "",      // VARCHAR(255)
    val user_email: String = "",     // VARCHAR(255)
    val mobile_number: String = "",  // VARCHAR(20)
    val latitude: Double = 0.0,      // DOUBLE PRECISION
    val longitude: Double = 0.0,     // DOUBLE PRECISION
    val timestamp: String = "",      // TIMESTAMP
    val message: String = ""         // TEXT
)
```

**Database Columns:**
- `id` → UUID PRIMARY KEY
- `user_id` → UUID REFERENCES users(id) ON DELETE CASCADE
- `user_name` → VARCHAR(255) NOT NULL
- `user_email` → VARCHAR(255) NOT NULL
- `mobile_number` → VARCHAR(20) DEFAULT ''
- `latitude` → DOUBLE PRECISION DEFAULT 0.0
- `longitude` → DOUBLE PRECISION DEFAULT 0.0
- `location` → GEOGRAPHY(POINT) -- Auto-generated from lat/lng
- `timestamp` → TIMESTAMP WITH TIME ZONE DEFAULT NOW()
- `message` → TEXT DEFAULT ''
- `status` → VARCHAR(20) DEFAULT 'active'
- `created_at` → TIMESTAMP WITH TIME ZONE

---

## 🔒 Security Features

### Row Level Security (RLS)
- **Users**: Can only access their own data
- **User Settings**: Linked to user permissions
- **Guardians**: Users manage their own, guardians can view alerts
- **Alerts**: Users see own alerts, guardians see alerts from protected users

### Policies Applied:
- Users can view/update own profile
- Users can manage own settings and guardians
- Guardians can view alerts sent to them
- Authenticated users can lookup other users by email (for guardian setup)

---

## 🚀 Advanced Features

### 1. Spatial Queries (PostGIS)
```sql
-- Find alerts within 5km radius
SELECT * FROM get_nearby_alerts(40.7128, -74.0060, 5000);
```

### 2. Auto-triggers
- **updated_at**: Automatically updates on record changes
- **location**: Auto-generates PostGIS point from lat/lng
- **user_settings**: Auto-creates when user registers

### 3. Performance Indexes
- Spatial index on alert locations
- Email lookups optimized
- Guardian relationships indexed
- FCM token lookups optimized

---

## 📊 Views Available

### `user_dashboard`
Complete user overview with counts:
```sql
SELECT * FROM user_dashboard WHERE id = 'user-uuid';
```

### `guardian_alerts`
All active alerts with guardian info:
```sql
SELECT * FROM guardian_alerts WHERE guardian_email = 'guardian@email.com';
```

---

## 🔧 Setup Instructions

1. **Create Database**: Run the `supabase_schema.sql` in your Supabase SQL Editor
2. **Enable Extensions**: PostGIS and UUID extensions will be enabled
3. **Configure RLS**: Row Level Security policies are automatically applied
4. **Test Connection**: Use your existing Kotlin code - it should work immediately!

---

## 🎯 API Compatibility

Your existing `SupabaseDatabaseHelper.kt` and `SupabaseAuthHelper.kt` are fully compatible with this schema. The table and column names match your Kotlin models exactly.

**Key Benefits:**
- ✅ Zero code changes required
- ✅ Enhanced security with RLS
- ✅ Spatial queries for location features
- ✅ Automatic data validation
- ✅ Performance optimized with indexes
- ✅ Scalable for future features