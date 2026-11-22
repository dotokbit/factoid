#### Factoid — a visual board of atomic facts

**Factoid** is a minimalist local Android app for quickly capturing and visually structuring short thesis-style notes (facts).

The app is designed with a focus on reducing working-memory load and cognitive offloading (including for people with ADHD, autism, and AuDHD): no complex nested folders, endless canvases, registration, or cloud synchronization — just instant launch and a clear visual "matryoshka" structure.

#### Screenshots

*To add screenshots, create a `screenshots` folder in the project root, place two files there (board.png and notebook.png), and uncomment the lines below:*

#### Key features and UX decisions

* **The Nested structure principle:** a clear visual hierarchy **Board -> Group -> Notebook (1:1) -> Fact (1:1)** without deep nesting.
* **Atomicity and overload protection:** notes are limited to 200 characters, displayed as an even grid of squares, 3 per row, with internal scrolling and no unnecessary visual noise (dates, tags, formatting).
* **Quick entry in two taps:** a floating + button lets you instantly record a thought into the **Inbox** of the current board, into an existing notebook, or immediately create a new one.
* **Flexible space organization:**
  * Multiple isolated **Boards** (e.g., *Work*, *Home*, *Personal*).
  * Visual **Groups**-frames for combining notebooks by meaning.
  * Sorting of elements on the board **alphabetically** or **by creation date**.
  * Full-text search across active facts of the current board and a global **Trash** with restore capability.
* **Itten color system:** 12 soft pastel shades with automatic calculation of contrasting text color (WCAG) for comfortable perception.
* **100% Offline & Privacy First:** the app works fully locally without the internet or accounts. Full export and import of the database in JSON is implemented via the **Android Storage Access Framework (SAF)** with data integrity validation before overwriting.

#### Technology stack and architecture

The app is built on the **MVVM** architecture with unidirectional data flow (**UDF**):

* **Language:** Kotlin 2.2+
* **UI:** Jetpack Compose, Material Design 3
* **Navigation:** Jetpack Navigation Compose
* **Local database:** Room (SQLite) with transactions and ForeignKey relationships (CASCADE)
* **Asynchrony and reactivity:** Kotlin Coroutines, StateFlow / Flow
* **Serialization and backups:** Kotlinx Serialization (JSON), Android SAF (CreateDocument / OpenDocument)
* **Minimum Android version:** API 26 (Android 8.0+)

#### Project structure

```text
com.example.factoid
├── data
│   ├── backup          # Models and JSON validation/export/import manager (SAF)
│   ├── local           # AppDatabase and DatabaseSnapshot
│   │   ├── dao         # DAO interfaces for Board, Group, Notebook, Factoid, and Backup
│   │   └── entity      # Room entities with indices and cascade deletion
│   └── repository      # FactoidRepository (single source of truth and business operations)
└── ui
    ├── components      # Reusable dialogs, color pickers, and QuickAddSheet
    ├── navigation      # AppNavigation graph
    ├── screens         # Screens: Board, Notebook, Inbox, Search, Trash, Settings
    ├── theme           # App theme and ColorPalette
    └── MainViewModel.kt # UI state management via StateFlow

```


#### Build and run

1. Clone the repository:

bash

git clone https://github.com/dotokbit/factoid.git

Use code with caution.

Copy code to clipboard.
2. Open the project folder in **Android Studio** (Ladybug / Meerkat version or newer with AGP 9.0+ support).
3. Wait for Gradle synchronization to complete and run the app configuration on an emulator or device (API 26+).

#### License and copyright

Copyright © 2026. All Rights Reserved.
The source code is published for informational purposes and for portfolio demonstration. Copying, modification, and distribution of the code without the author's written permission are prohibited.
