package com.yousseflabs.iamgeflowconverter.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ProjectScannerTest {

    @Test
    fun `test extractFlutterAssets with valid pubspec`(@TempDir tempDir: File) {
        val pubspecFile = File(tempDir, "pubspec.yaml")
        pubspecFile.writeText("""
            name: test_app
            flutter:
              assets:
                - assets/
                - assets/images/
        """.trimIndent())

        val assets = invokeExtractFlutterAssets(pubspecFile)
        assertEquals(listOf("assets", "assets/images"), assets)
    }

    @Test
    fun `test extractFlutterAssets with no assets`(@TempDir tempDir: File) {
        val pubspecFile = File(tempDir, "pubspec.yaml")
        pubspecFile.writeText("""
            name: test_app
            flutter:
              uses-material-design: true
        """.trimIndent())

        val assets = invokeExtractFlutterAssets(pubspecFile)
        assertEquals(emptyList<String>(), assets)
    }

    @Test
    fun `test extractFlutterAssets with comments`(@TempDir tempDir: File) {
        val pubspecFile = File(tempDir, "pubspec.yaml")
        pubspecFile.writeText("""
            name: test_app
            flutter:
              assets:
                # This is a comment
                - assets/
        """.trimIndent())

        val assets = invokeExtractFlutterAssets(pubspecFile)
        assertEquals(listOf("assets"), assets)
    }

    private fun invokeExtractFlutterAssets(pubspecFile: File): List<String> {
        val method = ProjectScanner::class.java.getDeclaredMethod("extractFlutterAssets", File::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(ProjectScanner, pubspecFile) as List<String>
    }
}
