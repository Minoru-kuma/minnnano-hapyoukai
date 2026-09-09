# AGENTS.md

## Project

This repository contains an Android application for managing piano recitals.

The primary user operates the application from an Android smartphone.

The application must work locally on the device and must not require a cloud backend.

---

## Core technology

Use the existing Android project configuration unless there is a clear reason to change it.

Primary technologies:

- Kotlin
- Jetpack Compose
- Material 3
- Room / SQLite
- Navigation Compose
- ViewModel
- Kotlin Coroutines / Flow
- Gradle Kotlin DSL

Prefer simple solutions suitable for a small MVP.

Do not introduce unnecessary frameworks.

Do not use:

- Firebase
- Supabase
- cloud databases
- Retrofit
- Ktor
- remote analytics
- remote logging
- external APIs for personal data

Do not add the Android INTERNET permission unless explicitly required.

---

## Privacy

Privacy is a core architectural requirement.

Student names, grades, teacher names, repertoire, recital information, and other personal data must remain on the Android device.

Do not transmit personal data outside the device.

Do not place real student or teacher data in:

- source code
- sample data
- tests
- screenshots
- fixtures
- documentation

Use fictional data only.

---

## Architecture

Prefer a simple architecture:

UI
→ ViewModel
→ Repository
→ DAO
→ Room

Avoid unnecessary Clean Architecture layers.

Do not introduce Hilt unless project complexity clearly justifies it.

For the MVP, prefer simple manual dependency injection.

---

## Domain model

The application manages these core concepts:

- Recital
- Section
- Performer
- Performance
- PerformanceMember
- Piece
- Composer
- ComposerAlias

### Recital

Represents one piano recital event.

A recital may contain any number of sections.

### Section

Section names are user-defined and must not be hard-coded.

Examples:

- 午前の部
- 午後の部
- 第1部
- 第2部
- 第3部

Sections have a display order.

### Performer

Represents a student or teacher.

At minimum, support:

- STUDENT
- TEACHER

Student grade may be stored when applicable.

### Performance

Represents one performance slot in a recital section.

A performance has a display order.

Student and teacher performances should share the same basic model where practical.

Teacher performances normally appear after student performances.

### PerformanceMember

One performance may have multiple performers.

This is required for cases such as teacher duets.

Do not model a performance as having only one performer.

### Piece

One performance may contain multiple pieces.

Each piece has its own display order.

---

## Composer notation

Composer identity and composer display notation must remain separate.

The same composer may appear as:

- バッハ
- J.S.バッハ
- J.S.Bach
- ヨハン・セバスティアン・バッハ

These may represent the same internal Composer.

A Piece should retain the display text selected for that specific program entry.

Do not automatically rewrite user-entered composer display notation without explicit user intent.

The design should allow future support for teacher-specific composer notation preferences, but this is not required for the MVP.

---

## UI principles

The primary user is a smartphone user and may not be comfortable with desktop-style software.

Prioritize:

- readable text
- large touch targets
- simple navigation
- low information density
- minimal required steps
- clear Japanese labels
- obvious primary actions

Avoid exposing software or database terminology in the UI.

Prefer usability over visual complexity.

---

## Agent workflow

For non-trivial tasks, first inspect the repository before modifying files.

Then:

1. identify affected areas
2. separate independent work where useful
3. delegate bounded work to subagents when available
4. avoid conflicting edits
5. integrate the results
6. perform final verification

The coordinating agent owns final architectural decisions.

---

## Suggested agent roles

When multi-agent execution is available, use roles such as:

### Database agent

Focus on:

- Room entities
- foreign keys
- indices
- DAO
- database configuration
- repository persistence logic
- migrations when required

### UI agent

Focus on:

- Compose screens
- reusable UI components
- navigation
- form behavior
- smartphone usability
- accessibility

### Domain reviewer

Review the implementation against business requirements, especially:

- variable recital sections
- multiple pieces per performance
- student vs teacher performances
- teacher duet support
- composer notation variation
- privacy requirements

Prefer review findings over unnecessary code edits.

### Test and build agent

Focus on:

- unit tests
- build verification
- lint
- regression detection

Summarize actionable failures for the coordinating agent.

---

## Parallel work rules

Parallelize independent tasks only.

Good candidates for parallel work include:

- database schema review and UI planning
- tests and documentation
- independent screen implementations

Avoid concurrent edits to shared infrastructure files such as:

- build.gradle.kts
- settings.gradle.kts
- libs.versions.toml
- AppDatabase
- central navigation configuration

Use one write owner for shared infrastructure.

---

## Implementation discipline

Before changing dependencies:

- inspect the existing Gradle configuration
- inspect libs.versions.toml if present
- preserve Android Studio generated versions where possible

Do not arbitrarily upgrade:

- Gradle
- Android Gradle Plugin
- Kotlin
- Compose

Keep changes incremental.

Prefer to keep the project buildable after each major step.

Do not rewrite working code only for stylistic preference.

Check git status before large changes.

Do not overwrite unrelated user changes.

---

## Verification

After implementation, run the relevant project checks when the environment allows it.

Typical checks are:

    ./gradlew test
    ./gradlew lint
    ./gradlew assembleDebug

On Windows, use the equivalent gradlew.bat commands if necessary.

Fix code-related failures before reporting completion.

If a failure is caused by unavailable SDKs, emulator access, local paths, or other machine-specific environment issues, report that separately.

Do not modify local machine configuration merely to hide an environment failure.

---

## Git safety

Do not:

- force push
- rewrite Git history
- delete unrelated branches
- discard unrelated user changes

Prefer small, focused changes.

---

## MVP priority

Current implementation priority:

1. Room foundation
2. Recital CRUD
3. Section CRUD
4. Performer and Performance management
5. Piece management
6. Composer and ComposerAlias handling
7. Program ordering
8. Program preview

Do not implement cloud synchronization or authentication.

The following are later-phase features unless explicitly requested:

- PDF generation
- backup and restore UI
- CSV import
- advanced drag-and-drop ordering
- teacher-specific composer display preferences
- iOS support
