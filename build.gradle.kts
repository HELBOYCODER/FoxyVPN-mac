import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.2.0"
    id("org.jetbrains.compose") version "1.9.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
        )
    }
}

sourceSets {
    main {
        kotlin.srcDir("src/main/kotlin/compat")
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.components.resources)
    implementation("org.jetbrains.compose.material:material-icons-extended:1.6.11")
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20240303")

    implementation("io.netty:netty-handler:4.1.115.Final")
    implementation("io.netty:netty-codec-http2:4.1.115.Final")
    implementation("io.netty:netty-transport:4.1.115.Final")
    implementation("io.netty:netty-handler-proxy:4.1.115.Final")

    implementation("org.conscrypt:conscrypt-openjdk-uber:2.5.2")

    testImplementation("junit:junit:4.13.2")
}

tasks.register<JavaExec>("runHelperTest") {
    group = "verification"
    description = "Drives the privileged TUN helper end-to-end without the UI"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass = "com.vauth.foxyvpn.tools.HelperTestKt"
    (project.findProperty("helperArgs") as? String)?.let { argumentProviders.add { listOf(it) } }
}

compose.desktop {
    application {
        mainClass = "com.vauth.foxyvpn.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "FoxyVPN"
            packageVersion = "1.0.4"
            vendor = "vauth"
            description = "Unofficial Firefox VPN client for macOS"
            appResourcesRootDir = project.layout.projectDirectory.dir("app-resources")

            macOS {
                bundleID = "com.vauth.foxyvpn.mac"
                minimumSystemVersion = "11.0"
                dockName = "FoxyVPN"
            }
        }
    }
}

tasks.matching { it.name == "prepareAppResources" || it.name.startsWith("package") || it.name.startsWith("createRuntimeImage") }.configureEach {
    dependsOn("syncAppResources")
}

tasks.register<Copy>("syncAppResources") {
    from("vendor")
    into("app-resources/macos")
    filePermissions { unix("755") }
}
