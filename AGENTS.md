# AGENTS.md

## Project

This repository contains an Android application for managing the preparation and program creation of a piano recital.

The primary user operates the application from an Android smartphone.

The application is designed to work locally on the device without requiring a cloud backend.

The product is NOT primarily a long-term recital-history database.

Its main purpose is:

> Centralize the work required to prepare the current piano recital, reuse information that has already been entered, and reduce fragmented work, transcription, duplicate entry, and parallel management across multiple files or tools.

---

## Product priorities

When requirements conflict, use the following priority order:

1. Reduce fragmented work and duplicate handling
2. Protect personal information
3. Preserve reasonable future extensibility

Reducing work fragmentation is more important than merely reducing keystrokes.

Information entered once should flow through later steps toward the final program without requiring unnecessary re-entry.

Avoid designs that force users to manage the same recital information separately in spreadsheets, documents, or multiple application screens.

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

Prefer simple solutions suitable for a small Android MVP.

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

Do not add the Android INTERNET permission unless explicitly required by a future approved feature.

---

## Privacy

Privacy is a core architectural requirement.

Personal information such as:

- student names
- grades
- teacher names
- teacher assignments
- repertoire
- recital participation

must remain on the Android device during normal application use.

Do not transmit personal data outside the device.

Do not place real student or teacher data in:

- source code
- sample data
- tests
- screenshots
- fixtures
- documentation

Use fictional data only.

Keep Android cloud-backup and device-transfer exclusions for application data unless there is a clearly documented reason to change them.

Do not introduce telemetry or remote crash reporting that could transmit personal information.

---

## Recital lifecycle

The active Room database manages only the CURRENT recital.

Valid states are:

- no active recital
- exactly one active recital

Do not design normal application workflows around multiple active or historical Recital records.

The expected lifecycle is:

No active recital
→ create current recital
→ select current-year participants
→ prepare sections, performances, pieces, and ordering
→ preview and finalize the program
→ save the finalized program PDF
→ optionally export a machine-readable archive
→ explicitly end the recital
→ delete recital-specific working data
→ retain reusable master data
→ begin the next recital when needed

Past completed recitals do not need to remain queryable or editable in the active Room database.

Do not implement a historical recital database unless explicitly requested in a future task.

---

## Persistent data vs recital-specific data

The data model must clearly distinguish reusable master data from current-recital working data.

### Persistent across recital years

The following are intended to remain after a recital is completed:

- Performer directory
- current performer grade
- performer type
- teacher assignment
- Composer
- ComposerAlias
- non-personal reusable application settings

### Recital-specific

The following belong to the current recital and should be removable when that recital is completed:

- Recital
- Section
- current-year participant selection
- Performance
- PerformanceMember
- Piece
- recital-specific ordering
- other recital-specific working state

Ending a recital must not unintentionally delete persistent performer or composer master data.

---

## Domain model

The core domain contains concepts such as:

- Recital
- RecitalParticipant
- Section
- Performer
- Performance
- PerformanceMember
- Piece
- Composer
- ComposerAlias

Exact implementation details may evolve, but the semantic boundaries below must be preserved.

---

## Recital

Recital represents the one currently active piano recital.

A recital may contain any number of sections.

The application must enforce the invariant that the active database contains at most one active recital.

Do not rely only on UI behavior if the invariant can reasonably be protected at the Repository or persistence boundary.

---

## Section

Section represents a user-defined part of the recital.

Section names are not fixed.

Examples:

- 午前の部
- 午後の部
- 第1部
- 第2部
- 第3部

The number of sections must not be artificially limited.

Sections have an explicit display order.

---

## Performer directory

Performer represents a reusable person in the local directory.

A performer should retain at least:

- id
- name
- type
- current grade when applicable
- assigned teacher when applicable

Supported person types include at minimum:

- STUDENT
- TEACHER

Teachers are generally expected to persist across recital years.

Students may also participate across multiple years, so names and basic directory information should not need to be entered again every year.

Do not treat equal names as proof that two Performer records represent the same person.

---

## Current grade

A student's grade represents the CURRENT grade only.

Do not store historical grade history in the active Room database unless explicitly required by a future feature.

Historical grade information should be preserved in finalized yearly outputs such as the program PDF or archival export.

When beginning a new recital year, the application should be able to suggest grade progression such as:

- 年少 → 年中
- 年中 → 年長
- 年長 → 小1
- 小1 → 小2
- 小2 → 小3
- 小3 → 小4
- 小4 → 小5
- 小5 → 小6
- 小6 → 中1
- 中1 → 中2
- 中2 → 中3
- 中3 → 高1
- 高1 → 高2
- 高2 → 高3

Suggested progression must not be silently committed.

The intended behavior is:

suggest next grades
→ show the proposed changes
→ allow manual corrections
→ user confirms
→ update current grades

Always allow manual override for real-world exceptions.

---

## Assigned teacher

A student may have an assigned teacher.

Where practical, teacher assignment should refer to a registered Performer whose type is TEACHER rather than storing an unrelated free-text duplicate.

The implementation must avoid creating inconsistent teacher relationships.

Do not require an assigned teacher for TEACHER performers.

---

## Current-year participation

Being present in the persistent Performer directory does NOT mean that the person participates in the current recital.

Current-year participation must be represented separately.

Use a recital-scoped relationship such as RecitalParticipant or an equivalent clear model.

Required behavior:

- performer directory persists across years
- current-year participation belongs to the active recital
- users select this year's participants from the existing directory
- ending the recital removes current-year participation
- performer directory records remain
- performances should not silently reference performers who are not valid participants in the active recital

Do not automatically carry every previous participant into the next recital.

---

## Performance

Performance represents one performance slot in a Section.

A performance has an explicit display order.

Do not model every performance as belonging to exactly one performer.

A performance may contain:

- one student
- one teacher
- multiple students
- multiple teachers
- a student and teacher together

Do not introduce separate SOLO / DUET models unless there is a demonstrated need.

---

## PerformanceMember

PerformanceMember connects performers to a performance.

One performance may contain multiple performers.

This is required for:

- teacher duets
- student + teacher performances
- other multi-person performances

The same performer must not be added twice to the same performance.

Performer display order within a performance should be explicit.

Removing a performer from a performance must not delete the Performer directory record.

---

## Performance ordering

For newly created performances:

- a teacher-only performance may default to the end of the section
- a performance containing at least one student may default before teacher-only performances

This is only a default insertion behavior.

Once a performance has been saved, changing its performers must not automatically change its established display order.

Manual user ordering is authoritative.

Teacher performances may be manually positioned elsewhere if the user chooses.

---

## Piece

One performance may contain multiple pieces.

Each piece has:

- its own title
- its own display order
- optional Composer identity
- program display text for the composer

An incomplete performance may temporarily have no registered pieces.

Do not silently discard incomplete work.

---

## Composer notation

Composer identity and composer display notation must remain separate.

The same composer may appear as:

- バッハ
- J.S.バッハ
- J.S.Bach
- ヨハン・セバスティアン・バッハ

These may all refer to the same Composer.

A Piece must preserve the exact composer display text selected or entered for that program entry.

Changing Composer or ComposerAlias later must not automatically rewrite existing Piece display text.

Do not automatically normalize:

- abbreviations
- spacing
- full-width / half-width forms
- punctuation
- user-entered display notation

unless the user explicitly chooses an operation that changes the displayed text.

Composer and ComposerAlias are reusable master data and should persist across recital years.

---

## Final program PDF

The finalized program PDF is a core output of the application.

It is the primary human-readable historical record of a completed recital.

Because completed recital data is not intended to remain in the active database, PDF generation must not be treated merely as an optional unrelated feature.

The program PDF should preserve the finalized visible state of the recital, including where applicable:

- recital name
- date
- venue
- sections
- performance order
- performer names
- student grades
- piece titles
- exact composer display notation
- teacher performances

The saved PDF must remain historically accurate even if persistent performer records change in a later year.

---

## Machine-readable archive

A future version of the application may support importing past recital data into a separate historical-data feature.

To preserve this possibility, the application may eventually export a machine-readable archive at recital completion.

The archive is primarily for future reconstruction, not for spreadsheet editing.

Do not assume CSV is necessarily the correct format.

A versioned structured format such as JSON or another local format may be preferable if it preserves relationships more reliably.

A future archive should contain:

- explicit schema / format version
- recital metadata
- participant snapshot data
- sections
- performances
- performance-member relationships
- pieces
- finalized ordering
- finalized grade values
- finalized composer display text
- sufficient identity information to reconstruct the recital

Archive data must represent the finalized recital as a snapshot.

Later changes to the reusable Performer or Composer directory must not alter previously exported archives.

Do not implement a historical database or historical browsing unless explicitly requested.

---

## Year-end workflow

Ending a recital is an explicit workflow.

Target flow:

prepare recital
→ preview final program
→ save finalized program PDF
→ optionally save machine-readable archive
→ confirm output success
→ explicitly end recital
→ delete recital-specific working data
→ retain Performer / Composer master data

Do not automatically delete recital data immediately after an export attempt.

Export and deletion must be separate operations.

Before deletion, clearly communicate what will be deleted and what will remain.

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

UI code should not directly manipulate DAOs.

Repository operations that modify multiple related records should use Room transactions where atomicity matters.

Examples include:

- creating a performance with members and pieces
- updating a performance program
- ending a recital and deleting recital-specific data
- applying confirmed grade progression when multiple records are updated

---

## UI principles

The primary user operates from a smartphone and may not be comfortable with desktop-style management software.

Prioritize:

- readable text
- large touch targets
- simple navigation
- low information density
- minimal navigation depth
- minimal duplicate entry
- clear Japanese labels
- obvious primary actions
- visible recovery from incomplete data

Avoid exposing database or software terminology in the UI.

Prefer usability over visual complexity.

Do not require horizontal scrolling for core workflows on a normal smartphone screen.

Use accessible labels for icon-only controls.

Do not rely on color alone to communicate:

- student vs teacher
- errors
- selection
- status

---

## Top-level UI model

The UI should reflect one active recital rather than a historical recital list.

When no recital exists, the primary action should be similar to:

- 「新しい発表会を始める」

When an active recital exists, the application should provide access to current work such as:

- 発表会情報
- 部
- 今年の参加者
- 出演者名簿
- 演奏・曲
- プログラム確認
- 年度終了

Exact navigation may evolve, but avoid unnecessary screens whose only purpose is choosing between historical recital records that should not exist in the active database.

---

## Save and editing behavior

User-entered work should not be cleared before successful persistence.

Prevent accidental double-saving while a save operation is in progress.

If an edit contains unsaved changes, warn before discarding them.

Errors shown to the user should:

- be understandable in Japanese
- preserve the current input
- avoid exposing SQL
- avoid exposing stack traces
- avoid unnecessary personal information

---

## Ordering

Sections, performances, performers within a performance, and pieces may have explicit display order where applicable.

Simple move-up / move-down controls are acceptable for the MVP.

Advanced drag-and-drop ordering is not required unless explicitly requested.

Ordering APIs should reject inconsistent child sets rather than silently producing partial ordering.

---

## Deletion semantics

Deletion behavior must respect the distinction between reusable master data and recital-specific data.

Deleting the current recital should remove recital-owned data such as:

- Sections
- current-year participation
- Performances
- PerformanceMembers
- Pieces

It must not automatically delete:

- Performers
- teacher assignments
- current grades
- Composers
- ComposerAliases

Deleting a Performance must not delete its Performer directory records.

A Performer who is still referenced by active recital data should not be silently deleted.

Use explicit validation and clear user-facing behavior.

---

## Room and schema discipline

Room schema changes must be deliberate.

When changing an existing schema:

- increment the Room database version
- create an explicit migration where required
- export the new schema
- add migration or behavior tests where appropriate

Do not use destructive migration merely to avoid implementing a correct migration.

Preserve working user data during normal upgrades.

---

## Implementation discipline

Before editing:

- inspect the existing repository
- inspect current Room entities and schema
- inspect current tests
- inspect relevant design documentation
- inspect git status

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

Do not rewrite working code merely for stylistic preference.

Do not overwrite unrelated user changes.

---

## Agent workflow

For non-trivial tasks, act as a coordinator before editing.

First:

1. inspect the repository
2. identify affected areas
3. identify shared files
4. split independent work where useful
5. delegate bounded work to subagents when available
6. prevent conflicting concurrent edits
7. integrate the results
8. verify the final repository state

The coordinating agent owns final architectural decisions.

---

## Suggested agent roles

When multi-agent execution is available, roles may include:

### Domain / data-model reviewer

Focus on:

- one-active-recital invariant
- persistent vs recital-specific data
- current-year participation
- student / teacher relationships
- current grade behavior
- teacher assignments
- multi-performer performances
- multiple pieces
- composer notation
- year-end lifecycle

Prefer review findings before changing code.

### Database agent

Focus on:

- Room entities
- foreign keys
- indices
- DAO
- database configuration
- migrations
- transactions
- repository persistence logic

### UI agent

Focus on:

- Compose screens
- navigation
- form behavior
- smartphone usability
- accessibility
- current-recital workflow
- year-end workflow

### Test / build agent

Focus on:

- unit tests
- Room tests
- migration tests
- Gradle builds
- lint
- regression detection

Summarize actionable failures for the coordinator.

---

## Parallel work rules

Parallelize independent work only.

Good candidates include:

- schema review and UI workflow review
- tests and documentation
- independent screen implementations

Avoid concurrent edits to shared infrastructure files such as:

- build.gradle.kts
- settings.gradle.kts
- libs.versions.toml
- AppDatabase
- central navigation configuration
- shared Room schema files

Use one write owner for shared infrastructure.

---

## Verification

After implementation, run the relevant project checks when the environment allows it.

Typical checks are:

    ./gradlew test
    ./gradlew lint
    ./gradlew assembleDebug

When an emulator or Android device is available, also run relevant connected tests.

On Windows, use the equivalent gradlew.bat commands where necessary.

Do not report success when the repository does not compile.

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

Before large refactors, inspect git status.

Files generated by the IDE that are unrelated to the requested implementation should not be committed without a reason.

---

## Current MVP direction

The intended MVP progresses toward a complete current-year workflow.

Core capabilities include:

1. local Room foundation
2. persistent performer directory
3. persistent composer / alias directory
4. one active recital
5. current-year participant selection
6. flexible recital sections
7. performances with one or multiple performers
8. multiple pieces per performance
9. composer display-notation handling
10. explicit ordering
11. program preview
12. finalized program PDF
13. explicit year-end cleanup

Future concerns that should not be over-engineered now include:

- historical recital database
- historical browsing
- archive import UI
- cloud synchronization
- user accounts
- multi-device synchronization
- advanced drag-and-drop ordering
- teacher-specific composer notation preferences
- iOS support

Preserve reasonable extension points, but do not build speculative systems before they are needed.