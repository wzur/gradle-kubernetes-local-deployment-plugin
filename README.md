# Gradle Kubernetes Local Deployment Plugin

A Gradle plugin project for managing local Kubernetes development environments. The root build currently includes the Kind plugin module.

For plugin installation, configuration, and task usage, see the [Kind plugin documentation](gradle-kind-plugin/README.md).

## Project Layout

- `gradle-kind-plugin/` contains the plugin implementation, unit and functional tests, and its usage documentation.
- `build-logic/` contains shared Gradle convention plugins used by the project builds.

## Development

Requirements: JDK 21 and the Gradle wrapper included in this repository.

Run the complete check suite, including unit and functional tests:

```shell
./gradlew check
```

Run checks for the Kind plugin module only:

```shell
./gradlew :gradle-kind-plugin:test
./gradlew :gradle-kind-plugin:functionalTest
./gradlew :gradle-kind-plugin:spotlessCheck
```

Apply the configured formatter with:

```shell
./gradlew :gradle-kind-plugin:spotlessApply
```

## Versioning and Releases

The plugin module applies the [Semver Gradle Plugin](https://github.com/JavierSegoviaCordoba/semver-gradle-plugin), which derives its version from Git tags. Tags are unprefixed by default. Run release tasks from the `gradle-kind-plugin` project path.

The version is calculated from Git history. Commit the release changes and use a clean working tree before previewing it:

```shell
./gradlew :gradle-kind-plugin:printSemver
```

The preview is authoritative. A checkout with no SemVer tags uses a commit-derived bootstrap version; establish the initial release tag deliberately. After a baseline tag exists, use `major`, `minor`, or `patch` to choose the stable release bump. For a patch release:

```shell
./gradlew :gradle-kind-plugin:createSemverTag \
	-Psemver.stage=final \
	-Psemver.scope=patch
```

To create and push the tag in one step, use `pushSemverTag` instead of `createSemverTag`:

```shell
./gradlew :gradle-kind-plugin:pushSemverTag \
	-Psemver.stage=final \
	-Psemver.scope=patch
```

The push task uses the `origin` remote by default; select another with `-Psemver.remote=<remote>`. These tasks manage Git tags, not artifact publication. The Kind plugin is configured for the Gradle Plugin Portal.

## Publishing

Set the Plugin Portal API key and secret in `$HOME/.gradle/gradle.properties` (or provide `GRADLE_PUBLISH_KEY` and `GRADLE_PUBLISH_SECRET` in CI):

```properties
gradle.publish.key=<key>
gradle.publish.secret=<secret>
```

From the repository root, validate the portal publication without uploading:

```shell
./gradlew :gradle-kind-plugin:publishPlugins --validate-only
```

Publish after validation with:

```shell
./gradlew :gradle-kind-plugin:publishPlugins
```

## Disclaimer

This project has been started to learn a bit more about Gradle internals. It is a by-product
of a take-home task during a recruitment process. Due to these circumstances, it hasn't been
tested on anything but Linux. If you find it useful, feel free to drop me a line.
