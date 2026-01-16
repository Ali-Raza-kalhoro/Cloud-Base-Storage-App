package com.example.cyber_cyber

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import java.util.regex.Pattern

class EmailSpamDetector(private val context: Context) {

    companion object {
        const val TAG = "EmailSpamDetector"

        // Email spam patterns
        val EMAIL_SPAM_KEYWORDS = listOf(
            "nigerian prince", "inheritance", "lottery winner", "urgent transfer",
            "bank account", "security alert", "password reset", "account verification",
            "free money", "investment opportunity", "bitcoin investment", "crypto offer",
            "you have won", "claim your prize", "limited offer", "exclusive deal",
            "million dollars", "fortune", "unclaimed funds", "foreign business",
            "dear friend", "confidential", "urgent action required", "account suspended",
            "verify identity", "payment processing", "tax refund", "lottery ticket"
        )

        val SUSPICIOUS_DOMAINS = listOf(
            "freeoffer", "winning", "prize", "lottery", "money4u", "quickcash",
            "investment", "crypto", "bitcoin", "banking", "security", "verify"
        )

        val PHISHING_PATTERNS = listOf(
            Pattern.compile("click here", Pattern.CASE_INSENSITIVE),
            Pattern.compile("verify now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reset password", Pattern.CASE_INSENSITIVE),
            Pattern.compile("account alert", Pattern.CASE_INSENSITIVE),
            Pattern.compile("security breach", Pattern.CASE_INSENSITIVE)
        )
    }

    fun scanEmailForSpam(sender: String, subject: String, body: String): SpamResult {
        val lowerSubject = subject.toLowerCase()
        val lowerBody = body.toLowerCase()
        val lowerSender = sender.toLowerCase()

        // Check for spam keywords
        val foundKeywords = EMAIL_SPAM_KEYWORDS.filter { keyword ->
            lowerSubject.contains(keyword) || lowerBody.contains(keyword)
        }

        // Check sender domain
        val suspiciousSender = isSuspiciousSender(lowerSender)

        // Check for phishing patterns
        val phishingPatterns = PHISHING_PATTERNS.filter { pattern ->
            pattern.matcher(lowerSubject).find() || pattern.matcher(lowerBody).find()
        }

        // Calculate spam score
        val spamScore = calculateSpamScore(foundKeywords.size, suspiciousSender, phishingPatterns.size)

        return SpamResult(
            isSpam = spamScore >= 3,
            spamScore = spamScore,
            detectedKeywords = foundKeywords,
            suspiciousSender = suspiciousSender,
            phishingPatterns = phishingPatterns.map { it.pattern() }
        )
    }

    private fun isSuspiciousSender(sender: String): Boolean {
        val emailRegex = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,6}$", Pattern.CASE_INSENSITIVE)
        val matcher = emailRegex.matcher(sender)

        if (!matcher.find()) return true

        val domain = sender.substringAfter('@').substringBeforeLast('.')
        return SUSPICIOUS_DOMAINS.any { domain.contains(it) }
    }

    private fun calculateSpamScore(
        keywordCount: Int,
        suspiciousSender: Boolean,
        phishingCount: Int
    ): Int {
        var score = 0
        score += keywordCount
        if (suspiciousSender) score += 2
        score += phishingCount
        return score
    }

    data class SpamResult(
        val isSpam: Boolean,
        val spamScore: Int,
        val detectedKeywords: List<String>,
        val suspiciousSender: Boolean,
        val phishingPatterns: List<String>
    )
}


