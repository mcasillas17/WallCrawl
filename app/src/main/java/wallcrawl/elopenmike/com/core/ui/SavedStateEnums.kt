package wallcrawl.elopenmike.com.core.ui

/** Reads an enum saved by name, falling back when it is missing or no longer exists. */
internal inline fun <reified T : Enum<T>> enumValueOrDefault(
    persistedName: String?,
    defaultValue: T
): T {
    if (persistedName == null) return defaultValue
    return try {
        enumValueOf<T>(persistedName)
    } catch (error: IllegalArgumentException) {
        defaultValue
    }
}

internal inline fun <reified T : Enum<T>> enumSetOrDefault(
    persistedNames: List<String>?,
    defaultValue: Set<T>
): Set<T> {
    if (persistedNames == null) return defaultValue
    return persistedNames.mapNotNull { name ->
        try {
            enumValueOf<T>(name)
        } catch (error: IllegalArgumentException) {
            null
        }
    }.toSet()
}
