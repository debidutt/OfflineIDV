package com.ing.offlineidv.nfc

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

public class ApprovedDependencyConfigurationTest {
    @Test
    public fun `protocol graph uses only reviewed exact versions`() {
        val catalog = locate("gradle/libs.versions.toml").readText()

        listOf(
            "jmrtd = \"0.8.8\"",
            "scubaSmartcards = \"0.0.21\"",
            "bcprov = \"1.85.2\"",
            "bcutil = \"1.85\"",
            "certCvc = \"1.4.13\"",
        ).forEach { declaration -> assertTrue("missing approved lock $declaration", catalog.contains(declaration)) }
    }

    @Test
    public fun `protocol graph fails conflicts locks configurations and excludes legacy provider`() {
        val build = locate("android/nfc/build.gradle.kts").readText()

        assertTrue(build.contains("resolutionStrategy.failOnVersionConflict()"))
        assertTrue(build.contains("lockAllConfigurations()"))
        assertTrue(build.contains("bcprov-jdk15on"))
        assertTrue(build.contains("bcprov-jdk18on"))
    }

    @Test
    public fun `approved protocol groups resolve exclusively from Maven Central`() {
        val settings = locate("settings.gradle.kts").readText()

        assertTrue(settings.contains("exclusiveContent"))
        listOf("org.jmrtd", "net.sf.scuba", "org.bouncycastle", "org.ejbca.cvc").forEach { group ->
            assertTrue("missing exclusive repository group $group", settings.contains("includeGroup(\"$group\")"))
        }
    }

    private fun locate(path: String): File {
        var directory: File? = File(requireNotNull(System.getProperty("user.dir"))).absoluteFile
        while (directory != null) {
            val candidate = File(directory, path)
            if (candidate.exists()) return candidate
            directory = directory.parentFile
        }
        error("Could not locate $path")
    }
}
