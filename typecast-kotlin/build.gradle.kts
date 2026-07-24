import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    kotlin("plugin.serialization") version "2.4.0"
    `maven-publish`
    `java-library`
    signing
    id("com.gradleup.nmcp") version "1.6.1"
    id("com.gradleup.nmcp.aggregation") version "1.6.1"
    jacoco
}

jacoco {
    toolVersion = "0.8.15"
}

group = "com.neosapience"
version = "1.2.9"

repositories {
    mavenCentral()
}

dependencies {
    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    
    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    
    // Environment Variables
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")
    
    // Coroutines (optional, for async support)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    // NMCP aggregation (Central Portal publishing)
    nmcpAggregation(project(":"))
}

tasks.test {
    useJUnitPlatform()
    // Exclude E2E tests from regular test runs
    exclude("**/*E2ETest.class")
    // Open java.lang to allow reflective env mutation in unit tests
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED"
    )
    finalizedBy(tasks.jacocoTestReport)
}

// Task for running E2E tests
tasks.register<Test>("e2eTest") {
    useJUnitPlatform()
    include("**/*E2ETest.class")
    description = "Run E2E tests against real API"
    group = "verification"
}

// Exclusions for kotlinx.serialization compiler-generated classes that
// cannot be exercised through public API tests:
//  - $$serializer:        synthetic KSerializer implementations
//  - $Companion:          companion objects providing the serializer() factory
//  - $Companion$1:        synthetic lambda inside companion (one per @Serializable type)
// These are pure framework plumbing emitted by the kotlinx.serialization
// compiler plugin and contain no project logic.
//
val coverageExclusions = listOf(
    "**/*\$\$serializer.*",
    "**/*\$Companion.*",
    "**/*\$Companion\$*.*",
    // Example runnable classes require a real API key and are not unit-testable
    "**/examples/**"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        })
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(coverageExclusions) }
        })
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
            limit {
                counter = "METHOD"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            
            pom {
                name.set("Typecast Kotlin SDK")
                description.set("Official Kotlin SDK for Typecast Text-to-Speech API")
                url.set("https://github.com/neosapience/typecast-sdk/tree/main/typecast-kotlin")
                
                licenses {
                    license {
                        name.set("Apache License 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0")
                    }
                }
                
                developers {
                    developer {
                        id.set("neosapience")
                        name.set("Neosapience")
                        email.set("help@typecast.ai")
                        organization.set("Neosapience")
                        organizationUrl.set("https://typecast.ai")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/neosapience/typecast-sdk.git")
                    developerConnection.set("scm:git:ssh://github.com:neosapience/typecast-sdk.git")
                    url.set("https://github.com/neosapience/typecast-sdk/tree/main/typecast-kotlin")
                }
            }
        }
    }
}

signing {
    useGpgCmd()
    sign(publishing.publications["maven"])
}

// Central Portal Publishing Configuration
nmcp {
    publishAllPublicationsToCentralPortal {
        username = System.getenv("CENTRAL_USERNAME") ?: findProperty("centralUsername")?.toString()
        password = System.getenv("CENTRAL_PASSWORD") ?: findProperty("centralPassword")?.toString()
        publishingType = "AUTOMATIC"
    }
}

nmcpAggregation {
    centralPortal {
        username = System.getenv("CENTRAL_USERNAME") ?: findProperty("centralUsername")?.toString()
        password = System.getenv("CENTRAL_PASSWORD") ?: findProperty("centralPassword")?.toString()
        publishingType = "AUTOMATIC"
    }
}

tasks.register("printRuntimeClasspath") {
    doLast {
        val cp = configurations.runtimeClasspath.get().asPath
        println("CLASSPATH=$cp")
    }
}
