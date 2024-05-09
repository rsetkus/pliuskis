package lt.setkus.pliuskis.data.crypto

import android.content.Context
import com.amazonaws.mobileconnectors.iot.AWSIotKeystoreHelper
import com.amazonaws.util.StringInputStream
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import lt.setkus.pliuskis.data.BuildConfig
import java.io.BufferedInputStream
import java.io.File
import java.security.KeyStore

const val PATH = "/path/to/keystore"

class AwsKeyStoreProducerTest : FunSpec({

    val mockContext = mockk<Context> {
        every { filesDir } returns File(PATH)
        every { assets } returns mockk {
            every { open(any()) } answers {
                BufferedInputStream(StringInputStream(firstArg()))
            }
        }
    }

    val producer = AwsKeyStoreProducer(mockContext)

    val mockKeyStore = mockk<KeyStore>()

    beforeEach {
        mockkStatic(AWSIotKeystoreHelper::class)
        every {
            AWSIotKeystoreHelper.getIotKeystore(
                BuildConfig.CERT_ID,
                PATH,
                BuildConfig.KEYSTORE_NAME,
                BuildConfig.KEYSTORE_PASSWORD
            )
        } returns mockKeyStore
        every {
            AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>()
            )
        } returns Unit
    }

    test("""
        GIVEN keystore is NOT saved, 
        WHEN requested for a key, 
        THEN should save and then return keystore
    """.trimIndent()) {
        val keyStore = producer.getKeyStore()
        keyStore shouldBe mockKeyStore

        verify {
            AWSIotKeystoreHelper.saveCertificateAndPrivateKey(
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>(),
                any<String>()
            )
        }
    }
})