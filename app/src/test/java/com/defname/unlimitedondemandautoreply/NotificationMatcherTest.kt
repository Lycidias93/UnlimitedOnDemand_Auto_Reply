package com.defname.unlimitedondemandautoreply

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMatcherTest {

    @Test
    fun configuredNotificationMatchesCaseInsensitively() {
        val result = evaluateNotificationMatch(
            sourcePackage = "com.google.android.apps.messaging",
            configuredPackage = "com.google.android.apps.messaging",
            title = "Message from 10118",
            configuredTitle = "10118",
            body = "Your VOLLSPEED option is ready",
            configuredBody = "Vollspeed"
        )

        assertTrue(result.packageMatch)
        assertTrue(result.titleMatch)
        assertTrue(result.bodyMatch)
        assertTrue(result.matched)
    }

    @Test
    fun configuredNotificationMatchesWhitespaceAndUnicodeVariants() {
        val result = evaluateNotificationMatch(
            sourcePackage = "com.google.android.apps.messaging",
            configuredPackage = "com.google.android.apps.messaging",
            title = "Message\nfrom\u00A010118",
            configuredTitle = "10118",
            body = "Your VO\u200BLLSPEED option is ready",
            configuredBody = "vollspeed"
        )

        assertTrue(result.titleMatch)
        assertTrue(result.bodyMatch)
        assertTrue(result.matched)
    }

    @Test
    fun normalizationCollapsesUnsafeTextDifferences() {
        assertEquals(
            "your vollspeed option",
            normalizeNotificationValue("  Your\nVO\u200BLLSPEED\u00A0option  ")
        )
    }

    @Test
    fun wrongPackageDoesNotMatch() {
        val result = evaluateNotificationMatch(
            sourcePackage = "com.termux.api",
            configuredPackage = "com.termux",
            title = "10118",
            configuredTitle = "10118",
            body = "Vollspeed",
            configuredBody = "Vollspeed"
        )

        assertFalse(result.packageMatch)
        assertFalse(result.matched)
    }

    @Test
    fun internalOverrideOnlyBypassesPackageCheck() {
        val result = evaluateNotificationMatch(
            sourcePackage = "com.defname.unlimitedondemandautoreply",
            configuredPackage = "com.google.android.apps.messaging",
            title = "wrong title",
            configuredTitle = "10118",
            body = "Vollspeed",
            configuredBody = "Vollspeed",
            allowInternalPackageOverride = true
        )

        assertTrue(result.packageMatch)
        assertFalse(result.titleMatch)
        assertTrue(result.bodyMatch)
        assertFalse(result.matched)
    }

    @Test
    fun internalOverrideMatchesWhenConfiguredTextMatches() {
        val result = evaluateNotificationMatch(
            sourcePackage = "com.defname.unlimitedondemandautoreply",
            configuredPackage = "com.google.android.apps.messaging",
            title = "10118",
            configuredTitle = "10118",
            body = "Vollspeed",
            configuredBody = "Vollspeed",
            allowInternalPackageOverride = true
        )

        assertTrue(result.matched)
    }
}
