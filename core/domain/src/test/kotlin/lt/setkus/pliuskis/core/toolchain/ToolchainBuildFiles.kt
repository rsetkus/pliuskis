package lt.setkus.pliuskis.core.toolchain

import java.io.File

/**
 * Reads the Gradle build files of this repository for the STORY-2 toolchain checks.
 *
 * The tests run with the module directory as working directory, so the repository root is found by walking up to
 * the directory that holds both `settings.gradle.kts` and `gradle/libs.versions.toml`.
 */
internal object ToolchainBuildFiles {

    const val KOTLIN_ANDROID_PLUGIN_ID = "org.jetbrains.kotlin.android"
    const val KOTLIN_COMPOSE_PLUGIN_ID = "org.jetbrains.kotlin.plugin.compose"
    const val ANDROID_APPLICATION_PLUGIN_ID = "com.android.application"

    val repoRoot: File by lazy {
        val start = File(System.getProperty("user.dir")).absoluteFile
        val root = generateSequence(start) { it.parentFile }
            .firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "gradle/libs.versions.toml").isFile }
        checkNotNull(root) { "No repository root (settings.gradle.kts + gradle/libs.versions.toml) above $start" }
    }

    val catalog: VersionCatalog by lazy { VersionCatalog.parse(File(repoRoot, "gradle/libs.versions.toml").readText()) }

    /** Gradle version from `gradle/wrapper/gradle-wrapper.properties`, e.g. "9.1.0". */
    val wrapperGradleVersion: String by lazy {
        val text = File(repoRoot, "gradle/wrapper/gradle-wrapper.properties").readText()
        val match = Regex("""gradle-([0-9][^-/]*)-(bin|all)\.zip""").find(text)
        checkNotNull(match) { "No distributionUrl in gradle-wrapper.properties" }.groupValues[1]
    }

    /** Module paths from `settings.gradle.kts`, e.g. ":feature:devices". */
    val modulePaths: List<String> by lazy {
        Regex("""include\(\s*"([^"]+)"\s*\)""")
            .findAll(File(repoRoot, "settings.gradle.kts").readText())
            .map { it.groupValues[1] }
            .toList()
    }

    fun moduleScript(modulePath: String): File =
        File(repoRoot, modulePath.trimStart(':').replace(':', '/') + "/build.gradle.kts")

    /** Every `.kts` and `.kt` file of `build-logic`, outside build output. */
    val buildLogicSources: List<File> by lazy {
        File(repoRoot, "build-logic").walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" }
            .filter { it.isFile && (it.extension == "kts" || it.extension == "kt") }
            .toList()
    }

    /** Every Gradle script (`*.gradle.kts`) of the repository and of `build-logic`, outside build output. */
    val gradleScripts: List<File> by lazy { repoFiles { it.name.endsWith(".gradle.kts") } }

    /** Every Kotlin source (`*.kt`) under a `src/` directory of any module, outside build output. */
    val moduleKotlinSources: List<File> by lazy {
        repoFiles { it.extension == "kt" && it.invariantSeparatorsPath.contains("/src/") }
    }

    private fun repoFiles(filter: (File) -> Boolean): List<File> =
        repoRoot.walkTopDown()
            .onEnter { it.name != "build" && !it.name.startsWith(".") }
            .filter { it.isFile && filter(it) }
            .toList()

    /** Convention plugin id to the source text of its implementation class. */
    val conventionPlugins: Map<String, String> by lazy {
        val registrations = File(repoRoot, "build-logic/convention/build.gradle.kts").readText()
        Regex("""id\s*=\s*"([^"]+)"\s*\n\s*implementationClass\s*=\s*"([^"]+)"""")
            .findAll(registrations)
            .associate { match ->
                val className = match.groupValues[2]
                val source = buildLogicSources.firstOrNull {
                    it.extension == "kt" && Regex("""class\s+$className\b""").containsMatchIn(it.readText())
                }
                match.groupValues[1] to checkNotNull(source) { "No source for $className" }.readText()
            }
    }

    /**
     * Plugin ids a module script applies, directly or through convention plugins (followed transitively).
     */
    fun appliedPluginIds(modulePath: String): Set<String> {
        val direct = pluginIdsReferencedIn(moduleScript(modulePath).readText())
        val result = direct.toMutableSet()
        val pending = ArrayDeque(direct)
        while (pending.isNotEmpty()) {
            val source = conventionPlugins[pending.removeFirst()].orEmpty()
            pluginIdsReferencedIn(source).filter { result.add(it) }.forEach { pending.addLast(it) }
        }
        return result
    }

    /** Plugin ids a Gradle script or build-logic source references: by id, by catalog alias or by `kotlin("x")`. */
    fun pluginIdsReferencedIn(source: String): List<String> {
        val byAccessor = Regex("""libs\.plugins\.([A-Za-z0-9_.]+)""").findAll(source)
            .mapNotNull { catalog.pluginIdForAlias(it.groupValues[1]) }
        val byFindPlugin = Regex("""findPlugin\(\s*"([^"]+)"\s*\)""").findAll(source)
            .mapNotNull { catalog.pluginIdForAlias(it.groupValues[1]) }
        val byId = Regex("""(?:id|apply)\(\s*"([^"]+)"\s*\)""").findAll(source).map { it.groupValues[1] }
        val byPluginArg = Regex("""plugin\s*=\s*"([^"]+)"""").findAll(source).map { it.groupValues[1] }
        val byKotlinShortcut = Regex("""kotlin\(\s*"([^"]+)"\s*\)""").findAll(source)
            .map { "org.jetbrains.kotlin." + it.groupValues[1] }
        return (byAccessor + byFindPlugin + byId + byPluginArg + byKotlinShortcut).toList()
    }
}

/** Minimal reader for `gradle/libs.versions.toml` (one entry per line, as this repository writes them). */
internal class VersionCatalog(
    val versions: Map<String, String>,
    val plugins: Map<String, CatalogEntry>,
    val libraries: Map<String, CatalogEntry>,
) {
    data class CatalogEntry(val coordinate: String, val versionRef: String?, val inlineVersion: String?)

    fun resolveVersion(entry: CatalogEntry): String? = entry.versionRef?.let { versions[it] } ?: entry.inlineVersion

    /**
     * Resolved versions of every library whose `group:name` coordinate starts with [coordinatePrefix], including a
     * BOM entry. Libraries without a version (managed by a BOM) are skipped.
     */
    fun libraryVersions(coordinatePrefix: String): List<String> =
        libraries.values.filter { it.coordinate.startsWith(coordinatePrefix) }.mapNotNull { resolveVersion(it) }

    fun pluginIdForAlias(alias: String): String? =
        plugins.entries.firstOrNull { normalize(it.key) == normalize(alias) }?.value?.coordinate

    private fun normalize(alias: String): String = alias.replace('-', '.').replace('_', '.')

    companion object {
        private val sectionRegex = Regex("""^\[(\w+)]""")
        private val versionRegex = Regex("""^([\w.-]+)\s*=\s*"([^"]*)"""")
        private val tableRegex = Regex("""^([\w.-]+)\s*=\s*\{(.*)}""")

        fun parse(text: String): VersionCatalog {
            val versions = mutableMapOf<String, String>()
            val plugins = mutableMapOf<String, CatalogEntry>()
            val libraries = mutableMapOf<String, CatalogEntry>()
            var section = ""
            text.lineSequence().map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.forEach { line ->
                val sectionMatch = sectionRegex.find(line)
                val versionMatch = versionRegex.find(line)
                val tableMatch = tableRegex.find(line)
                when {
                    sectionMatch != null -> section = sectionMatch.groupValues[1]
                    section == "versions" && versionMatch != null ->
                        versions[versionMatch.groupValues[1]] = versionMatch.groupValues[2]
                    section == "plugins" && tableMatch != null ->
                        plugins[tableMatch.groupValues[1]] = pluginEntry(tableMatch.groupValues[2])
                    section == "libraries" && tableMatch != null ->
                        libraries[tableMatch.groupValues[1]] = libraryEntry(tableMatch.groupValues[2])
                }
            }
            return VersionCatalog(versions, plugins, libraries)
        }

        private fun field(body: String, name: String): String? =
            Regex("""(?:^|[\s,{])${Regex.escape(name)}\s*=\s*"([^"]*)"""").find(body)?.groupValues?.get(1)

        private fun pluginEntry(body: String) =
            CatalogEntry(field(body, "id").orEmpty(), field(body, "version.ref"), field(body, "version"))

        private fun libraryEntry(body: String): CatalogEntry {
            val module = field(body, "module") ?: "${field(body, "group").orEmpty()}:${field(body, "name").orEmpty()}"
            return CatalogEntry(module, field(body, "version.ref"), field(body, "version"))
        }
    }
}

/** Compares dotted numeric versions ("2.4.0", "9.0.1"); a pre-release suffix is ignored for ordering. */
internal fun compareVersions(left: String, right: String): Int {
    fun parts(version: String) = version.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(left)
    val b = parts(right)
    return (0 until maxOf(a.size, b.size))
        .map { (a.getOrElse(it) { 0 }).compareTo(b.getOrElse(it) { 0 }) }
        .firstOrNull { it != 0 } ?: 0
}
