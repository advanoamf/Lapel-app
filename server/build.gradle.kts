import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation("com.lapel:domain:1.0")
    implementation("com.amazonaws:aws-lambda-java-core:1.4.0")
    implementation("com.amazonaws:aws-lambda-java-events:3.16.1")
    implementation(platform("software.amazon.awssdk:bom:2.55.12"))
    implementation("software.amazon.awssdk:dynamodb") {
        // Lighter HTTP client for faster Lambda start-up.
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")
    implementation(libs.kotlinx.serialization.json)

    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.launcher)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

/** Deployment package for AWS Lambda: classes at the root, dependencies in lib/. */
val lambdaZip by tasks.registering(Zip::class) {
    archiveFileName.set("lapel-server.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    from(sourceSets.main.get().output)
    into("lib") { from(configurations.runtimeClasspath) }
}

tasks.build { dependsOn(lambdaZip) }
