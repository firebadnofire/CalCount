# CalCount

CalCount is a native Android calorie counter focused on fast manual food entry,
reliable serving math, and clear daily tracking. The app stores nutrition values
per serving, then calculates consumed calories and nutrients from either serving
counts or measured amounts.

The current app is built with Kotlin, Android Views, Material Components,
Jetpack Navigation, ViewModel, LiveData, and local SharedPreferences-backed JSON
persistence.

## Features

- Create reusable food and liquid entries from nutrition-label-style fields.
- Store all nutrition values per serving.
- Log foods by serving count or by weight.
- Log liquids by serving count or by volume.
- Preview calculated calories and macros before saving a log entry.
- Track daily calories consumed and remaining.
- View macro summaries and meal sections for the current day.
- Browse food library entries and recent foods.
- Review past days in the history screen.
- Configure daily calorie goals, macro targets, display preferences, preferred
  weight unit, preferred liquid unit, and Material You behavior.
- Supports required nutrients plus optional useful and extended nutrients such
  as fiber, sugars, sodium, potassium, cholesterol, vitamins, and minerals.

## Product Scope

CalCount is intentionally a clean manual tracker. Version 1 is centered on:

- Fast food creation.
- Accurate serving, weight, and volume conversion.
- Reusable local food entries.
- Clear daily progress.
- Simple Material 3-style Android UI.

The app does not currently include barcode scanning, an online food database,
photo recognition, recipes, meal planning, social features, or cloud sync.

## Core Nutrition Rules

All food nutrition is stored per serving.

For serving-based logging:

```text
calories = perServingCalories * servings
```

For weight-based food logging:

```text
servingFraction = consumedWeightGrams / servingWeightGrams
calories = perServingCalories * servingFraction
```

For volume-based liquid logging:

```text
servingFraction = consumedVolumeMilliliters / servingVolumeMilliliters
calories = perServingCalories * servingFraction
```

Example:

```text
Serving = 55 g
Calories = 220
Consumed = 27.5 g

27.5 / 55 = 0.5 servings
220 * 0.5 = 110 calories
```

The calculator rejects zero or negative serving amounts where they would make
nutrition math invalid.

## Screens

- **Today**: daily calories, remaining calories, macro summary, and meal
  sections.
- **History**: previous days and day detail views.
- **Food Library**: saved foods, recent foods, and logging entry points.
- **Create Food**: form for creating and editing nutrition-label-style entries.
- **Log Food**: dialog for choosing meal type, input mode, and amount with live
  preview.
- **Settings**: calorie goals, macro targets, preferred units, preview choices,
  summary nutrients, and visual preferences.

## Data Model

The main local models are:

- `Food`: reusable nutrition entry with serving description, food kind, serving
  weight or volume, nutrition per serving, and optional servings per container.
- `LogEntry`: one consumed item with meal type, input mode, consumed amount, and
  calculated nutrition snapshot.
- `NutritionSnapshot`: calories, main macros, and optional nutrients.
- `Goals`: daily calorie target, macro targets, unit preferences, and display
  preferences.
- `AppState`: persisted foods, logs, and goals.

Persistence is local to the device through Android `SharedPreferences`. App data
is serialized as JSON and loaded at startup.

## Tech Stack

- Kotlin 2.0.21
- Android Gradle Plugin 8.13.2
- Gradle wrapper 8.13
- Android compile SDK 36
- Android min SDK 26
- Android target SDK 36
- Java 11 bytecode target
- AndroidX AppCompat, Core KTX, ConstraintLayout, Lifecycle, and Navigation
- Material Components 1.13.0
- JUnit 4 unit tests
- AndroidX Test and Espresso instrumentation test dependencies

## Repository Layout

```text
.
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/org/archuser/CalCount/
│       │   │   ├── data/
│       │   │   ├── domain/
│       │   │   └── ui/
│       │   └── res/
│       ├── test/
│       └── androidTest/
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
└── AGENTS.md
```

Important source areas:

- `app/src/main/java/org/archuser/CalCount/domain/NutritionCalculator.kt`:
  serving, weight, volume, and unit conversion math.
- `app/src/main/java/org/archuser/CalCount/data/CalorieRepository.kt`: local
  JSON persistence.
- `app/src/main/java/org/archuser/CalCount/ui/`: screens, dialogs, view model,
  and UI formatting.
- `app/src/test/java/org/archuser/CalCount/NutritionCalculatorTest.kt`: focused
  unit coverage for nutrition math.

## Requirements

- Android Studio with support for Android Gradle Plugin 8.13.x.
- JDK 11 or newer. Android Studio's bundled JDK is suitable.
- Android SDK Platform 36 installed for command-line builds.
- An Android emulator or physical device running Android 8.0/API 26 or newer.

This repository uses the checked-in Gradle wrapper. Use `./gradlew` rather than
a system Gradle installation.

## Getting Started

1. Clone or open the repository.
2. Open the project directory in Android Studio.
3. Let Android Studio sync Gradle dependencies from the configured repositories:
   Google Maven, Maven Central, and the Gradle Plugin Portal.
4. Select the `app` run configuration.
5. Run the app on an emulator or device.

Command-line setup validation:

```bash
./gradlew --no-daemon tasks
```

## Build

Create a debug APK:

```bash
./gradlew --no-daemon assembleDebug
```

Create a release APK:

```bash
./gradlew --no-daemon assembleRelease
```

The current release build type does not enable minification and does not define
release signing in `app/build.gradle.kts`. A locally built release APK may be
unsigned unless signing configuration is added by the build environment.

## Test

Run local JVM unit tests:

```bash
./gradlew --no-daemon test
```

Run Android instrumentation tests on a connected device or emulator:

```bash
./gradlew --no-daemon connectedAndroidTest
```

Run the existing nutrition calculator test class only:

```bash
./gradlew --no-daemon testDebugUnitTest --tests 'org.archuser.CalCount.NutritionCalculatorTest'
```

## Release Workflow Notes

The repository includes Forgejo release workflow documentation under
`.forgejo/docs/`. Before relying on any CI/CD release workflow, verify all
external dependencies at their exact resolved locations, including Gradle
distributions, Maven repositories, Forgejo actions, GitHub targets, and release
tokens.

Do not commit keystores, signing passwords, API tokens, or other secrets.
Release signing should be injected through environment variables or repository
secrets and kept least-privilege.

## Privacy and Security

- CalCount is a local-first app.
- The Android manifest declares no network permission.
- Food entries, logs, and goals are stored locally in app preferences.
- The app does not currently sync data to a server.
- Do not hardcode secrets or credentials in source files or Gradle scripts.
- Keep release signing material outside the repository.

Android backup is enabled in the manifest through the configured backup rules.
Review `app/src/main/res/xml/backup_rules.xml` and
`app/src/main/res/xml/data_extraction_rules.xml` before shipping if backup or
device-transfer behavior needs to change.

## Development Guidelines

- Keep changes small and focused.
- Match the existing Kotlin, Android Views, and XML layout style.
- Prefer simple AndroidX and Material Components patterns already used in the
  app.
- Add or update tests when changing serving math, unit conversion, persistence,
  or validation behavior.
- Preserve the rule that users choose one logging input mode at a time.
- Reject invalid nutrition inputs instead of silently correcting them.
- Avoid adding external services or dependencies unless they are necessary and
  explicitly validated.

## Current Limitations

- Data is stored locally only.
- There is no barcode scanner or remote food database.
- There is no recipe or meal planning model.
- Release signing is not configured directly in the module build file.
- Persistence uses a single JSON value in `SharedPreferences`, not a database.

## License

No license file is currently present in this repository. Add a license before
redistributing the app or accepting external contributions.
