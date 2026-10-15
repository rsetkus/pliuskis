package lt.setkus.pliuskis.main

import org.koin.dsl.module

/**
 * App-shell definitions for MainActivity. Nothing here may depend on the MQTT connection:
 * connecting is the device list screen's job (DevicesViewModel), not app launch's.
 */
val mainModule = module { }
