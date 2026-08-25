package com.pksafe.lock.manager.util

import android.content.Context
import android.os.Build
import android.sun.misc.BASE64Encoder
import android.sun.security.provider.X509Factory
import android.sun.security.x509.*
import android.util.Log
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Date
import java.util.Random
import kotlin.Throws

/**
 * Singleton ADB connection manager.
 * Manages RSA key pair and X.509 certificate for TLS-based wireless ADB.
 * Based on the proven muntashirakon ADB library approach.
 *
 * CRITICAL: The RSA keypair is the trust anchor for ADB pairings. It must
 * NEVER be regenerated after the first run — a new keypair means a new public
 * key that previously-paired customer phones have NOT authorized, causing
 * silent connection drops / immediate disconnects after pairing succeeds.
 *
 * The certificate can be freely regenerated as long as it uses the SAME
 * private key. The customer phone authorized the PUBLIC KEY during pairing,
 * not the certificate itself.
 */
class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {

    private var privateKey: PrivateKey? = null
    private var certificate: Certificate? = null

    init {
        setApi(Build.VERSION.SDK_INT)

        // Read existing private key — handle file corruption gracefully
        privateKey = try {
            readPrivateKeyFromFile(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read private key: ${e.message}")
            null
        }

        // Read existing certificate — handle file corruption gracefully
        certificate = try {
            readCertificateFromFile(context)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read certificate: ${e.message}")
            null
        }

        // ── 1. Keypair generation — ONLY on first run (privateKey == null) ──
        // The keypair is what the customer phone authorized during wireless ADB
        // pairing. Never regenerate it after initial pairing — a new keypair
        // creates a new public key that paired phones have NOT authorized.
        if (privateKey == null) {
            Log.i(TAG, "First run — generating RSA keypair")
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            keyPair = keyPairGenerator.generateKeyPair()
            privateKey = keyPair!!.private

            writePrivateKeyToFile(context, privateKey!!)
            writePublicKeyToFile(context, keyPair!!.public)
            Log.i(TAG, "RSA keypair saved (adb_private.key + adb_public.key)")
        }

        // ── 2. Certificate regeneration — when missing or expiring soon ──
        // The certificate can be safely regenerated using the SAME private key.
        // This fixes the root cause of "pairing succeeds but connection drops":
        // an expired certificate causes the TLS handshake to fail during
        // connect(), even though pair() succeeded and stored the public key.
        if (certificate == null || isCertificateExpiringSoon(certificate)) {
            val reason = if (certificate == null) "missing" else "expiring soon"
            Log.i(TAG, "Certificate $reason — regenerating (same keypair, 10-year validity)")

            val publicKey = resolvePublicKey(context, certificate, privateKey!!)
            certificate = generateCertificate(privateKey!!, publicKey)
            writeCertificateToFile(context, certificate!!)
            Log.i(TAG, "Certificate regenerated and saved (adb_cert.pem)")
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey!!

    override fun getCertificate(): Certificate = certificate!!

    override fun getDeviceName(): String = "PK Locker"

    companion object {
        private const val TAG = "AdbConnectionManager"

        /** 10-year certificate valid
ity — prevents cert expiry from breaking
         *  wireless ADB connections after the app has been installed for a while. */
        private const val CERT_VALIDITY_MS = 10L * 365 * 24 * 60 * 60 * 1000
        /** Regenerate the certificate if it expires within this window. */
        private const val CERT_REGEN_THRESHOLD_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

        @Volatile
        private var instance: AbsAdbConnectionManager? = null

        @JvmStatic
        @Synchronized
        @Throws(Exception::class)
        fun getInstance(context: Context): AbsAdbConnectionManager {
            if (instance == null) {
                instance = AdbConnectionManager(context.applicationContext)
            }
            return instance!!
        }

        private var keyPair: KeyPair? = null

        // ═══════════════════════════════════════════════════════════════════
        //  Certificate Health Check
        // ═══════════════════════════════════════════════════════════════════

        /**
         * Returns true if the certificate is null, expired, or will expire
         * within [CERT_REGEN_THRESHOLD_MS]. The certificate should be
         * regenerated in these cases — but ALWAYS using the same private key.
         */
        private fun isCertificateExpiringSoon(cert: Certificate?): Boolean {
            if (cert == null) return true
            return try {
                val x509 = cert as X509Certificate
                val expiryMs = x509.notAfter.time
                val nowMs = System.currentTimeMillis()
                (expiryMs - nowMs) < CERT_REGEN_THRESHOLD_MS
            } catch (e: Exception) {
                // Can't determine expiry — regenerate to be safe
                true
            }
        }

        // ═══════════════════════════════════════════════════════════════════
        //  Public Key Resolution
        // ═══════════════════════════════════════════════════════════════════

        /**
         * Resolves the public key for certificate regeneration.
         * Priority: old certificate → saved adb_public.key → derive from private key.
         */
        private fun resolvePublicKey(
            context: Context,
            oldCert: Certificate?,
            privateKey: PrivateKey
        ): PublicKey {
            // 1. Try getting public key from the old (possibly expired) certificate
            if (oldCert != null) {
                try {
                    return oldCert.publicKey
                } catch (e: Exception) {
                    Log.w(TAG, "Could not extract public key from old cert: ${e.message}")
                }
            }

            // 2. Try reading the saved public key file
            readPublicKeyFromFile(context)?.let { return it }

            // 3. Derive public key from the private key
            Log.i(TAG, "Deriving public key from private key")
            return derivePublicKeyFromPrivate(privateKey)
        }

        /**
         * Derives the RSA public key from a private key.
         * RSA public exponent is 65537 (standard for Android ADB key generation).
         */
        private fun derivePublicKeyFromPrivate(privateKey: PrivateKey): PublicKey {
            val keyFactory = KeyFactory.getInstance("RSA")
            val keySpec = keyFactory.getKeySpec(privateKey, RSAPrivateKeySpec::class.java)
            return keyFactory.generatePublic(RSAPublicKeySpec(keySpec.modulus, BigInteger.valueOf(65537)))
        }

        // ═══════════════════════════════════════════════════════════════════
        //  Certificate Generation
        // ═══════════════════════════════════════════════════════════════════

        private fun generateCertificate(privateKey: PrivateKey, publicKey: PublicKey): Certificate {
            val expiryDate = System.currentTimeMillis() + CERT_VALIDITY_MS
            val certificateExtensions = CertificateExtensions()
            certificateExtensions.set(
                SubjectKeyIdentifierExtension.NAME,
                SubjectKeyIdentifierExtension(KeyIdentifier(publicKey).identifier)
            )
            val notBefore = Date()
            val notAfter = Date(expiryDate)
            certificateExtensions.set(
                PrivateKeyUsageExtension.NAME,
                PrivateKeyUsageExtension(notBefore, notAfter)
            )
            val x500Name = X500Name("CN=PK Locker Admin")
            val x509CertInfo = X509CertInfo()
            x509CertInfo.set("version", CertificateVersion(2))
            x509CertInfo.set("serialNumber", CertificateSerialNumber(Random().nextInt() and Int.MAX_VALUE))
            x509CertInfo.set("algorithmID", CertificateAlgorithmId(AlgorithmId.get("SHA512withRSA")))
            x509CertInfo.set("subject", CertificateSubjectName(x500Name))
            x509CertInfo.set("key", CertificateX509Key(publicKey))
            x509CertInfo.set("validity", CertificateValidity(notBefore, notAfter))
            x509CertInfo.set("issuer", CertificateIssuerName(x500Name))
            x509CertInfo.set("extensions", certificateExtensions)
            val x509CertImpl = X509CertImpl(x509CertInfo)
            x509CertImpl.sign(privateKey, "SHA512withRSA")
            return x509CertImpl
        }

        // ═══════════════════════════════════════════════════════════════════
        //  File I/O
        // ═══════════════════════════════════════════════════════════════════

        @Throws(IOException::class, CertificateException::class)
        private fun readCertificateFromFile(context: Context): Certificate? {
            val file = File(context.filesDir, "adb_cert.pem")
            if (!file.exists()) return null
            FileInputStream(file).use { fis ->
                return CertificateFactory.getInstance("X.509").generateCertificate(fis)
            }
        }

        @Throws(CertificateEncodingException::class, IOException::class)
        private fun writeCertificateToFile(context: Context, cert: Certificate) {
            val file = File(context.filesDir, "adb_cert.pem")
            val encoder = BASE64Encoder()
            FileOutputStream(file).use { fos ->
                fos.write(X509Factory.BEGIN_CERT.toByteArray(StandardCharsets.UTF_8))
                fos.write(10)
                encoder.encode(cert.encoded, fos)
                fos.write(10)
                fos.write(X509Factory.END_CERT.toByteArray(StandardCharsets.UTF_8))
            }
        }

        @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        private fun readPrivateKeyFromFile(context: Context): PrivateKey? {
            val file = File(context.filesDir, "adb_private.key")
            if (!file.exists()) return null
            val bytes = ByteArray(file.length().toInt())
            FileInputStream(file).use { fis ->
                fis.read(bytes)
            }
            return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(bytes))
        }

        @Throws(IOException::class)
        private fun writePrivateKeyToFile(context: Context, key: PrivateKey) {
            val file = File(context.filesDir, "adb_private.key")
            FileOutputStream(file).use { fos ->
                fos.write(key.encoded)        }

        }

        @Throws(IOException::class, NoSuchAlgorithmException::class, InvalidKeySpecException::class)
        private fun readPublicKeyFromFile(context: Context): PublicKey? {
            val file = File(context.filesDir, "adb_public.key")
            if (!file.exists()) return null
            val bytes = ByteArray(file.length().toInt())
            FileInputStream(file).use { fis ->
                fis.read(bytes)
            }
            return KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(bytes))
        }

        @Throws(IOException::class)
        private fun writePublicKeyToFile(context: Context, publicKey: PublicKey) {
            val file = File(context.filesDir, "adb_public.key")
            FileOutputStream(file).use { fos ->
                fos.write(publicKey.encoded)
            }
        }
    }
}
