package com.pksafe.lock.manager.util

import android.content.Context
import android.os.Build
import android.sun.misc.BASE64Encoder
import android.sun.security.provider.X509Factory
import android.sun.security.x509.*
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.*
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.spec.InvalidKeySpecException
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Random
import kotlin.Throws

/**
 * Singleton ADB connection manager.
 * Manages RSA key pair and X.509 certificate for TLS-based wireless ADB.
 * Based on the proven muntashirakon ADB library approach.
 */
class AdbConnectionManager private constructor(context: Context) : AbsAdbConnectionManager() {
               
    private var privateKey: PrivateKey? = null
    private var certificate: Certificate? = null
                
    init {
        setApi(Build.VERSION.SDK_INT)
        privateKey = readPrivateKeyFromFile(context)
        certificate = readCertificateFromFile(context)

        if (privateKey == null) {
            // Generate new RSA key pair
            val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
            keyPairGenerator.initialize(2048, SecureRandom.getInstance("SHA1PRNG"))
            keyPair = keyPairGenerator.generateKeyPair()
            val publicKey = keyPair!!.public
            privateKey = keyPair!!.private

            // Generate self-signed X.509 certificate
            val expiryDate = System.currentTimeMillis() + 86400000L
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
            certificate = x509CertImpl

            writePrivateKeyToFile(context, privateKey!!)
            writeCertificateToFile(context, certificate!!)
        }
    }

    override fun getPrivateKey(): PrivateKey = privateKey!!

    override fun getCertificate(): Certificate = certificate!!

    override fun getDeviceName(): String = "PK Locker"

    companion object {
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
                fos.write(key.encoded)
            }
        }
    }
}
