import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.api.publish.maven.MavenPublication
import java.util.Locale

plugins {
    application
    java
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jspecify:jspecify:1.0.0")
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.27.3")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

group = "com.github.julia_script"
version = providers.gradleProperty("releaseVersion").orElse("0.1.0-SNAPSHOT").get()

application {
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}

val quickjsVersion = "0.14.0"
val quickjsArchiveUrl = "https://github.com/quickjs-ng/quickjs/archive/refs/tags/v$quickjsVersion.tar.gz"
val quickjsArchiveFile = layout.buildDirectory.file("downloads/quickjs-$quickjsVersion.tar.gz")
val quickjsSourceDir = layout.buildDirectory.dir("quickjs-src")
val nativeRootDir = layout.buildDirectory.dir("native")
val legacyNativeLibraryFile = layout.buildDirectory.file("native/libquickjs.dylib")
val embeddedNativesDir = layout.buildDirectory.dir("generated-resources/main/natives")

data class NativeTarget(
    val id: String,
    val outputFileName: String,
    val zigTargetTriple: String,
    val sharedLibraryFlag: String,
    val commandArgs: (String, String) -> List<String>,
)

val zigCommand = providers.gradleProperty("zigCommand").orElse("zig").get()

val nativeTargets = listOf(
    NativeTarget(
        id = "macos-aarch64",
        outputFileName = "libquickjs.dylib",
        zigTargetTriple = "aarch64-macos",
        sharedLibraryFlag = "-dynamiclib",
        commandArgs = { sourcePath, outputPath ->
            listOf(
                "-O2",
                "-fPIC",
                "-I", sourcePath,
                "$sourcePath/quickjs.c",
                "$sourcePath/libregexp.c",
                "$sourcePath/libunicode.c",
                "$sourcePath/dtoa.c",
                "-lm",
                "-o", outputPath,
            )
        },
    ),
    NativeTarget(
        id = "linux-x86_64",
        outputFileName = "libquickjs.so",
        zigTargetTriple = "x86_64-linux-gnu",
        sharedLibraryFlag = "-shared",
        commandArgs = { sourcePath, outputPath ->
            listOf(
                "-O2",
                "-fPIC",
                "-I", sourcePath,
                "$sourcePath/quickjs.c",
                "$sourcePath/libregexp.c",
                "$sourcePath/libunicode.c",
                "$sourcePath/dtoa.c",
                "-lm",
                "-o", outputPath,
            )
        },
    ),
    NativeTarget(
        id = "windows-x86_64",
        outputFileName = "quickjs.dll",
        zigTargetTriple = "x86_64-windows-gnu",
        sharedLibraryFlag = "-shared",
        commandArgs = { sourcePath, outputPath ->
            listOf(
                "-O2",
                "-I", sourcePath,
                "$sourcePath/quickjs.c",
                "$sourcePath/libregexp.c",
                "$sourcePath/libunicode.c",
                "$sourcePath/dtoa.c",
                "-Wl,--export-all-symbols",
                "-funsigned-char",
                "-fno-omit-frame-pointer",
                "-fno-sanitize=undefined",
                "-fno-sanitize-trap=undefined",
                "-fvisibility=hidden",
                "-o", outputPath,
            )
        },
    ),
)

val targetById = nativeTargets.associateBy { it.id }
val detectedHostTargetId = when {
    System.getProperty("os.name").lowercase(Locale.US).contains("mac") -> "macos-aarch64"
    System.getProperty("os.name").lowercase(Locale.US).contains("linux") -> "linux-x86_64"
    System.getProperty("os.name").lowercase(Locale.US).contains("windows") -> "windows-x86_64"
    else -> "macos-aarch64"
}
val hostTargetId = providers.gradleProperty("hostNativeTarget").orElse(detectedHostTargetId).get()
val hostTarget = targetById[hostTargetId] ?: error("Unknown hostNativeTarget: $hostTargetId")

val downloadQuickJs by tasks.registering(Exec::class) {
    val archivePath = quickjsArchiveFile.get().asFile.absolutePath
    outputs.file(archivePath)
    doFirst {
        quickjsArchiveFile.get().asFile.parentFile.mkdirs()
    }
    commandLine("curl", "-L", quickjsArchiveUrl, "-o", archivePath)
}

val extractQuickJs by tasks.registering(Exec::class) {
    dependsOn(downloadQuickJs)
    val archivePath = quickjsArchiveFile.get().asFile.absolutePath
    val sourcePath = quickjsSourceDir.get().asFile.absolutePath
    outputs.dir(sourcePath)
    doFirst {
        val sourceDir = quickjsSourceDir.get().asFile
        sourceDir.mkdirs()
    }
    commandLine("tar", "-xzf", archivePath, "--strip-components=1", "-C", sourcePath)
}

val nativeBuildTasks = nativeTargets.associateWith { target ->
    val taskName = "buildQuickJs${target.id.split("-").joinToString("") { it.replaceFirstChar { c -> c.titlecase(Locale.US) } }}"
    tasks.register(taskName, Exec::class) {
        dependsOn(extractQuickJs)
        val sourcePath = quickjsSourceDir.get().asFile.absolutePath
        val outputFile = layout.buildDirectory.file("native/${target.id}/${target.outputFileName}").get().asFile
        outputs.file(outputFile.absolutePath)
        doFirst {
            outputFile.parentFile.mkdirs()
        }
        commandLine(
            zigCommand,
            "cc",
            "-target",
            target.zigTargetTriple,
            target.sharedLibraryFlag,
            *target.commandArgs(sourcePath, outputFile.absolutePath).toTypedArray()
        )
    }
}

val buildNativeMac by tasks.registering {
    dependsOn(nativeBuildTasks.getValue(targetById.getValue("macos-aarch64")))
}

val buildNativeLinux by tasks.registering {
    dependsOn(nativeBuildTasks.getValue(targetById.getValue("linux-x86_64")))
}

val buildNativeWindows by tasks.registering {
    dependsOn(nativeBuildTasks.getValue(targetById.getValue("windows-x86_64")))
}

val buildNativeAll by tasks.registering {
    dependsOn(nativeBuildTasks.values)
}

val assembleNativeDist by tasks.registering {
    dependsOn(buildNativeAll)
    doLast {
        // Remove legacy pre-matrix artifact location if present.
        legacyNativeLibraryFile.get().asFile.delete()
    }
}

val stageEmbeddedNatives by tasks.registering(Copy::class) {
    dependsOn(buildNativeAll)
    from(nativeRootDir)
    into(embeddedNativesDir)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(stageEmbeddedNatives)
    from(embeddedNativesDir) {
        into("natives")
    }
}

tasks.named<JavaExec>("run") {
    dependsOn(nativeBuildTasks.getValue(hostTarget))
    jvmArgs("--enable-native-access=ALL-UNNAMED", "-Dquickjs.native.target=$hostTargetId")
}

tasks.named("compileJava") {
    dependsOn(nativeBuildTasks.getValue(hostTarget))
}

tasks.withType<Test>().configureEach {
    dependsOn(nativeBuildTasks.getValue(hostTarget))
    useJUnitPlatform()
    jvmArgs(
        "--enable-native-access=ALL-UNNAMED",
        "-Dquickjs.native.target=$hostTargetId",
        // Temurin 22 + Panama: rare SIGSEGV in ServiceThread (ResolvedMethodTable::grow, G1BarrierSet).
        // Serial GC avoids G1 write barriers in that stack; C1-only tiering reduces MH/C2 churn.
        "-XX:+UseSerialGC",
        "-XX:TieredStopAtLevel=1",
    )
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = project.name
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            val githubRepository = providers.gradleProperty("githubRepository")
                .orElse(System.getenv("GITHUB_REPOSITORY") ?: "julia-script/java-quickjs")
                .get()
            url = uri("https://maven.pkg.github.com/$githubRepository")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orElse("")
                    .get()
                password = providers.gradleProperty("gpr.key")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orElse("")
                    .get()
            }
        }
    }
}
