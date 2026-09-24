package com.murphy.smsforwarder

object MessageFilterMatcher {
    private val otpKeywords = listOf(
        "验证码", "校验码", "动态码", "安全码", "登录码", "确认码", "授权码", "动态密码", "一次性密码",
        "verification code", "security code", "authentication code", "one-time code", "one time code",
        "passcode", "verify", "verification", "activation", "password", "otp", "pin", "code"
    )
    private val numericCode = Regex("(?<![A-Za-z0-9])\\d{4,8}(?![A-Za-z0-9])")
    private val alphaNumericCode = Regex("(?<![A-Za-z0-9])[A-Za-z0-9]{4,10}(?![A-Za-z0-9])")
    private val normalizedKeywords = otpKeywords.map(String::lowercase).toSet()
    private val keywordPatterns = otpKeywords.map { keyword ->
        if (keyword.any { it.code > 127 } || keyword.contains(' ')) {
            Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE)
        } else {
            Regex("(?<![A-Za-z])${Regex.escape(keyword)}(?![A-Za-z])", RegexOption.IGNORE_CASE)
        }
    }

    fun matchesOtp(text: String): Boolean {
        val lower = text.lowercase()
        val keywordRanges = keywordPatterns.mapNotNull { pattern ->
            pattern.find(lower)?.range
        }
        if (keywordRanges.isEmpty()) return false
        if (numericCode.containsMatchIn(text)) return true

        return alphaNumericCode.findAll(text).any { match ->
            val candidate = match.value
            if (candidate.lowercase() in normalizedKeywords) return@any false
            val hasLetter = candidate.any(Char::isLetter)
            val hasDigit = candidate.any(Char::isDigit)
            when {
                hasLetter && hasDigit -> true
                hasLetter && candidate == candidate.uppercase() -> keywordRanges.any { range ->
                    range.last < match.range.first && match.range.first - range.last <= 24
                }
                else -> false
            }
        }
    }

    fun matchesKeywords(text: String, rawKeywords: String): Boolean =
        parseKeywords(rawKeywords).any { text.contains(it, ignoreCase = true) }

    fun parseKeywords(rawKeywords: String): List<String> = rawKeywords
        .split(',', '，', ';', '；', '\n')
        .map(String::trim)
        .filter(String::isNotEmpty)
        .distinctBy(String::lowercase)
}
