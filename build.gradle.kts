import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

plugins {
    id("java")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.changelog)
}

group = providers.gradleProperty("pluginGroup").get()

val baseVersion = providers.gradleProperty("pluginVersion").get()
val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "3"
version = "$baseVersion.$runNumber"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())

        bundledPlugins("org.jetbrains.kotlin", "com.intellij.java")
        pluginVerifier()
        zipSigner()
    }

    // WebP support
    implementation("org.sejda.imageio:webp-imageio:0.1.6")

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

intellijPlatform {
    buildSearchableOptions.set(false)

    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = provider { "$baseVersion.$runNumber" }

        description = providers.fileContents(
            layout.projectDirectory.file("README.md")
        ).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException(
                        "Plugin description section not found in README.md:\n$start ... $end"
                    )
                }

                subList(indexOf(start) + 1, indexOf(end))
                    .joinToString("\n")
                    .let(::markdownToHTML)
            }
        }

        val changelog = project.changelog
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild").orNull?.let { value ->
                value.ifBlank { null }
            }?.let { provider { it } } ?: provider { null }
        }

        vendor {
            name = providers.gradleProperty("pluginVendorName")
            email = providers.gradleProperty("pluginVendorEmail")
            url = providers.gradleProperty("pluginRepositoryUrl")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = providers.gradleProperty("pluginVersion").map {
            listOf(
                it.substringAfter('-', "")
                    .substringBefore('.')
                    .ifEmpty { "default" }
            )
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    versionPrefix = ""
    headerParserRegex = Regex("""(\d+\.\d+\.\d+)""")
}

tasks.test {
    useJUnitPlatform()
}