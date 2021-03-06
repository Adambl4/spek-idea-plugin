package org.jetbrains.spek.tooling.execution

import org.junit.platform.engine.TestExecutionResult
import org.junit.platform.engine.UniqueId
import org.junit.platform.engine.discovery.DiscoverySelectors
import org.junit.platform.launcher.EngineFilter
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestIdentifier
import org.junit.platform.launcher.TestPlan
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder
import org.junit.platform.launcher.core.LauncherFactory
import java.io.CharArrayWriter
import java.io.PrintWriter
import java.util.*

/**
 * @author Ranie Jade Ramiso
 */
class SpekTestRunner(val spec: String, val scope: String? = null) {
    fun run() {
        val builder = LauncherDiscoveryRequestBuilder.request()
            .filters(EngineFilter.includeEngines(SPEK))

        if (scope != null) {
            var root = UniqueId.forEngine(SPEK)
            UniqueId.parse(scope).segments.forEach {
                root = root.append(it.type, it.value)
            }
            builder.selectors(DiscoverySelectors.selectUniqueId(root))
        }

        val request = builder
            .selectors(DiscoverySelectors.selectClass(spec))
            .build()

        val launcher = LauncherFactory.create()

        val durationMap = HashMap<String, Long>()

        launcher.registerTestExecutionListeners(object: TestExecutionListener {
            override fun executionFinished(testIdentifier: TestIdentifier, testExecutionResult: TestExecutionResult) {
                if (testIdentifier.parentId.isPresent) {
                    val name = testIdentifier.displayName
                    if (testIdentifier.isContainer) {
                        out("testSuiteFinished name='$name'")
                    } else {
                        val duration = System.currentTimeMillis() - durationMap[testIdentifier.uniqueId]!!
                        if (testExecutionResult.status != TestExecutionResult.Status.SUCCESSFUL) {
                            val throwable = testExecutionResult.throwable.get()
                            val writer = CharArrayWriter()
                            throwable.printStackTrace(PrintWriter(writer))
                            val details = writer.toString()
                                .toTcSafeString()

                            val message = throwable.message?.toTcSafeString()

                            out("testFailed name='$name' duration='$duration' message='$message' details='$details'")

                        } else {
                            out("testFinished name='$name' duration='$duration'")
                        }
                    }
                }
            }

            override fun executionStarted(testIdentifier: TestIdentifier) {
                if (testIdentifier.parentId.isPresent) {
                    val name = testIdentifier.displayName
                    if (testIdentifier.isContainer) {
                        out("testSuiteStarted name='$name'")
                    } else {
                        durationMap.put(testIdentifier.uniqueId, System.currentTimeMillis())
                        out("testStarted name='$name'")
                    }
                }
            }

            override fun testPlanExecutionStarted(testPlan: TestPlan) {
                out("enteredTheMatrix")
            }

            override fun executionSkipped(testIdentifier: TestIdentifier, reason: String) {
                val name = testIdentifier.displayName
                out("testIgnored name='$name' ignoreComment='$reason'")
                out("testFinished name='$name'")
            }
        })

        launcher.execute(request)
    }

    private fun String.toTcSafeString(): String {
        return this.replace("\n", "|n")
            .replace("\r", "|r")
    }

    private fun out(event: String) {
        println("##teamcity[$event]")
    }

    companion object {
        const val SPEK = "spek"
    }
}
