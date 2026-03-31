import org.gradle.api.tasks.JavaExec

val defaultComparisonSteps = "5"
val defaultComparisonJudge = "true"

plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("ai.djl.huggingface:tokenizers:0.33.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    runtimeOnly("org.slf4j:slf4j-nop:2.0.17")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "MainKt"
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8")
}

tasks.named<JavaExec>("run") {
    defaultCharacterEncoding = "UTF-8"
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8"
    )
}

tasks.register<JavaExec>("compareStrategies") {
    group = "application"
    description = "Запускает dev-инструмент для сравнения стратегий памяти."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("devtools.comparison.StrategyComparisonMainKt")
    defaultCharacterEncoding = "UTF-8"
    jvmArgs(
        "-Dfile.encoding=UTF-8",
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "-Dsun.stdout.encoding=UTF-8",
        "-Dsun.stderr.encoding=UTF-8"
    )

    val comparisonSteps = project.findProperty("comparisonSteps")?.toString()?.takeIf { it.isNotBlank() }
        ?: defaultComparisonSteps
    systemProperty("comparison.steps", comparisonSteps)

    val comparisonJudge = project.findProperty("comparisonJudge")?.toString()?.takeIf { it.isNotBlank() }
        ?: defaultComparisonJudge
    systemProperty("comparison.judge", comparisonJudge)
}

tasks.test {
    useJUnitPlatform()
}
