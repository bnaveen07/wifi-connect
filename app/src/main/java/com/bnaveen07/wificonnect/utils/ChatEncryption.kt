package com.bnaveen07.wificonnect.utils

import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

object ChatEncryption {

    private var keyPair: KeyPair? = null
    private val symmetricKeys = mutableMapOf<String, SecretKey>()

    fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        keyPair = keyGen.generateKeyPair()
        return keyPair!!
    }

    fun getPublicKey(): String {
        return keyPair?.public?.let {
            Base64.encodeToString(it.encoded, Base64.DEFAULT)
        } ?: ""
    }

    fun getPrivateKey(): PrivateKey? {
        return keyPair?.private
    }

    // Simple symmetric encryption for messages
    fun encryptMessage(message: String, myIp: String, otherUserIp: String): String {
        return try {
            val key = deriveSymmetricKey(myIp, otherUserIp)
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val encryptedBytes = cipher.doFinal(message.toByteArray())
            Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
        } catch (e: Exception) {
            message // Return original if encryption fails
        }
    }

    fun decryptMessage(encryptedMessage: String, myIp: String, otherUserIp: String): String {
        return try {
            val key = deriveSymmetricKey(myIp, otherUserIp)
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.DECRYPT_MODE, key)
            val encryptedBytes = Base64.decode(encryptedMessage, Base64.DEFAULT)
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes)
        } catch (e: Exception) {
            encryptedMessage // Return original if decryption fails
        }
    }

    // Simple hash-based key derivation for consistent keys across users
    fun deriveSymmetricKey(userIp1: String, userIp2: String): SecretKey {
        val combined = listOf(userIp1, userIp2).sorted().joinToString("")
        val hash = combined.hashCode()
        val keyBytes = ByteArray(16) // 128-bit key
        for (i in keyBytes.indices) {
            keyBytes[i] = ((hash shr (i * 8)) and 0xFF).toByte()
        }
        return SecretKeySpec(keyBytes, "AES")
    }

    // Generate session token for user verification
    fun generateSessionToken(): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..32)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }
}
