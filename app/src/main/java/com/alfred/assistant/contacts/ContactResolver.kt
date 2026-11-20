package com.alfred.assistant.contacts

import android.content.Context
import android.provider.ContactsContract
import android.util.Log

/**
 * Resolves a contact name to a phone number by querying the device's contacts
 * via [ContactsContract].
 */
object ContactResolver {

    private const val TAG = "AlfredContacts"

    /**
     * Looks up a phone number for the given [name] from the device contacts.
     *
     * Performs a case-insensitive LIKE query on [ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME]
     * using the pattern `%name%`.
     *
     * @param context The Android context for content resolver access.
     * @param name The contact name to search for (partial match supported).
     * @return The first matching phone number, or `null` if no match is found.
     */
    fun resolve(context: Context, name: String): String? {
        if (name.isBlank()) {
            Log.w(TAG, "Contact name is blank, cannot resolve")
            return null
        }

        return try {
            queryContact(context, name)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing READ_CONTACTS permission", e)
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact: $name", e)
            null
        }
    }

    /**
     * Queries the contacts database for a phone number matching the given [name].
     */
    private fun queryContact(context: Context, name: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        // Case-insensitive LIKE search
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$name%")

        // Sort by display name for consistent results
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        val cursor = context.contentResolver.query(
            uri,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                )
                val numberIndex = it.getColumnIndex(
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                )

                if (numberIndex >= 0) {
                    val matchedName = if (nameIndex >= 0) it.getString(nameIndex) else "Unknown"
                    val phoneNumber = it.getString(numberIndex)

                    Log.d(TAG, "Found contact: $matchedName -> $phoneNumber")
                    return phoneNumber
                }
            }
        }

        Log.d(TAG, "No contact found matching: $name")
        return null
    }
}
