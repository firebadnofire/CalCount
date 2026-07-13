# CalCount Dissection

This document is the "how the app actually works" guide for new contributors.
It is meant to bridge the gap between the product description and the code that
is currently in the repository.

## 1. What This App Is

CalCount is a local-first Android calorie tracker built around one core rule:
nutrition is stored per serving, then converted into consumed nutrition through
servings, weight, or volume math.

The app is intentionally simple:

- no backend
- no cloud sync
- no barcode scanning
- no online food database
- no Room database yet

The current implementation favors:

- straightforward Android Views + XML layouts
- a single shared `AppViewModel` for app state
- one repository class for persistence
- one calculator object for nutrition math
- local JSON storage in `SharedPreferences`

That makes the project easy to understand, easy to run, and fairly easy to
change without chasing behavior across many layers.

## 2. Stack And Runtime Shape

## Platform

- Kotlin 2.0.21
- Android Gradle Plugin 8.13.2
- compile SDK 36
- min SDK 26
- target SDK 36
- Java 11 target

## Android Architecture Choices

- Android Views, not Jetpack Compose
- XML layouts with ViewBinding enabled
- Jetpack Navigation for screen switching
- `LiveData` + `AndroidViewModel` for shared UI state
- Material Components / Material 3 styling
- Dynamic color support through Material You when enabled in settings

## Persistence Choice

The app stores all user data in a single serialized app-state JSON blob inside
`SharedPreferences`.

This is a deliberate simplicity tradeoff:

- very low setup cost
- no database schema to migrate yet
- easy to reason about for an early app
- acceptable for a local-first v1-sized project

The tradeoff is that there is no query layer, partial updates, or durable schema
versioning yet. If the app grows much larger, `Room` is the likely next step.

## 3. High-Level App Layout

At runtime, the app is basically:

1. `MainActivity` hosts the top app bar, bottom navigation, and navigation host.
2. A shared `AppViewModel` loads app state from disk at startup.
3. Fragments observe `uiState` and render from that shared state.
4. User actions call `AppViewModel` methods such as `saveFood`, `logFood`, or
   `updateGoals`.
5. The view model validates input, calls domain math where needed, persists the
   updated state, and republishes new UI state.

This gives the app a simple unidirectional loop:

`UI -> ViewModel -> Repository/Calculator -> New State -> UI`

## 4. Repository Map

## Root

- `AGENTS.md`
  Project-specific operating rules for contributors and agents.
- `README.md`
  Product overview, setup, and feature summary.
- `dissection.md`
  This document.

## Android module

- `app/build.gradle.kts`
  Module config, dependencies, SDK levels, ViewBinding.
- `app/src/main/AndroidManifest.xml`
  App manifest and platform-level config.

## Main source folders

- `app/src/main/java/org/archuser/CalCount/data`
  Persistence and core data models.
- `app/src/main/java/org/archuser/CalCount/domain`
  Nutrition calculation and unit conversion logic.
- `app/src/main/java/org/archuser/CalCount/ui`
  View model, fragments, adapters, and UI formatting helpers.
- `app/src/main/res/layout`
  XML screens, rows, and dialog layouts.
- `app/src/main/res/navigation`
  Navigation graph.
- `app/src/test`
  JVM unit tests for core logic.

## 5. Main Entry Point

`MainActivity` is intentionally thin.

Its responsibilities are:

- optionally apply Material You dynamic colors before `super.onCreate`
- inflate `activity_main.xml`
- wire the `MaterialToolbar`
- attach the `BottomNavigationView` to the navigation graph
- handle the special case where history detail should pop back to history before
  switching tabs
- observe the view model's transient message stream and show snackbars

This keeps the activity focused on app shell concerns rather than business
logic.

## 6. Navigation Model

The navigation graph lives in:

- `app/src/main/res/navigation/mobile_navigation.xml`

Top-level destinations:

- Today
- History
- Food Library
- Create Food
- Settings

Additional destination:

- `dayDetailFragment`

Notable design choice:

- history detail is a normal navigation destination, but it reuses the same
  general visual structure as Today by binding `fragment_today.xml` again in
  `DayDetailFragment`

That is a practical reuse choice: the history detail screen is conceptually "the
Today dashboard, but for a chosen date."

## 7. Core State Model

There are two closely related state containers:

## Persisted state

`AppState` contains:

- `foods`
- `logs`
- `goals`

This is what gets serialized to storage.

## UI state

`AppUiState` contains:

- the same core persisted collections
- `editingFood`

It also provides convenience functions for derived data:

- `todayLogs()`
- `logsForDate(...)`
- `todayNutrition()`
- `nutritionForDate(...)`
- `remainingCalories()`
- `mealsForToday()`
- `mealsForDate(...)`
- `recentFoods(...)`

Important design choice:

- derived dashboard and history values are computed in memory from stored logs,
  not stored separately

That avoids duplication and keeps the source of truth small.

## 8. Data Model Details

## `Food`

Represents a reusable food or liquid definition.

Key fields:

- `name`
- `servingDescription`
- `kind` (`FOOD` or `LIQUID`)
- `servingWeightGrams`
- `servingVolumeMilliliters`
- `nutritionPerServing`
- `servingsPerContainer`

Design note:

- foods and liquids share one model, differentiated by `FoodKind`
- solids use weight storage
- liquids use volume storage

## `LogEntry`

Represents a saved consumption event.

Key fields:

- which food was logged
- meal bucket
- input mode
- normalized consumed amount
- already calculated nutrition snapshot
- timestamp

Important choice:

- each log stores `calculatedNutrition` at save time

That means historical entries do not need to recalculate if the source food is
later edited. This is usually the correct behavior for a tracker because past
logs should remain stable.

## `Goals`

Holds both nutrition targets and display preferences.

This includes:

- daily calorie target
- optional macro and micronutrient targets
- preferred display units
- Material You toggle
- main dashboard macro selection
- macro summary selection
- whether calories should also appear in live preview when another macro is the
  main focus

Design note:

- settings are not only about goals; this model also acts as lightweight UI
  preferences storage

## `NutritionSnapshot`

This is the numeric payload used for both:

- per-serving food nutrition
- already-calculated logged nutrition
- derived daily totals

It is the app's common nutrition currency.

## 9. Domain Logic: The Most Important File

The heart of the app is:

- `app/src/main/java/org/archuser/CalCount/domain/NutritionCalculator.kt`

It owns:

- serving-based scaling
- weight-based scaling
- volume-based scaling
- grams/unit conversion
- milliliters/unit conversion
- multiplication of optional nutrient fields

This file is the clearest expression of the product rule:

- store nutrition per serving
- convert user input into a serving fraction
- scale the whole nutrition snapshot by that fraction

Key behavior:

- food logging by servings requires a positive serving count
- food logging by weight requires a positive serving weight and positive amount
- liquid logging by volume requires a positive serving volume and positive amount
- invalid zero/negative amounts throw `IllegalArgumentException`

Why this matters:

- if nutrition math changes, this is the first file to inspect
- if unit behavior changes, this is also the first file to inspect

## 10. Persistence Layer

Persistence lives in:

- `app/src/main/java/org/archuser/CalCount/data/CalorieRepository.kt`

Responsibilities:

- load serialized app state from `SharedPreferences`
- parse JSON into models
- serialize models back to JSON
- provide simple success/failure results to the view model

Important implementation details:

- all app data is stored under one preferences key: `app_state`
- writes use `commit()`, not `apply()`
- failed deserialization resets to a new empty `AppState`
- when stored data is unreadable, the app surfaces a clear recovery message

Design rationale:

- `commit()` makes persistence synchronous and explicit
- this is simpler for a small app where a write should either succeed now or
  fail visibly now

Tradeoff:

- it is not optimized for large datasets
- there is no migration/versioning framework
- a malformed stored blob resets the whole local state

## 11. Shared ViewModel

The app's main coordinator is:

- `app/src/main/java/org/archuser/CalCount/ui/AppViewModel.kt`

This class does most of the real application orchestration.

Responsibilities:

- load state on startup
- expose `uiState`
- expose transient user messages
- track which food is being edited
- validate food input
- validate log input
- validate settings input
- invoke calculator logic
- persist updates
- republish state after save

## Why one shared view model?

For the current app size, one shared activity-scoped view model keeps cross-tab
coordination simple:

- edit a food in the library, then jump to Create Food
- save a log, then immediately see Today and History update
- change settings, then have every screen render with the new display rules

This would likely be split later if the app grows substantially, but today it is
an understandable and reasonable choice.

## Validation style

The view model validates aggressively and explicitly:

- required fields must be present
- numeric fields must parse
- non-negative and positive constraints are enforced separately
- invalid input returns early and sets a visible message

This matches the product requirement to avoid silent correction.

## 12. Screen-By-Screen Breakdown

## Today

Files:

- `ui/today/TodayFragment.kt`
- `res/layout/fragment_today.xml`
- `res/layout/item_log_entry.xml`
- `res/layout/item_summary_line.xml`

Purpose:

- show today's summary
- show the main selected macro
- optionally show calories as a secondary number
- show progress/summary lines
- group entries by meal

Implementation notes:

- the screen is rendered entirely from `AppUiState`
- meal sections are built dynamically by inflating rows into containers
- empty meal placeholders are shown when no entries exist

Design choice:

- the dashboard is text-heavy and list-based, not chart-heavy
- this matches the product's practical/manual-entry focus

## History

Files:

- `ui/history/HistoryFragment.kt`
- `res/layout/fragment_history.xml`
- `res/layout/item_history_day.xml`

Purpose:

- group logs by local date
- show one row per day with summary totals
- navigate into a date detail screen

Implementation notes:

- days are derived directly from log timestamps
- the list is built dynamically rather than via `RecyclerView`

That is fine at the current app scale, though it is one of the clearer places
where `RecyclerView` may become attractive if the dataset grows.

## Day Detail

File:

- `ui/history/DayDetailFragment.kt`

Purpose:

- render a historical day using the same structure as Today

Design choice:

- this fragment intentionally duplicates some Today rendering behavior instead of
  extracting a shared presenter/helper

That keeps the code obvious, but it is also a likely future cleanup area if both
screens evolve further.

## Food Library

Files:

- `ui/library/FoodLibraryFragment.kt`
- `res/layout/fragment_food_library.xml`
- `res/layout/item_food.xml`
- `res/layout/item_recent_food_chip.xml`

Purpose:

- search saved foods
- surface recent foods
- launch logging
- launch editing

Implementation notes:

- recent foods are inferred from log history, not stored separately
- search is simple local substring matching on food name and serving description
- food cards show a compact serving line and summary nutrition line

Design choice:

- recent foods only show when the search box is empty
- this keeps the screen focused and avoids mixing search results with recents

## Log Food Dialog

File:

- `ui/library/LogFoodDialogFragment.kt`

Purpose:

- choose meal type
- choose input mode
- enter amount
- preview math before saving

Why it matters:

- this is where the product's core interaction is most visible

Important behavior:

- user chooses one mode at a time
- foods log by servings or weight, while liquids log by servings or volume
- live preview is calculated on text change
- invalid amounts are surfaced immediately

Design choice:

- logging is implemented as a dialog rather than a full screen

That fits the app's low-friction logging goal.

## Create Food

Files:

- `ui/create/CreateFoodFragment.kt`
- `res/layout/fragment_create_food.xml`

Purpose:

- create new food entries
- edit existing food entries
- collect required nutrients first
- optionally expand useful and extended nutrients

Important behavior:

- the same screen handles create and edit
- editing state comes from the shared view model
- food/liquid mode changes the serving amount label
- useful and extended nutrient sections are collapsible

Design choice:

- the form is intentionally long but explicit
- the code avoids abstraction-heavy form models and instead maps fields directly
  into `SaveFoodInput`

This is verbose, but easy to inspect and safe to change.

## Settings

Files:

- `ui/settings/SettingsFragment.kt`
- `res/layout/fragment_settings.xml`

Purpose:

- manage calorie target
- manage nutrient targets
- choose display units
- choose main macro
- choose which summary nutrients appear
- toggle Material You

Design choice:

- settings affect both behavior and presentation
- macro summary checkboxes are created programmatically from the enum rather than
  hardcoded in XML

That means adding a new summary-eligible nutrient usually starts in the enum and
formatter layer, not in a dozen duplicated UI fields.

## 13. Formatting Layer

Formatting is centralized in:

- `ui/UiFormatters.kt`

Responsibilities:

- numeric rounding
- calorie label formatting
- unit label formatting
- entry amount strings
- summary-line strings
- dashboard progress strings
- time formatting

Why this file exists:

- it keeps presentation string logic out of fragments
- it gives the project one place to change display formatting rules

Important nuance:

- calorie display intentionally rounds using nutrition-label-like behavior in
  `nutritionLabelCaloriesValue(...)`

That is a product-facing choice, not just a formatting detail.

## 14. Current Design Themes

A new contributor should understand these repeated patterns:

## 1. Favor explicitness over abstraction

Examples:

- direct field-by-field form mapping
- explicit JSON parse/serialize code
- explicit render methods in fragments

This codebase currently values readability over compact cleverness.

## 2. Keep the source of truth small

Examples:

- `AppState` is the persisted core
- dashboard totals are derived, not stored
- recent foods are derived from log history

## 3. Separate math from UI

Examples:

- `NutritionCalculator` does math
- fragments do rendering
- `UiFormatters` does text presentation
- `AppViewModel` coordinates between them

## 4. Prefer local-first behavior

Examples:

- no network permission
- no service layer
- all data is available offline

## 5. Let settings drive presentation

Examples:

- main macro choice changes dashboard emphasis
- unit preferences change displayed labels
- summary macro selection changes secondary lines
- Material You can be toggled on or off

## 15. Known Constraints And Future Pressure Points

These are not necessarily bugs, but they are architectural pressure points.

## Single shared view model

Good for:

- simplicity
- cross-screen coordination

Potential future cost:

- `AppViewModel` is already large and will keep growing if new feature areas are
  added without refactoring

## SharedPreferences JSON storage

Good for:

- speed of development
- easy local persistence

Potential future cost:

- dataset growth
- migrations
- partial update complexity
- whole-state reset risk on parse failure

## Dynamic view inflation instead of RecyclerView

Good for:

- simple screens
- low ceremony

Potential future cost:

- long history lists
- large food libraries
- less efficient view recycling

## Today / DayDetail duplication

Good for:

- clarity

Potential future cost:

- duplicated rendering logic if both screens change often

## 16. Testing Status

Current explicit unit coverage is concentrated in:

- `app/src/test/java/org/archuser/CalCount/NutritionCalculatorTest.kt`

What is covered:

- serving scaling
- weight scaling
- ounce-to-gram conversion

What is not deeply covered yet:

- repository serialization/deserialization
- view model validation paths
- food vs liquid edge cases across the full stack
- history grouping behavior
- formatter rules

If you change math or storage behavior, adding tests should be part of the
change.

## 17. Where To Make Changes

If you want to change a behavior, start here:

- nutrition math or unit conversion:
  `domain/NutritionCalculator.kt`
- saved data structure:
  `data/model/*` and `data/CalorieRepository.kt`
- save/log/settings workflows:
  `ui/AppViewModel.kt`
- dashboard presentation:
  `ui/today/TodayFragment.kt` and `ui/UiFormatters.kt`
- history presentation:
  `ui/history/*`
- food creation form:
  `ui/create/CreateFoodFragment.kt` and `fragment_create_food.xml`
- food logging flow:
  `ui/library/LogFoodDialogFragment.kt` and `dialog_log_food.xml`
- settings behavior:
  `ui/settings/SettingsFragment.kt`, `Goals.kt`, and `UiFormatters.kt`
- navigation structure:
  `res/navigation/mobile_navigation.xml`
- global shell/navigation UI:
  `MainActivity.kt` and `activity_main.xml`

## 18. Suggested Mental Model For New Contributors

If you are new to the project, read files in this order:

1. `README.md`
2. `app/src/main/java/org/archuser/CalCount/domain/NutritionCalculator.kt`
3. `app/src/main/java/org/archuser/CalCount/data/model/*`
4. `app/src/main/java/org/archuser/CalCount/data/CalorieRepository.kt`
5. `app/src/main/java/org/archuser/CalCount/ui/AppViewModel.kt`
6. `MainActivity.kt`
7. the fragment for the screen you want to change
8. the matching XML layout
9. `ui/UiFormatters.kt`

That reading order usually gives the fastest path from product idea to code
implementation.

## 19. Bottom Line

CalCount is a deliberately straightforward Android app.

Its main strength is that the code follows the product model closely:

- foods define per-serving nutrition
- logs store calculated results
- totals are derived from logs
- settings shape both targets and presentation

The architecture is not fancy, but it is coherent. For a new contributor, that
is a benefit: most behavior can be traced quickly from fragment -> view model ->
calculator/repository -> model.
