package com.example

import com.example.browser.FingerprintPreset
import com.example.privacy.FingerprintScriptGenerator
import com.example.privacy.ProfileProxyManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Proxy

class ProfileIsolationUnitTest {

    @Test
    fun profileProxyManager_parsesSocks5AndHttpProxiesAndRejectsInvalidUrls() {
        val tor = ProfileProxyManager.parseProxyUrl("socks5://127.0.0.1:9050")
        assertNotNull(tor)
        assertEquals("socks5", tor!!.scheme)
        assertEquals("127.0.0.1", tor.host)
        assertEquals(9050, tor.port)
        assertEquals("socks5://127.0.0.1:9050", tor.toWebKitRule())
        assertEquals(Proxy.Type.SOCKS, tor.toJavaNetProxy().type())

        val implicitHttp = ProfileProxyManager.parseProxyUrl("10.0.0.2:8080")
        assertNotNull(implicitHttp)
        assertEquals("http", implicitHttp!!.scheme)
        assertEquals("10.0.0.2", implicitHttp.host)
        assertEquals(8080, implicitHttp.port)
        assertEquals(Proxy.Type.HTTP, implicitHttp.toJavaNetProxy().type())

        assertNull(ProfileProxyManager.parseProxyUrl(""))
        assertNull(ProfileProxyManager.parseProxyUrl("ftp://127.0.0.1:21"))
        assertNull(ProfileProxyManager.parseProxyUrl("socks5://127.0.0.1")) // missing port
        assertTrue(ProfileProxyManager.isValidOrBlank(""))
        assertTrue(ProfileProxyManager.isValidOrBlank("socks5://127.0.0.1:9050"))
        assertFalse(ProfileProxyManager.isValidOrBlank("not a proxy"))
    }

    @Test
    fun fingerprintScriptGenerator_generatesDistinctSeededProfilesAndWebRtcGuard() {
        val scriptProfileA = FingerprintScriptGenerator.generateInjectionScript(
            preset = FingerprintPreset.DEFAULT,
            fingerprintSeed = 0x1122334455667788L,
            enableWebRtcProtection = true,
            spoofNavigatorIdentity = false
        )
        val scriptProfileB = FingerprintScriptGenerator.generateInjectionScript(
            preset = FingerprintPreset.DEFAULT,
            fingerprintSeed = 0x7FEDCBA987654321L,
            enableWebRtcProtection = true,
            spoofNavigatorIdentity = false
        )

        assertNotEquals(scriptProfileA, scriptProfileB)
        assertTrue(scriptProfileA.contains("WrappedPeerConnection"))
        assertTrue(scriptProfileA.contains("const protectWebRtc = true;"))
        assertTrue(scriptProfileA.contains("const shouldSpoofNav = false;"))
        assertTrue(scriptProfileA.contains("37445"))
        assertTrue(scriptProfileA.contains("AudioBuffer"))

        val desktopSpoofed = FingerprintScriptGenerator.generateInjectionScript(
            preset = FingerprintPreset.WINDOWS_DESKTOP,
            fingerprintSeed = 0x1122334455667788L,
            enableWebRtcProtection = false,
            spoofNavigatorIdentity = true
        )
        assertTrue(desktopSpoofed.contains("const shouldSpoofNav = true;"))
        assertTrue(desktopSpoofed.contains("const protectWebRtc = false;"))
    }
}
