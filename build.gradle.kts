plugins {
    java
}

group = "it.unlocksecurity.burp"
// Releases get their version from the git tag computed by semantic-release (-PreleaseVersion=x.y.z).
version = providers.gradleProperty("releaseVersion").getOrElse("0.0.0-SNAPSHOT")

repositories {
    mavenCentral()
}

dependencies {
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.7")
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("net.portswigger.burp.extensions:montoya-api:2026.7")
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
    // -PpreviewUi renders the screenshot window and suite tab too, which needs a desktop session.
    val previewUi = project.hasProperty("previewUi")
    systemProperty("java.awt.headless", (!previewUi).toString())
    if (previewUi) {
        systemProperty("preview.ui", "true")
    }
    systemProperty("preview.dir", layout.buildDirectory.dir("preview").get().asFile.absolutePath)
}

// Burp loads a single jar, so bundle the runtime dependencies (Gson) into it.
tasks.jar {
    archiveFileName = "screenshot-mode-${project.version}.jar"
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.map { files -> files.map { if (it.isDirectory) it else zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/versions/**/module-info.class", "module-info.class")
}
