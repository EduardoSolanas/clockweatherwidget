package com.clockweather.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Release artifacts are built in CI, so an AdMob id that is missing from the workflow
 * environment silently falls back to Google's sample ad units. The app still builds,
 * still shows ads, and earns nothing -- a failure that is invisible until revenue
 * never arrives. Every Gradle step in the pipeline must therefore carry both ids.
 */
class DeployWorkflowAdMobSecretsTest {

    private val workflow = File("../.github/workflows/deploy.yml")

    private fun gradleSteps(): List<String> =
        workflow.readText()
            .split(Regex("(?m)^      - "))
            .filter { it.contains("./gradlew") }

    @Test
    fun `the deploy workflow exists where the test expects it`() {
        assertTrue(workflow.absolutePath, workflow.isFile)
    }

    @Test
    fun `every gradle step receives the admob app id`() {
        val steps = gradleSteps()
        assertFalse("No gradle steps found -- the parser is broken", steps.isEmpty())
        steps.forEach { step ->
            assertTrue(
                "Gradle step missing ADMOB_APP_ID:\n$step",
                step.contains("ADMOB_APP_ID: \${{ secrets.ADMOB_APP_ID }}"),
            )
        }
    }

    @Test
    fun `every gradle step receives the admob interstitial ad unit id`() {
        gradleSteps().forEach { step ->
            assertTrue(
                "Gradle step missing ADMOB_INTERSTITIAL_AD_UNIT_ID:\n$step",
                step.contains(
                    "ADMOB_INTERSTITIAL_AD_UNIT_ID: \${{ secrets.ADMOB_INTERSTITIAL_AD_UNIT_ID }}"
                ),
            )
        }
    }

    @Test
    fun `no google sample ad unit id is hardcoded into the workflow`() {
        assertFalse(workflow.readText().contains("ca-app-pub-3940256099942544"))
    }
}
