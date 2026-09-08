package com.duress.adminspike

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Guarda y verifica los dos PIN.
 * - Argon2id deriva un verificador lento de forzar.
 * - Se envuelve con una clave HMAC no exportable del Android Keystore
 *   (ata cualquier ataque por fuerza bruta al hardware del telefono).
 * - Los dos PIN se guardan con formato identico; el rol va por indice (0=normal, 1=duress),
 *   nunca etiquetado en el archivo.
 */
class SecurePinStore(context: Context) {

    private val appContext = context.applicationContext
    private val storeFile = File(appContext.filesDir, "duress.bin")

    // Parametros Argon2id (baseline OWASP; ajustables en endurecimiento).
    private val argonIterations = 2
    private val argonMemoryKB = 19456   // 19 MB
    private val argonParallelism = 1
    private val hashLength = 32
    private val saltLength = 16
    private val keystoreAlias = "duress_hmac_key"

    fun isConfigured(): Boolean = storeFile.exists()

    fun configure(normalPin: String, duressPin: String) {
        val rnd = SecureRandom()
        val salt0 = ByteArray(saltLength).also { rnd.nextBytes(it) }
        val salt1 = ByteArray(saltLength).also { rnd.nextBytes(it) }
        ensureHmacKey()
        val v0 = verifier(normalPin, salt0)
        val v1 = verifier(duressPin, salt1)
        storeFile.writeBytes(salt0 + v0 + salt1 + v1)
    }

    enum class Result { NORMAL, DURESS, WRONG }

    /** No hace cortocircuito: siempre evalua ambos slots (tiempo uniforme). */
    fun verify(pin: String): Result {
        if (!isConfigured()) return Result.WRONG
        val data = storeFile.readBytes()
        val salt0 = data.copyOfRange(0, saltLength)
        val stored0 = data.copyOfRange(saltLength, saltLength + hashLength)
        val base1 = saltLength + hashLength
        val salt1 = data.copyOfRange(base1, base1 + saltLength)
        val stored1 = data.copyOfRange(base1 + saltLength, base1 + saltLength + hashLength)

        val cand0 = verifier(pin, salt0)
        val cand1 = verifier(pin, salt1)
        val matchNormal = constantTimeEquals(cand0, stored0)
        val matchDuress = constantTimeEquals(cand1, stored1)

        return when {
            matchNormal -> Result.NORMAL
            matchDuress -> Result.DURESS
            else -> Result.WRONG
        }
    }

    fun reset() { storeFile.delete() }

    private fun verifier(pin: String, salt: ByteArray): ByteArray = hmac(argon2(pin, salt))

    private fun argon2(pin: String, salt: ByteArray): ByteArray {
        val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withIterations(argonIterations)
            .withMemoryAsKB(argonMemoryKB)
            .withParallelism(argonParallelism)
            .build()
        val gen = Argon2BytesGenerator().apply { init(params) }
        val out = ByteArray(hashLength)
        gen.generateBytes(pin.toByteArray(Charsets.UTF_8), out)
        return out
    }

    private fun ensureHmacKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (ks.containsAlias(keystoreAlias)) return
        val kg = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore"
        )
        kg.init(KeyGenParameterSpec.Builder(keystoreAlias, KeyProperties.PURPOSE_SIGN).build())
        kg.generateKey()
    }

    private fun hmac(input: ByteArray): ByteArray {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val key = ks.getKey(keystoreAlias, null) as SecretKey
        return Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(input)
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var r = 0
        for (i in a.indices) r = r or (a[i].toInt() xor b[i].toInt())
        return r == 0
    }
}
