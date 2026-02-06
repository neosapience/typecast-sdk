# Publishing to Maven Central via Central Portal

This guide explains how to manually publish the Typecast Kotlin SDK to Maven Central via the **Central Portal** (central.sonatype.com).

> **Note**: The legacy OSSRH service (s01.oss.sonatype.org) was shut down on June 30, 2025. All publishing must now go through the Central Portal.

## Prerequisites

1. **Central Portal Account**: Register at https://central.sonatype.com
2. **User Token**: Generate a token from Central Portal
3. **GPG Key**: For signing artifacts
4. **Gradle**: Included via Gradle Wrapper

## Configuration

### 1. Generate User Token

1. Log in to https://central.sonatype.com
2. Click on your username in the top right
3. Go to **View Account**
4. Click **Generate User Token**
5. Save the username and password (token credentials)

### 2. Environment Variables

Set the following environment variables:

```bash
export CENTRAL_USERNAME="your-token-username"
export CENTRAL_PASSWORD="your-token-password"
export GPG_SIGNING_KEY="base64-encoded-private-key"
export GPG_PASSPHRASE="your-gpg-passphrase"
```

### 3. Alternative: gradle.properties

Create or update `~/.gradle/gradle.properties`:

```properties
centralUsername=your-token-username
centralPassword=your-token-password
signing.keyId=YOUR_KEY_ID
signing.password=your-gpg-passphrase
signing.secretKeyRingFile=/path/to/secring.gpg
```

### 4. GPG Key Setup

```bash
# Import your GPG private key
gpg --import /path/to/private-key.asc

# Or import from base64-encoded key
echo "$GPG_SIGNING_KEY" | base64 -d | gpg --import

# Verify the key is imported
gpg --list-secret-keys

# Upload public key to keyserver (required for Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID

# Export for in-memory signing (base64 encoded)
gpg --export-secret-keys YOUR_KEY_ID | base64 > private-key-base64.txt
```

## Publishing

### Option 1: Using Environment Variables (Recommended)

```bash
# Set environment variables
export CENTRAL_USERNAME="your-token-username"
export CENTRAL_PASSWORD="your-token-password"
export GPG_SIGNING_KEY="$(cat private-key-base64.txt)"
export GPG_PASSPHRASE="your-gpg-passphrase"

# Build and publish to Maven Central
cd typecast-kotlin
./gradlew publishToMavenCentral
```

### Option 2: Using gradle.properties

If credentials are in `~/.gradle/gradle.properties`:

```bash
cd typecast-kotlin
./gradlew publishToMavenCentral
```

### Build Without Publishing

To build and verify locally without publishing:

```bash
./gradlew build
./gradlew publishToMavenLocal  # Publishes to ~/.m2/repository
```

## Verification

After release, verify the artifact is available:

```bash
# Check on Maven Central (may take up to 2 hours for search index)
https://search.maven.org/artifact/com.neosapience/typecast-kotlin

# Direct repository URL (available within 30 minutes)
https://repo1.maven.org/maven2/com/neosapience/typecast-kotlin/
```

## Usage

Once published, users can add the dependency:

### Gradle (Kotlin DSL)

```kotlin
implementation("com.neosapience:typecast-kotlin:1.0.0")
```

### Gradle (Groovy DSL)

```groovy
implementation 'com.neosapience:typecast-kotlin:1.0.0'
```

### Maven

```xml
<dependency>
    <groupId>com.neosapience</groupId>
    <artifactId>typecast-kotlin</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Troubleshooting

### GPG Signing Fails

```bash
# Check if gpg-agent is running
gpgconf --kill gpg-agent
gpg-agent --daemon

# For in-memory signing, ensure key is base64 encoded correctly
cat private-key.asc | base64 | tr -d '\n' > private-key-base64.txt
```

### Validation Fails

Common issues:
- Missing Javadoc JAR
- Missing Sources JAR
- Invalid POM metadata
- Missing GPG signature

Check the Central Portal Deployments page for specific error messages:
https://central.sonatype.com/publishing/deployments

### 401 Unauthorized

- Verify User Token credentials in environment variables or gradle.properties
- Make sure you're using the **token** credentials, not your account password
- Old OSSRH tokens do NOT work with Central Portal

### Signature Verification Failed

- Ensure public key is uploaded to a keyserver
- Try multiple keyservers: `keys.openpgp.org`, `keyserver.ubuntu.com`

## Version Management

Before releasing a new version:

1. Update version in `build.gradle.kts`
2. Update CHANGELOG if applicable
3. Commit and tag the release

```bash
# Example version update workflow
# Edit build.gradle.kts: version = "1.1.0"
git add build.gradle.kts
git commit -m "Bump version to 1.1.0"
git tag v1.1.0
git push origin main --tags
```

## Gradle Tasks Reference

| Task | Description |
|------|-------------|
| `./gradlew build` | Build the project |
| `./gradlew test` | Run unit tests |
| `./gradlew e2eTest` | Run E2E tests |
| `./gradlew publishToMavenCentral` | Publish to Maven Central via Central Portal |
| `./gradlew publishToMavenLocal` | Publish to local Maven repo |
| `./gradlew clean` | Clean build artifacts |

## Central Portal vs Legacy OSSRH

| Feature | Central Portal (New) | OSSRH (Deprecated) |
|---------|---------------------|-------------------|
| URL | central.sonatype.com | s01.oss.sonatype.org |
| Gradle Plugin | nmcp (community) | maven-publish + nexus |
| Authentication | User Token | JIRA credentials |
| Status | Active | Shut down (June 2025) |

## Links

- [Central Portal](https://central.sonatype.com)
- [Publishing Documentation](https://central.sonatype.org/publish/publish-portal-gradle/)
- [Token Generation](https://central.sonatype.org/publish/generate-portal-token/)
- [NMCP Gradle Plugin](https://github.com/GradleUp/nmcp)
