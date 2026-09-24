package com.murphy.smsforwarder

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageFilterMatcherTest {
    @Test
    fun matchesChineseNumericCode() {
        assertTrue(MessageFilterMatcher.matchesOtp("您的验证码为 482913，请勿泄露"))
    }

    @Test
    fun matchesEnglishMixedCode() {
        assertTrue(MessageFilterMatcher.matchesOtp("Your verification code is A7K2Q9"))
    }

    @Test
    fun matchesEnglishUppercaseLetterCode() {
        assertTrue(MessageFilterMatcher.matchesOtp("Your security code is ABCDEF"))
    }

    @Test
    fun doesNotTreatKeywordAsCode() {
        assertFalse(MessageFilterMatcher.matchesOtp("Your VERIFICATION CODE has expired"))
    }

    @Test
    fun requiresOtpKeyword() {
        assertFalse(MessageFilterMatcher.matchesOtp("Your delivery number is 482913"))
    }

    @Test
    fun doesNotMatchPinInsideShipping() {
        assertFalse(MessageFilterMatcher.matchesOtp("Your shipping number is 482913"))
    }

    @Test
    fun parsesSupportedKeywordSeparators() {
        assertTrue(MessageFilterMatcher.matchesKeywords("Your parcel is ready", "银行，parcel;账单\n快递"))
    }

    @Test
    fun emptyKeywordsNeverMatch() {
        assertFalse(MessageFilterMatcher.matchesKeywords("Any message", " ,；\n"))
    }

    @Test
    fun matchesStandalonePinKeyword() {
        assertTrue(MessageFilterMatcher.matchesOtp("Your PIN is 482913"))
    }
}
