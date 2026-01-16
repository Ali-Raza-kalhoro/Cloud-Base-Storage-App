package com.example.cyber_cyber
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.util.Log

class EmailContentObserver(
    private val context: Context,
    handler: Handler?,
    private val onEmailDetected: (String, String, String) -> Unit
) : ContentObserver(handler) {

    companion object {
        const val TAG = "EmailContentObserver"
        val EMAIL_URI = Uri.parse("content://com.android.email.provider")
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        Log.d(TAG, "Email database changed")

        checkForNewEmails()
    }

    private fun checkForNewEmails() {


        Log.d(TAG, "Checking for new emails...")


        simulateEmailDetection()
    }

    private fun simulateEmailDetection() {
        // Simulate different types of emails
        val testEmails = listOf(
            Triple("aliraza.bdn01@gmail.com", "Urgent Inheritance Transfer", "Dear friend, I have millions of dollars to transfer to your account..."),
            Triple("jahanzaibkalhoro452@gmail.com", "Security Alert: Password Reset", "Click here to reset your bank account password immediately..."),
            Triple("lottery@winning-ticket.com", "You Won $1,000,000!", "Claim your prize now by verifying your account details..."),
            Triple("Zainab.gcu@gmail.com", "Urgent Inheritance Transfer", "Dear friend, I have millions of dollars to transfer to your account..."),
            Triple("john@example.com", "Meeting Tomorrow", "Hi, let's meet tomorrow at 10 AM for the project discussion.")
        )

        testEmails.forEach { (sender, subject, body) ->
            onEmailDetected(sender, subject, body)
        }
    }
}