package dev.gavenda.yuuka.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationTest {
    @Test
    fun currencyCodeIsExactlyThreeLetters() {
        assertTrue(isCurrencyCode("php"))
        assertTrue(isCurrencyCode(" USD "))
        assertFalse(isCurrencyCode(""))
        assertFalse(isCurrencyCode("PH"))
        assertFalse(isCurrencyCode("P1P"))
        assertFalse(isCurrencyCode("PHPP"))
    }

    @Test
    fun hexColourNeedsAllSixDigits() {
        assertTrue(isHexColour("#64748b"))
        assertTrue(isHexColour("#ABCDEF"))
        assertFalse(isHexColour("#fff"))
        assertFalse(isHexColour("64748b"))
        assertFalse(isHexColour("#64748g"))
    }

    @Test
    fun namesAreComparedWithoutRegardToCaseOrPadding() {
        assertTrue(sameName(" Travel", "travel "))
        assertFalse(sameName("Travel", "Trips"))
    }

    @Test
    fun aLogoLinkIsHttpOrHttpsOnly() {
        assertTrue(isHttpUrl("https://example.com/logo.png"))
        assertTrue(isHttpUrl("http://example.com/logo.png"))
        assertFalse(isHttpUrl("data:image/png;base64,AAAA"))
        assertFalse(isHttpUrl("ftp://example.com/logo.png"))
        assertFalse(isHttpUrl("not a url"))
        assertFalse(isHttpUrl("https://"))
    }
}
