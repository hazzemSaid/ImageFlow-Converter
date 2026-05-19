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

    private fun scanGeneralProject(root: File): List<ImageFile> {
        return root.walkTopDown()
            .onEnter { dir -> dir.name !in IGNORED_DIRS }
            .filter { it.isFile }
            .mapNotNull { file -> createImageFile(file, root) }
            .sortedBy { it.relativePath }
            .toList()
    }

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
     * A simple parser to extract asset paths from pubspec.yaml
     */
    internal fun extractFlutterAssets(pubspecFile: File): List<String> {
        val lines = pubspecFile.readLines()
        val assets = mutableListOf<String>()
        var inFlutterSection = false
        var inAssetsSection = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) continue // skip comments

            if (trimmed == "flutter:") {
                inFlutterSection = true
                inAssetsSection = false
                continue
            }

            if (inFlutterSection && trimmed == "assets:") {
                inAssetsSection = true
                continue
            }

            if (inFlutterSection && inAssetsSection) {
                if (trimmed.startsWith("-")) {
                    val path = trimmed.substring(1).trim().trimEnd('/')
                    if (path.isNotEmpty()) {
                        assets.add(path)
                    }
                } else if (line.isNotEmpty() && !line.startsWith(" ") && !line.startsWith("-")) {
                    // If we meet a line that is not indented, we left the flutter/assets section
                    inFlutterSection = false
                    inAssetsSection = false
                }
            }
        }
        return assets
    }

    private fun createImageFile(file: File, root: File): ImageFile? {
        val ext = ImageExtension.from(file) ?: return null
        return ImageFile(
            file = file,
            extension = ext,
            relativePath = file.relativeTo(root).path
        )
    }
}
