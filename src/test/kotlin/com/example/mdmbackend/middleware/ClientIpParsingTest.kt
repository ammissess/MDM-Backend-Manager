package com.example.mdmbackend.middleware

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientIpParsingTest {

    @Test
    fun `accept valid ipv4 literal`() {
        assertEquals("10.0.2.16", normalizeClientIpCandidate("10.0.2.16"))
    }

    @Test
    fun `accept valid ipv6 literal`() {
        assertEquals("2001:db8::1", normalizeClientIpCandidate("2001:db8::1"))
    }

    @Test
    fun `accept forwarded ipv4 with port`() {
        assertEquals("10.0.2.16", normalizeClientIpCandidate("10.0.2.16:54321"))
    }

    @Test
    fun `accept bracketed ipv6 with port`() {
        assertEquals("2001:db8::1", normalizeClientIpCandidate("[2001:db8::1]:443"))
    }

    @Test
    fun `reject domain candidate`() {
        assertEquals(null, normalizeClientIpCandidate("binance.com"))
    }

    @Test
    fun `reject unknown token`() {
        assertEquals(null, normalizeClientIpCandidate("unknown"))
    }

    @Test
    fun `extract for token from forwarded header segment`() {
        val candidates = extractForwardedForCandidates("for=10.0.2.16;proto=http;by=203.0.113.43")
        assertEquals(listOf("10.0.2.16"), candidates)
    }

    @Test
    fun `extract quoted forwarded ipv6`() {
        val candidates = extractForwardedForCandidates("for=\"[2001:db8::5]:1234\";proto=https")
        assertEquals(listOf("\"[2001:db8::5]:1234\""), candidates)
        assertEquals("2001:db8::5", normalizeClientIpCandidate(candidates.first()))
    }

    @Test
    fun `ip literal validation rejects non ip strings`() {
        assertFalse(isValidIpLiteral("binance.com"))
        assertFalse(isValidIpLiteral("10.0.2"))
        assertFalse(isValidIpLiteral("2001:db8:::1"))
        assertTrue(isValidIpLiteral("10.0.2.16"))
    }
}

