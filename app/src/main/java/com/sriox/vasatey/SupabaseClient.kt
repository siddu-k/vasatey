package com.sriox.vasatey

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseClient {
    
    // Updated Supabase project credentials
    private const val SUPABASE_URL = "https://hjxmjmdqvgiaeourpbbc.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImhqeG1qbWRxdmdpYWVvdXJwYmJjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjIxMzg5NjEsImV4cCI6MjA3NzcxNDk2MX0.mVibzZbffS1JfCVa7yW8yndG_e7iYI72vgo_9h3SCiQ"
    
    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}