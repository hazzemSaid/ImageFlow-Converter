package com.yousseflabs.iamgeflowconverter.util

import com.intellij.openapi.project.Project
import com.yousseflabs.iamgeflowconverter.model.image.ImageExtension
import com.yousseflabs.iamgeflowconverter.model.image.ImageFile
import java.io.File

object ProjectScanner {

    private val IGNORED_DIRS = setOf(
        "build", ".gradle", ".idea", "node_modules", ".git",
        ".cxx", "intermediates", "generated", "tmp",
        ".dart_tool", ".flutter-plugins", "ephemeral", ".pub-cache"
    )

    /**
     * Scans the given project for image assets.
     *
     * @param project The IntelliJ project to scan.
     * @return A list of discovered image files.
     */
    fun scan(project: Project): List<ImageFile> {
        val basePath = project.basePath ?: return emptyList()
        val root = File(basePath)
        if (!root.exists()) {
            return emptyList()
        }

        val pubspecFile = File(root, "pubspec.yaml")
        
        // If it's a Flutter project, use pubspec.yaml to find assets
        return if (pubspecFile.exists()) {
            scanFlutterProject(root, pubspecFile)
        } else {
            // Fallback to general scan for non-Flutter projects
            scanGeneralProject(root)
        }
    }

    /**
     * Scans all directories under the given root, excluding ignored folders.
     *
     * @param root The project root directory.
     * @return A list of discovered image files.
     */
    private fun scanGeneralProject(root: File): List<ImageFile> {
        return root.walkTopDown()
            .onEnter { dir -> dir.name !in IGNORED_DIRS }
            .filter { it.isFile }
            .mapNotNull { file -> createImageFile(file, root) }
            .sortedBy { it.relativePath }
            .toList()
    }

    /**
     * Scans a Flutter project by reading assets from pubspec.yaml.
     *
     * @param root The project root directory.
     * @param pubspecFile The pubspec.yaml file used to resolve assets.
     * @return A list of discovered image files.
     */
    private fun scanFlutterProject(root: File, pubspecFile: File): List<ImageFile> {
        val assetPaths = extractFlutterAssets(pubspecFile)
        val results = mutableListOf<ImageFile>()

        for (path in assetPaths) {
            val assetFile = File(root, path)
            if (!assetFile.exists()) continue

            if (assetFile.isFile) {
                createImageFile(assetFile, root)?.let { results.add(it) }
            } else if (assetFile.isDirectory) {
                // If it's a folder, scan all files inside it
                assetFile.walkTopDown()
                    .filter { it.isFile }
                    .mapNotNull { file -> createImageFile(file, root) }
                    .forEach { results.add(it) }
            }
        }
        return results.sortedBy { it.relativePath }
    }

    /**
     * Extracts Flutter asset paths from the given pubspec.yaml file.
     *
     * @param pubspecFile The pubspec.yaml file to parse.
     * @return A list of asset paths as declared under the assets section.
     */
    private fun extractFlutterAssets(pubspecFile: File): List<String> {
        val lines = pubspecFile.readLines()
        val assetLines = extractAssetLines(lines)
        return parseAssetPaths(assetLines)
    }

    /**
     * Collects raw asset entries from a pubspec.yaml file.
     *
     * @param lines The pubspec.yaml file lines.
     * @return A list of raw asset lines, including the leading dash.
     */
    private fun extractAssetLines(lines: List<String>): List<String> {
        val result = mutableListOf<String>()
        var inAssetsSection = false

        for (line in lines) {
            val trimmed = line.trim()
            if (!trimmed.startsWith("#")) {
                when {
                    trimmed == "assets:" -> inAssetsSection = true
                    inAssetsSection && trimmed.startsWith("-") -> result.add(trimmed)
                    inAssetsSection && line.isNotBlank() 
                        && !line.startsWith(" ") 
                        && !line.startsWith("\t") -> inAssetsSection = false
                }
            }
        }
        return result
    }

    /**
     * Normalizes asset lines to relative asset paths.
     *
     * @param assetLines Raw asset lines as declared in pubspec.yaml.
     * @return A list of cleaned asset paths.
     */
    private fun parseAssetPaths(assetLines: List<String>): List<String> {
        return assetLines
            .map { it.removePrefix("-").trim().trimEnd('/') }
            .filter { it.isNotEmpty() }
    }

    /**
     * Creates an [ImageFile] if the file extension is supported.
     *
     * @param file The candidate file.
     * @param root The project root used to compute relative paths.
     * @return The created ImageFile, or null if not an image.
     */
    private fun createImageFile(file: File, root: File): ImageFile? {
        val ext = ImageExtension.from(file) ?: return null
        return ImageFile(
            file = file,
            extension = ext,
            relativePath = file.relativeTo(root).path
        )
    }
}
