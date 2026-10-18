# Contributing to CommandHub

Thanks for your interest in improving CommandHub.

This project is primarily developed by the original author, but bug reports, feature
suggestions, and pull requests are all welcome.

## Getting set up

```bash
git clone https://github.com/Jedimig08/CommandHub.git
cd CommandHub
```

Requirements:

- Android Studio Meerkat or newer
- Android SDK 36 (`minSdk` 26)
- JDK 17+ — if `java` is not on your `PATH`, point Gradle at Android Studio's bundled JDK:

  ```bash
  export JAVA_HOME=/opt/android-studio/jbr
  ```

Open the project in Android Studio and let Gradle sync, or build from the command line.

**A physical Android device is required** to run the app. Emulators lack the USB,
Bluetooth, and camera hardware this project depends on.

## Before you open a pull request

Run all three of these and make sure they pass:

```bash
JAVA_HOME=/opt/android-studio/jbr ./gradlew compileDebugKotlin
JAVA_HOME=/opt/android-studio/jbr ./gradlew testDebugUnitTest
JAVA_HOME=/opt/android-studio/jbr ./gradlew lintDebug
```

Then manually test your change on a real device. If your change affects the web API, hit
the endpoints directly with `curl` — see [API.md](API.md) for the full reference.

## Coding conventions

- **Follow the existing style.** Match the formatting and naming of the file you are
  editing.
- **One subsystem per file.** Keep hardware-specific logic in its own manager class under
  `viewmodel/`, and HTTP/WebSocket endpoints in the matching module under
  `server/routes/`.
- **HTML belongs in assets.** Web pages go in `app/src/main/assets/web/*.html` and are
  filled in by `WebAssetLoader` with `{{placeholder}}` substitution. Do not build pages
  with inline Kotlin strings.
- **Keep async code on coroutines and StateFlow** so the UI updates reactively.
- **Document non-obvious logic** with a short comment. Do not restate what the code says.
- **No committed IDE or build output.** `.idea/`, `build/`, and `local.properties` are
  gitignored.

## Changing the web API

If you add or modify an endpoint, update **`API.md`** in the same pull request:

1. Document the route, its query parameters, and its request/response schema.
2. Add or update the JSON schema under *Data Schemas* if you introduced a new
   `@Serializable` data class.
3. Add a `curl` example if the endpoint is useful from the command line.

## Adding a new dashboard widget

1. Define the widget type and its data in `data/`.
2. Render it in `ui/DashboardPage.kt`.
3. Add it to `app/src/main/assets/layout.json` (or document how a user adds it).
4. Note the new capability in `README.md`.

## Reporting bugs

Use the **Bug report** issue template. It asks for the affected subsystem, reproduction
steps, your device, and logs — all of which are needed to reproduce a hardware-dependent
issue.

For security issues, see [SECURITY.md](SECURITY.md) and **do not open a public issue**.

## Suggesting features

Use the **Feature request** issue template. Start with the problem you are trying to
solve rather than the implementation you have in mind.

## Pull request process

1. Fork the repository and create a branch from `main`.
2. Make your changes, keeping commits scoped and descriptive.
3. Update documentation (`README.md`, `API.md`, `CHANGELOG.md`) where behaviour changed.
4. Open a PR using the pull request template and fill in how you tested it.
5. Be ready to discuss the design — larger changes are easier to land if the approach is
   agreed on first.
