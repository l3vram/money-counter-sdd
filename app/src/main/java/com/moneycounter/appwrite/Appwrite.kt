package com.moneycounter.appwrite

import android.content.Context
import io.appwrite.Client

/**
 * Appwrite client configuration.
 *
 * Endpoint and project come from the Appwrite.io Console
 * (project "My first project"; created from the Fra Cloud dashboard).
 */
object Appwrite {
    const val ENDPOINT = "https://fra.cloud.appwrite.io/v1"
    const val PROJECT_ID = "6aa332f40001072d0747"

    /** Database (from the Console > Databases section) holding the app tables. */
    const val DATABASE_ID = "main"
    const val USERS_TABLE = "users"
    const val MEMBERS_TABLE = "members"

    lateinit var client: Client
        private set

    /** Initializes the [client] once. Safe to call multiple times. */
    fun init(context: Context) {
        if (::client.isInitialized) return
        client = Client(context.applicationContext)
            .setEndpoint(ENDPOINT)
            .setProject(PROJECT_ID)
    }
}