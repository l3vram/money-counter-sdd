package com.moneycounter.appwrite

import com.moneycounter.auth.mapAuthError
import com.moneycounter.signup.SignupFields
import com.moneycounter.signup.SignupRepository
import com.moneycounter.signup.SignupRequest
import com.moneycounter.signup.signupRequestToPayload
import io.appwrite.exceptions.AppwriteException
import io.appwrite.services.TablesDB

/**
 * Appwrite-backed signup requests. Writes the `signups/{uid}` row and reads the
 * superuser WhatsApp number from the single `settings` row (id = "app").
 */
class AppwriteSignupRepository(
    private val tables: TablesDB = TablesDB(Appwrite.client),
    private val databaseId: String = Appwrite.DATABASE_ID,
    private val signupsTable: String = Appwrite.SIGNUPS_TABLE,
    private val settingsTable: String = Appwrite.SETTINGS_TABLE,
    private val settingsRowId: String = Appwrite.SETTINGS_ROW_ID
) : SignupRepository {

    override suspend fun submit(request: SignupRequest) {
        try {
            tables.createRow(databaseId, signupsTable, request.uid, signupRequestToPayload(request))
        } catch (e: Exception) {
            throw Exception(mapAuthError(e), e)
        }
    }

    override suspend fun settingsSuperuserWhatsapp(): String? {
        val row = try {
            tables.getRow(databaseId, settingsTable, settingsRowId)
        } catch (e: AppwriteException) {
            if (e.code == 404) return null
            throw e
        }
        return row.data["superuserWhatsapp"] as? String
    }

    override suspend fun setMustChangePassword(uid: String, flag: Boolean) {
        try {
            tables.updateRow(
                databaseId,
                signupsTable,
                uid,
                mapOf(SignupFields.MUST_CHANGE_PASSWORD to flag)
            )
        } catch (e: Exception) {
            throw Exception(mapAuthError(e), e)
        }
    }

    override suspend fun readMustChangePassword(uid: String): Boolean {
        val row = try {
            tables.getRow(databaseId, signupsTable, uid)
        } catch (e: AppwriteException) {
            if (e.code == 404) return false
            throw e
        }
        return (row.data[SignupFields.MUST_CHANGE_PASSWORD] as? Boolean) == true
    }
}