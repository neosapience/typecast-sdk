plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    `maven-publish`
    `java-library`
}

group = "io.typecast"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // HTTP Client
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Environment Variables
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    
    // Coroutines (optional, for async support)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
}

tasks.test {
    useJUnitPlatform()
    // Exclude E2E tests from regular test runs
    exclude("**/*E2ETest.class")
}

// Task for running E2E tests
tasks.register<Test>("e2eTest") {
    useJUnitPlatform()
    include("**/*E2ETest.class")
    description = "Run E2E tests against real API"
    group = "verification"
}

kotlin {
    jvmToolchain(17)
}

java {
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
                url.set("https://github.com/typecast-ai/typecast-kotlin")
                
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                
                developers {
                    developer {
                        id.set("typecast")
                        name.set("Typecast Team")
                        email.set("help@typecast.ai")
                    }
                }
                
                scm {
                    connection.set("scm:git:git://github.com/typecast-ai/typecast-kotlin.git")
                    developerConnection.set("scm:git:ssh://github.com:typecast-ai/typecast-kotlin.git")
                    url.set("https://github.com/typecast-ai/typecast-kotlin")
                }
            }
        }
    }
}
