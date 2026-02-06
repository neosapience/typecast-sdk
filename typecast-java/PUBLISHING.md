# Publishing to Maven Central via Central Portal

This guide explains how to manually publish the Typecast Java SDK to Maven Central via the **Central Portal** (central.sonatype.com).

> **Note**: The legacy OSSRH service (s01.oss.sonatype.org) was shut down on June 30, 2025. All publishing must now go through the Central Portal.

## Prerequisites

1. **Central Portal Account**: Register at https://central.sonatype.com
2. **User Token**: Generate a token from Central Portal
3. **GPG Key**: For signing artifacts
4. **Maven**: Version 3.6+ installed

## Configuration

### 1. Generate User Token

1. Log in to https://central.sonatype.com
2. Click on your username in the top right
3. Go to **View Account**
4. Click **Generate User Token**
5. Save the username and password (token credentials)

### 2. Maven settings.xml

Create or update `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username>YOUR_TOKEN_USERNAME</username>
      <password>YOUR_TOKEN_PASSWORD</password>
    </server>
  </servers>
  <profiles>
    <profile>
      <id>central</id>
      <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <properties>
        <gpg.keyname>YOUR_GPG_KEY_ID</gpg.keyname>
        <!-- Read GPG passphrase from environment variable -->
        <gpg.passphrase>${env.GPG_PASSPHRASE}</gpg.passphrase>
      </properties>
    </profile>
  </profiles>
</settings>
```

> **Security Note**: Never pass the GPG passphrase directly on the command line (e.g., `-Dgpg.passphrase="..."`), as it may be exposed in shell history or process listings. Use environment variables instead.

### 3. GPG Key Setup

```bash
# Import your GPG private key
gpg --import /path/to/private-key.asc

# Or import from base64-encoded key
echo "$GPG_SIGNING_KEY" | base64 -d | gpg --import

# Verify the key is imported
gpg --list-secret-keys

# Upload public key to keyserver (required for Maven Central)
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
```

## Publishing

### Option 1: Using Environment Variables (Recommended)

Set the GPG passphrase as an environment variable before running Maven:

```bash
# Export the GPG passphrase (do this once per terminal session)
export GPG_PASSPHRASE="YOUR_GPG_PASSPHRASE"

# Build and deploy
cd typecast-java
mvn clean deploy
```

The passphrase will be read from the `GPG_PASSPHRASE` environment variable via your `settings.xml` configuration (see above).

**Alternative: Configure maven-gpg-plugin in pom.xml**

If you prefer to configure the passphrase source directly in the project, add this to your `pom.xml`:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-gpg-plugin</artifactId>
    <version>3.1.0</version>
    <configuration>
        <passphrase>${env.GPG_PASSPHRASE}</passphrase>
    </configuration>
</plugin>
```

### Option 2: Interactive GPG Passphrase

```bash
cd typecast-java
mvn clean deploy
# GPG will prompt for passphrase if not configured via environment variable
```

### Build Output

A successful publish will look similar to:

```
[INFO] --- central-publishing-maven-plugin:0.7.0:publish (default-cli) @ typecast-java ---
[INFO] Using Central baseUrl: https://central.sonatype.com
[INFO] Using credentials from server id central in settings.xml
[INFO] Staging files...
[INFO] Created bundle successfully
[INFO] Going to upload bundle...
[INFO] Uploaded bundle successfully
[INFO] Waiting until Deployment is validated
[INFO] Deployment has been validated and published
[INFO] BUILD SUCCESS
```

## Verification

After release, verify the artifact is available:

```bash
# Check on Maven Central (may take up to 2 hours for search index)
https://search.maven.org/artifact/com.neosapience/typecast-java

# Direct repository URL (available within 30 minutes)
https://repo1.maven.org/maven2/com/neosapience/typecast-java/
```

## Usage

Once published, users can add the dependency:

### Maven

```xml
<dependency>
    <groupId>com.neosapience</groupId>
    <artifactId>typecast-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'com.neosapience:typecast-java:1.0.0'
```

## Troubleshooting

### GPG Signing Fails

```bash
# Check if gpg-agent is running
gpgconf --kill gpg-agent
gpg-agent --daemon

# For macOS, you may need pinentry-mac
brew install pinentry-mac
echo "pinentry-program /usr/local/bin/pinentry-mac" >> ~/.gnupg/gpg-agent.conf
```

### Validation Fails

Common issues:

- Missing Javadoc JAR
- Missing Sources JAR
- Invalid POM metadata (missing required elements)
- Missing GPG signature

Check the Central Portal Deployments page for specific error messages:
https://central.sonatype.com/publishing/deployments

### 401 Unauthorized

- Verify User Token credentials in `settings.xml`
- Ensure the `<server><id>` matches `central`
- Make sure you're using the **token** credentials, not your account password

### Token Issues

- User tokens are different from your account password
- Tokens can be regenerated if lost
- Old OSSRH tokens do NOT work with Central Portal

## Version Management

Before releasing a new version:

1. Update version in `pom.xml`
2. Update CHANGELOG if applicable
3. Commit and tag the release

```bash
# Example version update workflow
mvn versions:set -DnewVersion=1.1.0
git add pom.xml
git commit -m "Bump version to 1.1.0"
git tag v1.1.0
git push origin main --tags
```

## Central Portal vs Legacy OSSRH

| Feature        | Central Portal (New)            | OSSRH (Deprecated)         |
| -------------- | ------------------------------- | -------------------------- |
| URL            | central.sonatype.com            | s01.oss.sonatype.org       |
| Plugin         | central-publishing-maven-plugin | nexus-staging-maven-plugin |
| Authentication | User Token                      | JIRA credentials           |
| Status         | Active                          | Shut down (June 2025)      |

## Links

- [Central Portal](https://central.sonatype.com)
- [Publishing Documentation](https://central.sonatype.org/publish/publish-portal-maven/)
- [Token Generation](https://central.sonatype.org/publish/generate-portal-token/)
