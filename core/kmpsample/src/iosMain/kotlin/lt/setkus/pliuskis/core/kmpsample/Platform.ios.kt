package lt.setkus.pliuskis.core.kmpsample

import platform.UIKit.UIDevice

actual fun platformName(): String = UIDevice.currentDevice.systemName()
