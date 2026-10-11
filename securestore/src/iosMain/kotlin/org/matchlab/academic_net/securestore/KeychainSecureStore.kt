package org.matchlab.academic_net.securestore

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
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
import platform.posix.memcpy

/**
 * The iOS [SecureStore]: every key is a generic-password Keychain item of [service], readable
 * after the first unlock and never moved to another device or a backup
 * (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`). The Keychain encrypts the items.
 *
 * The Keychain survives an uninstall of the app, so a reinstalled app would sign in as the
 * previous install. To prevent that, the first access of a fresh install (no marker in
 * `NSUserDefaults`, which an uninstall does erase) deletes the items left by the previous one.
 * The marker is set only after the Keychain confirmed the deletion; until then every access
 * tries again, and [read] answers `null` rather than hand out an item of the previous install.
 *
 * Only the compilation of this class is verified: there is no Xcode and no iOS run in the
 * project's environment. On a simulator without a signed build the Keychain may answer
 * `errSecMissingEntitlement`; then [write] throws and [read] returns `null`.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class KeychainSecureStore(private val service: String = DEFAULT_SERVICE) : SecureStore {
    private val installMarker = "$service.installed"
    private val installLock = NSLock()
    private var installChecked = false

    override fun read(key: String): String? {
        // The items of the previous install are still there: they are not ours to return.
        if (!dropItemsOfPreviousInstall()) return null
        val data = withItemQuery(
            key,
            mapOf(kSecReturnData to kCFBooleanTrue, kSecMatchLimit to kSecMatchLimitOne),
        ) { query ->
            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query, result.ptr)
                // On success the result is a retained NSData, taken over by CFBridgingRelease.
                if (status == errSecSuccess) CFBridgingRelease(result.value) as? NSData else null
            }
        }
        return data?.toByteArray()?.decodeToString()
    }

    override fun write(key: String, value: String) {
        // Writing before the old items are gone would lose the new value to the pending cleanup.
        check(dropItemsOfPreviousInstall()) { "Keychain items of the previous install could not be deleted" }
        val data = CFBridgingRetain(value.encodeToByteArray().toNSData())
        try {
            // Update first; an item that does not exist yet is added with its protection class.
            val updateStatus = withItemQuery(key) { query ->
                val changes = dictionaryOf(mapOf(kSecValueData to data))
                try {
                    SecItemUpdate(query, changes)
                } finally {
                    CFRelease(changes)
                }
            }
            val status = if (updateStatus == errSecItemNotFound) {
                withItemQuery(
                    key,
                    mapOf(
                        kSecValueData to data,
                        kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                    ),
                ) { query -> SecItemAdd(query, null) }
            } else {
                updateStatus
            }
            check(status == errSecSuccess) { "Keychain write failed: status $status" }
        } finally {
            CFRelease(data)
        }
    }

    override fun remove(key: String) {
        dropItemsOfPreviousInstall()
        val status = deleteItems(key)
        check(status.isDeleted()) { "Keychain remove failed: status $status" }
    }

    /** Deletes every item of the service: the Keychain has no key to destroy, the items are the secret. */
    override fun wipe() {
        val status = deleteItems(key = null)
        check(status.isDeleted()) { "Keychain wipe failed: status $status" }
    }

    /** Deletes the item of [key], or every item of the service when [key] is `null`; returns the Keychain status. */
    private fun deleteItems(key: String?): Int = withItemQuery(key) { query -> SecItemDelete(query) }

    /** The deletion is confirmed: the items are gone or there were none. */
    private fun Int.isDeleted(): Boolean = this == errSecSuccess || this == errSecItemNotFound

    /**
     * Deletes the service's items once per install; see the class description. Returns `true`
     * when the items of the previous install are known to be gone. A failed deletion leaves the
     * marker unset, so the next access tries again.
     */
    private fun dropItemsOfPreviousInstall(): Boolean {
        installLock.lock()
        try {
            if (installChecked) return true
            val defaults = NSUserDefaults.standardUserDefaults
            if (!defaults.boolForKey(installMarker)) {
                if (!deleteItems(key = null).isDeleted()) return false
                defaults.setBool(true, forKey = installMarker)
            }
            installChecked = true
            return true
        } finally {
            installLock.unlock()
        }
    }

    /**
     * Runs [block] with a generic-password query for this service (and for [key] when it is not
     * `null`) extended with [attributes]. The values in [attributes] stay owned by the caller.
     */
    private fun <T> withItemQuery(
        key: String?,
        attributes: Map<CFStringRef?, CFTypeRef?> = emptyMap(),
        block: (CFDictionaryRef?) -> T,
    ): T {
        val serviceRef = CFBridgingRetain(service)
        val accountRef = key?.let { CFBridgingRetain(it) }
        try {
            val items = mutableMapOf<CFStringRef?, CFTypeRef?>(
                kSecClass to kSecClassGenericPassword,
                kSecAttrService to serviceRef,
            )
            if (accountRef != null) items[kSecAttrAccount] = accountRef
            items.putAll(attributes)

            val query = dictionaryOf(items)
            try {
                return block(query)
            } finally {
                CFRelease(query)
            }
        } finally {
            CFRelease(serviceRef)
            accountRef?.let { CFRelease(it) }
        }
    }

    companion object {
        const val DEFAULT_SERVICE = "org.matchlab.academic_net.session"
    }
}

/**
 * A new Core Foundation dictionary of [items], which the caller releases. Keys and values are not
 * retained by it: they must stay alive while the dictionary is used.
 */
@OptIn(ExperimentalForeignApi::class)
private fun dictionaryOf(items: Map<CFStringRef?, CFTypeRef?>): CFDictionaryRef? = memScoped {
    val keys = allocArrayOf(*items.keys.toTypedArray())
    val values = allocArrayOf(*items.values.toTypedArray())
    CFDictionaryCreate(kCFAllocatorDefault, keys.reinterpret(), values.reinterpret(), items.size.convert(), null, null)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.convert()) }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())
    if (result.isNotEmpty()) {
        result.usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
    return result
}
