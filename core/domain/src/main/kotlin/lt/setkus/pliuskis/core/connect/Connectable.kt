package lt.setkus.pliuskis.core.connect

import kotlinx.coroutines.flow.Flow

interface Connectable {
    fun connect(): String
}