package lt.setkus.pliuskis.data.auth

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

private const val SERVICE = "lt.setkus.pliuskis.session"
private const val ACCOUNT = "cognito"

/**
 * iOS [SecureSessionStorage]: the session is one generic password item in the Keychain (service
 * `lt.setkus.pliuskis.session`), readable after the first unlock and only on this device, so it is neither in
 * UserDefaults nor in a backup restored to another phone. A Keychain error on read is "nothing stored" (then the user
 * signs in again); on write it is thrown, so the sign-in fails instead of losing the session silently.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class KeychainSessionStorage : SecureSessionStorage {

    override fun read(): String? = withItemQuery(
        kSecReturnData to kCFBooleanTrue,
        kSecMatchLimit to kSecMatchLimitOne,
    ) { query ->
        memScoped {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status != errSecSuccess && status != errSecItemNotFound) {
                Logger.withTag(TAG).w { "Keychain read failed with status $status" }
            }
            (CFBridgingRelease(result.value) as? NSData)
                ?.takeIf { status == errSecSuccess }
                ?.let { data -> NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() }
        }
    }

    override fun write(session: String) {
        delete()
        val data = checkNotNull(NSString.create(string = session).dataUsingEncoding(NSUTF8StringEncoding)) {
            "The session can't be encoded"
        }
        val dataRef = CFBridgingRetain(data)
        val status = withItemQuery(
            kSecValueData to dataRef,
            kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
        ) { query -> SecItemAdd(query, null) }
        CFRelease(dataRef)
        check(status == errSecSuccess) { "Keychain write failed with status $status" }
    }

    override fun delete() {
        withItemQuery { query -> SecItemDelete(query) }
    }

    /** Runs [block] with a query for the session item plus [extra] attributes, then releases the query. */
    private fun <T> withItemQuery(vararg extra: Pair<CFStringRef?, CFTypeRef?>, block: (CFDictionaryRef?) -> T): T {
        val service = CFBridgingRetain(SERVICE)
        val account = CFBridgingRetain(ACCOUNT)
        val attributes = listOf(
            kSecClass to kSecClassGenericPassword,
            kSecAttrService to service,
            kSecAttrAccount to account,
        ) + extra
        // No retain callbacks: every value outlives the query, which is released below.
        val query = CFDictionaryCreateMutable(null, attributes.size.convert(), null, null)
        attributes.forEach { (key, value) -> CFDictionaryAddValue(query, key, value) }
        val result = block(query)
        CFRelease(query)
        CFRelease(service)
        CFRelease(account)
        return result
    }

    private companion object {
        const val TAG = "TokenStore"
    }
}
