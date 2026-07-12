package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Asserts every DataStore file-name constant is excluded from Android
 * cloud backup in BOTH backup-rules XML files.
 *
 * Why: security-audit finding M6 (2026-05-14) keeps all local
 * persistence out of Google Drive Auto Backup, but the exclude lists
 * are per-exact-filename — a NEW DataStore file that nobody remembers
 * to exclude silently fails OPEN into cloud backup. This task closes
 * that failure class: adding a `*_FILE_NAME` constant to
 * `DataStoreFactory.kt` without the matching `<exclude>` entries
 * breaks the build with instructions.
 *
 * Parsing contract (library-coupled check rule): the constants are
 * matched as `*_FILE_NAME = "<value>"` in [dataStoreFactoryFile]. If
 * ZERO constants parse, the task fails loudly — that means the
 * factory's shape changed and this check needs updating, not that
 * everything is excluded.
 */
abstract class BackupRulesExcludeCheckTask : DefaultTask() {
    /** Path to data-local's commonMain DataStoreFactory.kt. */
    @get:Input
    var dataStoreFactoryFile: String = ""

    /** backup_rules.xml + data_extraction_rules.xml paths. */
    @get:Input
    var backupRulesFiles: List<String> = emptyList()

    private val fileNameConstPattern = Regex("""\w*_FILE_NAME\s*=\s*"([^"]+)"""")

    @TaskAction
    fun check() {
        val factory = File(dataStoreFactoryFile)
        if (!factory.exists()) {
            throw GradleException(
                "BackupRulesExcludeCheck FAILED: DataStoreFactory not found at $dataStoreFactoryFile.\n" +
                    "The file moved — update `dataStoreFactoryFile` in MobileGarage/build.gradle.kts.",
            )
        }

        val fileNames = fileNameConstPattern
            .findAll(factory.readText())
            .map { it.groupValues[1] }
            .toList()

        if (fileNames.isEmpty()) {
            throw GradleException(
                "BackupRulesExcludeCheck FAILED: no `*_FILE_NAME = \"...\"` constants parsed from\n" +
                    "$dataStoreFactoryFile.\n" +
                    "This is a check-assumption failure, not a clean pass: the factory's constant\n" +
                    "naming changed and this task's regex needs updating to match it.",
            )
        }

        val violations = mutableListOf<String>()
        backupRulesFiles.forEach { rulesPath ->
            val rulesFile = File(rulesPath)
            if (!rulesFile.exists()) {
                throw GradleException(
                    "BackupRulesExcludeCheck FAILED: backup rules file not found at $rulesPath.\n" +
                        "The file moved — update `backupRulesFiles` in MobileGarage/build.gradle.kts.",
                )
            }
            val rulesText = rulesFile.readText()
            fileNames.forEach { fileName ->
                if (!rulesText.contains("path=\"$fileName\"")) {
                    violations.add("${rulesFile.name}: missing <exclude domain=\"file\" path=\"$fileName\" />")
                }
            }
        }

        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    append("BackupRulesExcludeCheck FAILED: ")
                    append(violations.size)
                    append(" missing exclude(s).\n\n")
                    violations.forEach { append("  - $it\n") }
                    append("\n")
                    append("Every DataStore file must be excluded from Android cloud backup\n")
                    append("(security-audit posture M6 — local data is device-local and re-syncs\n")
                    append("from the server; an off-device Google Drive copy is pure downside).\n\n")
                    append("Fix: add the missing <exclude domain=\"file\" path=\"...\" /> entry to\n")
                    append("androidApp/src/main/res/xml/backup_rules.xml (<full-backup-content>) and\n")
                    append("androidApp/src/main/res/xml/data_extraction_rules.xml (<cloud-backup>).\n")
                },
            )
        }

        logger.lifecycle(
            "BackupRulesExcludeCheck passed: ${fileNames.size} DataStore file(s) excluded in ${backupRulesFiles.size} rules file(s).",
        )
    }
}
