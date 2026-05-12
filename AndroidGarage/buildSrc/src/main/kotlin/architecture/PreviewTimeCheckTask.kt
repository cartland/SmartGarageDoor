package architecture

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

/**
 * Two-pass BFS check that ensures `@Preview` Composables never
 * transitively call a Composable whose default parameter value uses
 * `Clock.System.now()` / `System.currentTimeMillis()` / `Instant.now()`
 * etc. without explicitly passing the time parameter.
 *
 * Why this matters: `@Preview` and screenshot tests render at
 * preview-time. A default like `now: Instant = Instant.now()` reads
 * wall-clock time at render, producing non-deterministic output —
 * screenshots flap, golden-image tests get false-positive diffs, and
 * the framed README screenshot drifts from one regen to the next.
 *
 * **Pass 1**: collect "time-sensitive" Composables — `@Composable fun
 * Foo(now: Instant = Instant.now())` records `Foo` with its `now`
 * parameter as time-sensitive.
 *
 * **Pass 2**: BFS from each `@Preview` Composable through the call
 * chain (depth-limited at 6 to keep the check fast). For every call
 * to a time-sensitive Composable, verify the time-sensitive parameter
 * was explicitly passed; if not, report a violation with the call
 * chain (`PreviewName → Body → DeviceListItem` etc).
 *
 * Suppression: add `// @PreviewTimeExempt` on the call line when the
 * non-determinism is intentional (rare).
 *
 * Ported from battery-butler 2026-05-12. Adapted to use SmartGarageDoor's
 * `sourceDirs: List<String>` convention (vs. battery-butler's
 * `project.rootDir.walk()`).
 */
abstract class PreviewTimeCheckTask : DefaultTask() {
    /**
     * Source directories to scan, e.g. `androidApp/src/main/java`.
     * Walked recursively for `*.kt` files. Test source sets and
     * `build/` paths are skipped.
     */
    @get:Input
    var sourceDirs: List<String> = emptyList()

    private data class Violation(
        val file: String,
        val line: Int,
        val chain: List<String>,
        val missingParam: String,
        val targetFunc: String,
    )

    private data class ParsedFunction(
        val name: String,
        val relativePath: String,
        val startLine: Int,
        val bodyLines: List<String>,
        val isPreview: Boolean,
        val isComposable: Boolean,
    )

    private data class FunctionCall(
        val calledName: String,
        val callLine: Int,
        val argText: String,
    )

    private val clockPatterns = listOf(
        Regex("""Clock\s*\.\s*System\s*\.\s*now\s*\(\s*\)"""),
        Regex("""System\s*\.\s*currentTimeMillis\s*\(\s*\)"""),
        Regex("""Calendar\s*\.\s*getInstance\s*\(\s*\)"""),
        Regex("""LocalDate\s*\.\s*now\s*\(\s*\)"""),
        Regex("""LocalDateTime\s*\.\s*now\s*\(\s*\)"""),
        Regex("""Instant\s*\.\s*now\s*\(\s*\)"""),
    )

    private val exemptComment = "@PreviewTimeExempt"
    private val skipPathFragments = listOf(
        "/build/",
        "/test/",
        "/androidTest/",
        "/commonTest/",
        "/jvmTest/",
        "/androidUnitTest/",
        "/androidInstrumentedTest/",
        "/screenshotTest/",
    )
    private val maxDepth = 6

    @TaskAction
    fun check() {
        val kotlinFiles = mutableListOf<Pair<File, String>>() // file → rootRelative
        sourceDirs.forEach { dir ->
            val rootFile = File(dir)
            if (!rootFile.exists()) return@forEach
            rootFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { f -> skipPathFragments.none { frag -> f.path.contains(frag) } }
                .forEach { f -> kotlinFiles.add(f to f.relativeTo(rootFile).path) }
        }

        // Pass 1: collect time-sensitive Composables.
        val timeSensitive = mutableMapOf<String, MutableSet<String>>()
        kotlinFiles.forEach { (file, _) -> collectTimeSensitive(file.readLines(), timeSensitive) }

        if (timeSensitive.isEmpty()) {
            logger.lifecycle(
                "Preview time check passed: no time-sensitive Composables found " +
                    "(scanned ${kotlinFiles.size} file(s)).",
            )
            return
        }

        logger.lifecycle(
            "Found ${timeSensitive.size} time-sensitive Composable(s): " +
                timeSensitive.entries.joinToString { "${it.key}(${it.value.joinToString()})" },
        )

        // Parse all Composable functions (Composable + Preview only).
        val allFunctions = mutableListOf<ParsedFunction>()
        kotlinFiles.forEach { (file, rel) ->
            allFunctions.addAll(parseFunctions(file.readLines(), rel))
        }
        val functionsByName = allFunctions.groupBy { it.name }
        val previewFunctions = allFunctions.filter { it.isPreview }

        // Pass 2: BFS from each @Preview.
        val violations = mutableListOf<Violation>()
        for (preview in previewFunctions) {
            checkPreview(preview, timeSensitive, functionsByName, violations)
        }

        if (violations.isNotEmpty()) {
            val unique = violations.distinctBy {
                Triple(it.file, it.line, "${it.targetFunc}:${it.missingParam}")
            }
            val report = buildString {
                appendLine()
                appendLine("=== Preview time check FAILED: ${unique.size} violation(s) ===")
                appendLine()
                unique.forEach { v ->
                    val chain = v.chain.joinToString(" → ")
                    appendLine("${v.file}:${v.line} [preview-uses-clock-now]")
                    appendLine("  Chain: $chain")
                    appendLine(
                        "  '${v.targetFunc}' is called without '${v.missingParam}', " +
                            "defaulting to wall-clock time.",
                    )
                    appendLine(
                        "  Fix: Thread '${v.missingParam}' as an explicit parameter " +
                            "through the call chain.",
                    )
                    appendLine(
                        "  Suppress: Add // $exemptComment on the call line if intentional.",
                    )
                    appendLine()
                }
                appendLine(
                    "@Preview Composables must not transitively use " +
                        "Clock.System.now() / Instant.now() / etc. via default params.",
                )
                appendLine(
                    "Pass a fixed Instant to ensure deterministic " +
                        "screenshot tests and stable golden images.",
                )
            }
            throw GradleException(report)
        }

        logger.lifecycle(
            "Preview time check passed: ${previewFunctions.size} preview(s) checked, " +
                "no violations.",
        )
    }

    // ── Pass 1: collect time-sensitive composables ──────────────────────────

    private fun collectTimeSensitive(
        lines: List<String>,
        result: MutableMap<String, MutableSet<String>>,
    ) {
        var inComposableSignature = false
        var funcName = ""
        var parenDepth = 0

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()

            if (trimmed.startsWith("@Composable")) {
                for (ahead in (index + 1) until minOf(index + 5, lines.size)) {
                    val funMatch = Regex("""fun\s+(\w+)\s*\(""").find(lines[ahead].trim())
                    if (funMatch != null) {
                        inComposableSignature = true
                        funcName = funMatch.groupValues[1]
                        parenDepth = 0
                        break
                    }
                }
            }

            if (inComposableSignature) {
                for (ch in line) {
                    if (ch == '(') parenDepth++
                    if (ch == ')') parenDepth--
                }

                if (line.contains("=") && !line.contains(exemptComment)) {
                    for (pattern in clockPatterns) {
                        if (pattern.containsMatchIn(line)) {
                            val paramName = Regex("""(\w+)\s*:""")
                                .find(line.trim())
                                ?.groupValues
                                ?.get(1) ?: "unknown"
                            result.getOrPut(funcName) { mutableSetOf() }.add(paramName)
                        }
                    }
                }

                if (parenDepth <= 0 && line.contains(")")) {
                    inComposableSignature = false
                }
            }
        }
    }

    // ── Function parsing ────────────────────────────────────────────────────

    private fun parseFunctions(
        lines: List<String>,
        relativePath: String,
    ): List<ParsedFunction> {
        val functions = mutableListOf<ParsedFunction>()
        var i = 0

        while (i < lines.size) {
            var isPreview = false
            var isComposable = false
            val annotationStart = i

            while (i < lines.size) {
                val t = lines[i].trim()
                if (t.startsWith("@Preview")) isPreview = true
                if (t.startsWith("@Composable")) isComposable = true
                if (t.startsWith("fun ") || t.contains(" fun ")) break
                if (!t.startsWith("@") &&
                    !t.startsWith("//") &&
                    !t.startsWith("/*") &&
                    !t.startsWith("*") &&
                    t.isNotEmpty()
                ) {
                    isPreview = false
                    isComposable = false
                    break
                }
                i++
            }

            if (i >= lines.size) break

            val funMatch = Regex("""fun\s+(\w+)\s*[\(<]""").find(lines[i].trim())
            if (funMatch == null || (!isComposable && !isPreview)) {
                i++
                continue
            }

            val funcName = funMatch.groupValues[1]

            var braceStart = i
            while (braceStart < lines.size && !lines[braceStart].contains("{")) braceStart++
            if (braceStart >= lines.size) {
                i++
                continue
            }

            var braceDepth = 0
            var funcEndLine = braceStart
            for (j in braceStart until lines.size) {
                for (ch in lines[j]) {
                    if (ch == '{') braceDepth++
                    if (ch == '}') braceDepth--
                }
                if (braceDepth <= 0) {
                    funcEndLine = j
                    break
                }
            }

            functions.add(
                ParsedFunction(
                    name = funcName,
                    relativePath = relativePath,
                    startLine = annotationStart + 1,
                    bodyLines = lines.subList(braceStart, funcEndLine + 1),
                    isPreview = isPreview,
                    isComposable = isComposable || isPreview,
                ),
            )

            i = funcEndLine + 1
        }

        return functions
    }

    // ── Call extraction ─────────────────────────────────────────────────────

    private fun findCalls(
        bodyLines: List<String>,
        baseLineNum: Int,
    ): List<FunctionCall> {
        val calls = mutableListOf<FunctionCall>()
        val joined = bodyLines.joinToString("\n")
        val callPattern = Regex("""([A-Z]\w+)\s*\(""")

        var searchFrom = 0
        while (true) {
            val match = callPattern.find(joined, searchFrom) ?: break
            val calledName = match.groupValues[1]
            val openParenIndex = match.range.last

            var depth = 1
            var pos = openParenIndex + 1
            while (pos < joined.length && depth > 0) {
                if (joined[pos] == '(') depth++
                if (joined[pos] == ')') depth--
                pos++
            }

            val argText = joined.substring(
                openParenIndex + 1,
                maxOf(openParenIndex + 1, pos - 1),
            )
            val callLine = baseLineNum +
                joined.substring(0, match.range.first).count { it == '\n' }

            calls.add(FunctionCall(calledName, callLine, argText))
            searchFrom = match.range.first + 1
        }

        return calls
    }

    // ── Pass 2: BFS from @Preview ───────────────────────────────────────────

    private data class BfsEntry(
        val func: ParsedFunction,
        val chain: List<String>,
    )

    private fun checkPreview(
        preview: ParsedFunction,
        timeSensitive: Map<String, Set<String>>,
        functionsByName: Map<String, List<ParsedFunction>>,
        violations: MutableList<Violation>,
    ) {
        val queue = ArrayDeque<BfsEntry>()
        queue.add(BfsEntry(preview, listOf(preview.name)))
        val visited = mutableSetOf(preview.name)

        while (queue.isNotEmpty()) {
            val entry = queue.removeFirst()
            if (entry.chain.size > maxDepth) continue

            val calls = findCalls(entry.func.bodyLines, entry.func.startLine)

            for (call in calls) {
                val tsParams = timeSensitive[call.calledName]
                if (tsParams != null) {
                    for (param in tsParams) {
                        if (!isParamPassed(param, call.argText) &&
                            !call.argText.contains(exemptComment)
                        ) {
                            violations.add(
                                Violation(
                                    file = entry.func.relativePath,
                                    line = call.callLine,
                                    chain = entry.chain + call.calledName,
                                    missingParam = param,
                                    targetFunc = call.calledName,
                                ),
                            )
                        }
                    }
                    // Don't recurse into time-sensitive funcs — they're the leaf.
                    continue
                }

                if (call.calledName in visited) continue
                visited.add(call.calledName)

                val calledFuncs = functionsByName[call.calledName] ?: continue
                for (calledFunc in calledFuncs) {
                    if (calledFunc.isComposable) {
                        queue.add(BfsEntry(calledFunc, entry.chain + call.calledName))
                    }
                }
            }
        }
    }

    private fun isParamPassed(
        paramName: String,
        argText: String,
    ): Boolean = Regex("""\b${Regex.escape(paramName)}\s*=""").containsMatchIn(argText)
}
