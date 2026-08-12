import groovy.json.JsonSlurper
import java.io.File
import java.security.MessageDigest

val artifactProperties = java.util.Properties()
val artifactPropertiesFile = rootProject.file("platform-artifacts.properties")
if (artifactPropertiesFile.isFile) {
    artifactPropertiesFile.inputStream().use(artifactProperties::load)
}
val configuredArtifactsDirectory =
    providers.gradleProperty("platformArtifactsDir").orNull
        ?: artifactProperties.getProperty("platformArtifactsDir")
        ?: "platform-artifacts/api-37"
val platformArtifactsDirectory = rootProject.file(configuredArtifactsDirectory)
rootProject.extensions.extraProperties["platformArtifactsDirectory"] = platformArtifactsDirectory

fun sha256(file: File): String =
    file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

tasks.register("verifyPlatformArtifacts") {
    group = "verification"
    description = "Verifies the AAOS API 37 platform artifact lock before an APK build."
    val lockFile = rootProject.file("platform-artifacts.lock.json")
    inputs.file(lockFile)
    inputs.dir(platformArtifactsDirectory)
    doLast {
        check(lockFile.isFile) { "Platform artifact lock is missing: ${lockFile.absolutePath}" }
        @Suppress("UNCHECKED_CAST")
        val lock = JsonSlurper().parse(lockFile) as Map<String, Any?>
        check(lock["apiLevel"] == 37) { "Platform artifact lock must target API 37." }
        @Suppress("UNCHECKED_CAST")
        val artifacts = lock["artifacts"] as? List<Map<String, Any?>>
            ?: error("Platform artifact lock has no artifact list.")
        val errors = artifacts.mapNotNull { entry ->
            val name = entry["name"] as? String ?: return@mapNotNull "Artifact without a name."
            val expected = entry["sha256"] as? String ?: return@mapNotNull "$name has no checksum."
            val artifact = platformArtifactsDirectory.resolve(name)
            when {
                expected.startsWith("PENDING_") -> "$name is not pinned; run scripts/sync-platform-artifacts.ps1."
                !artifact.isFile -> "Missing $name at ${artifact.absolutePath}."
                sha256(artifact) != expected -> "$name checksum does not match platform-artifacts.lock.json."
                else -> null
            }
        }
        check(errors.isEmpty()) {
            "Invalid AAOS platform artifacts:\n${errors.joinToString("\n")}" 
        }
    }
}
