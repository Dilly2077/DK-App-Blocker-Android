package com.dk.appblocker

import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class CredentialHasherTest {
    @Test fun hashesAreSaltedAndVerifyUnicode() {
        val first = CredentialHasher.hash("password🌱")
        val second = CredentialHasher.hash("password🌱")
        assertNotEquals(first, second)
        assertTrue(first.startsWith("pbkdf2-sha256\$600000\$"))
        assertTrue(CredentialHasher.verify("password🌱", first))
        assertFalse(CredentialHasher.verify("wrong", first))
        assertFalse(CredentialHasher.needsMigration(first))
    }
    @Test fun legacyHashRequiresCorrectCredential() {
        val legacy = MessageDigest.getInstance("SHA-256").digest("1234".toByteArray()).joinToString("") { "%02x".format(it) }
        assertTrue(CredentialHasher.verify("1234", legacy))
        assertFalse(CredentialHasher.verify("0000", legacy))
        assertTrue(CredentialHasher.needsMigration(legacy))
    }
    @Test fun malformedAndExcessiveWorkFactorsAreRejected() {
        listOf("", "null", "pbkdf2-sha256\$999999999\$AAAA\$AAAA", "pbkdf2-sha256\$600000\$bad\$bad", "xyz").forEach {
            assertFalse(CredentialHasher.verify("1234", it))
        }
    }
}
