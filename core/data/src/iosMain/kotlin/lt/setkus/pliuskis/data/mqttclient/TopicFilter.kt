package lt.setkus.pliuskis.data.mqttclient

private const val LEVEL_SEPARATOR = "/"
private const val SINGLE_LEVEL_WILDCARD = "+"
private const val MULTI_LEVEL_WILDCARD = "#"
private const val SYSTEM_TOPIC_PREFIX = "$"

/**
 * True when [topic] matches the MQTT 3.1.1 topic filter [filter]: `+` matches one level, `#` (last) matches the
 * parent level and everything below it. A wildcard in the first level does not match a topic that starts with `$`.
 *
 * The transport reports each PUBLISH once, without the subscription it belongs to, so the client finds the open
 * subscriptions of a message itself.
 */
internal fun topicMatchesFilter(filter: String, topic: String): Boolean {
    val filterLevels = filter.split(LEVEL_SEPARATOR)
    val topicLevels = topic.split(LEVEL_SEPARATOR)
    val wildcardFirst = filterLevels.first() == SINGLE_LEVEL_WILDCARD || filterLevels.first() == MULTI_LEVEL_WILDCARD
    val systemTopicHidden = wildcardFirst && topic.startsWith(SYSTEM_TOPIC_PREFIX)
    return !systemTopicHidden && levelsMatch(filterLevels, topicLevels)
}

private fun levelsMatch(filterLevels: List<String>, topicLevels: List<String>): Boolean {
    val multiLevelAt = filterLevels.indexOf(MULTI_LEVEL_WILDCARD)
    val exactLevels = if (multiLevelAt == -1) filterLevels else filterLevels.subList(0, multiLevelAt)
    val sizeMatches = if (multiLevelAt == -1) {
        topicLevels.size == filterLevels.size
    } else {
        topicLevels.size >= multiLevelAt
    }
    return sizeMatches &&
        exactLevels.indices.all { index ->
            exactLevels[index] == SINGLE_LEVEL_WILDCARD || exactLevels[index] == topicLevels[index]
        }
}
