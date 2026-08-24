package com.demo.upimesh.util

import android.content.Context
import android.util.Base64
import com.demo.upimesh.model.MeshPacket
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    private const val PREFS_NAME = "upi_mesh_crypto"
    private const val PREF_PRIVATE_KEY = "private_key"
    private const val PREF_PUBLIC_KEY = "public_key"

    fun sha256(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun generatePacketHash(sender: String, receiver: String, amount: String, timestamp: Long): String {
        return sha256("$sender|$receiver|$amount|$timestamp").take(16).uppercase()
    }

    fun getOrCreateKeyPair(context: Context): KeyPair {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val storedPrivate = prefs.getString(PREF_PRIVATE_KEY, null)
        val storedPublic = prefs.getString(PREF_PUBLIC_KEY, null)

        if (storedPrivate != null && storedPublic != null) {
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(java.security.spec.PKCS8EncodedKeySpec(Base64.decode(storedPrivate, Base64.NO_WRAP)))
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(storedPublic, Base64.NO_WRAP)))
            return KeyPair(publicKey, privateKey)
        }

        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048)
        val pair = generator.generateKeyPair()
        prefs.edit()
            .putString(PREF_PRIVATE_KEY, Base64.encodeToString(pair.private.encoded, Base64.NO_WRAP))
            .putString(PREF_PUBLIC_KEY, Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP))
            .apply()
        return pair
    }

    fun wrapPayload(
        context: Context,
        senderVpa: String,
        receiverVpa: String,
        amount: String,
        note: String,
        packetId: String,
        timestamp: Long = System.currentTimeMillis()
    ): MeshPacket {
        val keyPair = getOrCreateKeyPair(context)
        val aesKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }

        val plaintext = "$senderVpa|$receiverVpa|$amount|$note|$timestamp|$packetId"
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val signature = Signature.getInstance("SHA256withRSA").apply {
            initSign(keyPair.private)
            update((packetId + Base64.encodeToString(encryptedBytes, Base64.NO_WRAP) + Base64.encodeToString(iv, Base64.NO_WRAP)).toByteArray(Charsets.UTF_8))
        }.sign()

        return MeshPacket(
            packetId = packetId,
            senderVpa = senderVpa,
            ciphertext = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
            encryptedKey = "${Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)}:${Base64.encodeToString(aesKey.encoded, Base64.NO_WRAP)}",
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            ttl = 5,
            signature = Base64.encodeToString(signature, Base64.NO_WRAP),
            createdTimestamp = timestamp
        )
    }

    fun unwrapPayload(packet: MeshPacket, context: Context): ParsedPayload? {
        return try {
            val keyPair = getOrCreateKeyPair(context)
            val parts = packet.encryptedKey.split(":", limit = 2)
            if (parts.size != 2) return null

            val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(parts[0], Base64.NO_WRAP)))
            val aesKeyBytes = Base64.decode(parts[1], Base64.NO_WRAP)
            val iv = Base64.decode(packet.iv, Base64.NO_WRAP)
            val ciphertext = Base64.decode(packet.ciphertext, Base64.NO_WRAP)

            val signatureBytes = Base64.decode(packet.signature ?: "", Base64.NO_WRAP)
            val verifier = Signature.getInstance("SHA256withRSA").apply {
                initVerify(publicKey)
                update((packet.packetId + packet.ciphertext + packet.iv).toByteArray(Charsets.UTF_8))
            }
            if (!verifier.verify(signatureBytes)) {
                return null
            }

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"), GCMParameterSpec(128, iv))
            val plaintext = String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            val values = plaintext.split("|", limit = 6)
            if (values.size < 6) return null
            ParsedPayload(values[0], values[1], values[2], values[3], values[4].toLong(), values[5])
        } catch (_: Exception) {
            null
        }
    }

    data class ParsedPayload(
        val senderVpa: String,
        val receiverVpa: String,
        val amount: String,
        val note: String,
        val timestamp: Long,
        val packetId: String
    )

    fun encryptPaymentOffline(
        context: Context,
        senderVpa: String,
        receiverVpa: String,
        amount: java.math.BigDecimal,
        pin: String,
        packetId: String,
        timestamp: Long = System.currentTimeMillis()
    ): String? {
        return try {
            val prefs = context.getSharedPreferences("upi_mesh_prefs", Context.MODE_PRIVATE)
            val pubKeyBase64 = prefs.getString("server_public_key", null) ?: return null

            // Reconstruct server public key
            val keyBytes = Base64.decode(pubKeyBase64, Base64.NO_WRAP)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("RSA")
            val serverPublicKey = kf.generatePublic(spec)

            // Hash the pin using SHA-256
            val pinMd = MessageDigest.getInstance("SHA-256")
            val pinHashBytes = pinMd.digest(pin.toByteArray(Charsets.UTF_8))
            val pinHash = pinHashBytes.joinToString("") { "%02x".format(it) }

            // Construct PaymentInstruction JSON string matching Jackson deserializer on backend
            val amountStr = amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
            val paymentJson = "{\"senderVpa\":\"$senderVpa\",\"receiverVpa\":\"$receiverVpa\",\"amount\":$amountStr,\"pinHash\":\"$pinHash\",\"nonce\":\"$packetId\",\"signedAt\":$timestamp}"
            val plaintext = paymentJson.toByteArray(Charsets.UTF_8)

            // 1. Generate one-time 256-bit AES key
            val kg = KeyGenerator.getInstance("AES").apply { init(256) }
            val aesKey = kg.generateKey()

            // 2. AES-GCM encrypt
            val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
            val aes = Cipher.getInstance("AES/GCM/NoPadding")
            aes.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            val aesCiphertext = aes.doFinal(plaintext)

            // 3. RSA-OAEP encrypt the AES key
            val rsa = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
            val oaep = javax.crypto.spec.OAEPParameterSpec(
                "SHA-256", "MGF1", java.security.spec.MGF1ParameterSpec.SHA256, javax.crypto.spec.PSource.PSpecified.DEFAULT
            )
            rsa.init(Cipher.ENCRYPT_MODE, serverPublicKey, oaep)
            val encryptedAesKey = rsa.doFinal(aesKey.encoded)

            // 4. Pack: [encrypted AES key (256 bytes)][IV (12 bytes)][AES ciphertext]
            val output = ByteArray(encryptedAesKey.size + iv.size + aesCiphertext.size)
            System.arraycopy(encryptedAesKey, 0, output, 0, encryptedAesKey.size)
            System.arraycopy(iv, 0, output, encryptedAesKey.size, iv.size)
            System.arraycopy(aesCiphertext, 0, output, encryptedAesKey.size + iv.size, aesCiphertext.size)

            Base64.encodeToString(output, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
