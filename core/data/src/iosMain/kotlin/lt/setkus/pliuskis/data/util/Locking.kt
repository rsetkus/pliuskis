package lt.setkus.pliuskis.data.util

import platform.Foundation.NSLockingProtocol

/** Runs [block] while holding this lock. */
internal inline fun <T> NSLockingProtocol.locked(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}
