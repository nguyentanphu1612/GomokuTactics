# 🎯 Gomoku Tactics (Cờ Ca-Rô Chiến Thuật)

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Java-ED8B00?logo=openjdk&logoColor=white)](https://www.java.com)
[![Backend](https://img.shields.io/badge/Backend-Firebase%20Realtime%20DB-FFCA28?logo=firebase&logoColor=black)](https://firebase.google.com)
[![Database](https://img.shields.io/badge/Local%20DB-SQLite-003B57?logo=sqlite&logoColor=white)](https://www.sqlite.org)
[![Localization](https://img.shields.io/badge/i18n-English%20%7C%20Ti%E1%BA%BFng%20Vi%E1%BB%87t-blue)](#key-features)

A feature-rich native Android strategy game featuring a custom **4-tier Grandmaster AI Engine** (VCF/VCT Threat-Space Solver, Principal Variation Search, Zero-GC Zobrist Transposition Table), **real-time 1v1 online multiplayer** via Firebase, hardware-accelerated custom `Canvas` rendering for boards up to **30×30**, and full step-by-step **Match Replay**.

---

## 📸 Screenshots & Demo

> *Tip: Replace the placeholders below with actual screenshots or a short GIF of gameplay, AI hints, and board themes.*

| Home & Match Setup | Grandmaster AI Arena & Replay | Online 1v1 Lobby | Themes & Rules |
| :---: | :---: | :---: | :---: |
| ![Home](app/screenshots/home.png) | ![Gameplay](app/screenshots/replay.png) | ![Online](app/screenshots/online.png) | ![Theme](app/screenshots/theme.png) |

---

## ✨ Key Features

- **Multi-Tier AI Opponent & Tactical Hints:** Three difficulty levels (*Easy*, *Hard*, *Extreme*) plus an on-demand **Hint System** powered by the same search engine.
- **Real-Time Online Multiplayer:** Instant 6-digit room matchmaking with synchronized chess clocks, disconnect detection, and surrender handling via Firebase Realtime Database.
- **Scalable Boards & Custom Rules:** Supports **15×15**, **20×20**, and **30×30** grids with multi-touch pinch-to-zoom (`1.0x–3.5x`), pan gestures, and toggleable **"Block Two Ends"** (*Chặn 2 đầu*) tournament rules.
- **6 Curated Board Themes:** Custom-rendered themes including traditional X/O styles (*Classic Wood*, *Cyber Night*, *Jade Emerald*, *Kyoto Sakura*) and 3D-shaded stones (*Japanese Goban*, *Inferno & Frost*).
- **SQLite Match History & Replay Engine:** Automatically records every match with move coordinates, win-rate analytics, search/filter queries, and an interactive **Auto-Play / Step-by-Step Replay** viewer.
- **Bilingual & Adaptive UI:** Seamless runtime switching between **English** and **Vietnamese** (`AppCompatDelegate`), paired with full **Light / Dark (Obsidian)** Material themes.

---

## 🧠 Engineering & Architecture Highlights

### 1. 4-Tier Gomoku AI Engine (`com.example.gomokutactics.ai`)
To deliver strong tactical play within strict mobile CPU and battery budgets (`250ms`–`2600ms`), `GomokuAI` cascades through four decision layers:
1. **Pro Opening Book:** Instant center control and diagonal response heuristics for early-game tempo.
2. **Immediate Critical Move Filter:** $O(N^2)$ tactical scan for immediate 5-in-a-row wins, forced blocks, and unstoppable open-fours before spawning deep search trees.
3. **Threat-Space Search (`ThreatSpaceSolver`):**
   - **VCF (Victory by Continuous Four):** Searches forced four-threat sequences up to **16 plies** deep.
   - **VCT (Victory by Continuous Threat):** Combines fours and open-threes up to **10 plies** deep.
   - **Defensive VCF Pruning:** Detects opponent forced-win sequences up to **14 plies** ahead and restricts candidate moves to verified defensive refutations.
4. **Iterative Deepening PVS + Aspiration Windows:**
   - Uses **Principal Variation Search (PVS)** with narrow **Aspiration Windows** (`±35,000`) and Alpha-Beta pruning.
   - **Zero-GC Zobrist Transposition Table (`ZobristTT`):** Stores `262,144` entries (`2^18`, ~4.5 MB RAM) using **primitive arrays** (`long[]`, `int[]`, `byte[]`, `short[]`) with bit-packed coordinates `(row << 8) | col` to eliminate garbage collection pauses during deep searches.
   - **Move Ordering Heuristics:** Combines TT best-move priority, **Killer Heuristic** (2 slots/ply), **History Heuristic**, and pattern threat scores.
5. **Sliding-Window Pattern Evaluator (`PatternEvaluator`):** Evaluates 9-cell directional windows across 4 axes to classify contiguous and gapped threats (`X.XXX`, `XX.XX`, `.XX.X.`) as well as multi-directional forks (*Double Four*, *Four-Three*, *Double Three*).

### 2. Concurrency & Race-Condition Prevention
- **Atomic Room Matchmaking (`FirebaseRoomManager`):** Uses `DatabaseReference.runTransaction()` when creating or joining 6-digit rooms to guarantee collision-free room codes and prevent two guests from joining the same lobby simultaneously.
- **Graceful Disconnect Handling:** Registers `.onDisconnect().setValue("ABANDONED")` on active room nodes so opponents are notified immediately upon network loss or app termination.
- **Lifecycle-Safe AI Threading (`GameActivity`):** Offloads AI computation to a dedicated `ExecutorService` guarded by a monotonic `matchGeneration` counter—stale background results are safely discarded if the user restarts or exits mid-computation.
- **Resource Leak Prevention (`SoundManager`):** Reuses a single thread-safe `ToneGenerator` instance across the app lifecycle to avoid native `AudioTrack` handle exhaustion.

### 3. Custom Canvas Rendering & Touch Geometry (`GomokuBoardView`)
- **Responsive Vector Drawing:** Renders rounded boards, Hoshi star points, last-move corner brackets, pulsing hint overlays, and 3D-shaded stones (drop shadow, rim stroke, and specular highlight) directly on `Canvas`.
- **Zoom & Pan Coordinate Mapping:** Integrates `ScaleGestureDetector` and `GestureDetector` with viewport boundary clamping (`clampTranslation`) and inverse affine coordinate transformation to map screen taps accurately onto zoomed `30×30` grids.

---

## 📂 Project Structure

```text
app/src/main/java/com/example/gomokutactics/
├── activities/
│   ├── SplashActivity.java         # Animated entry point
│   ├── MainActivity.java           # Win-rate dashboard, match setup & locale switcher
│   ├── GameActivity.java           # Core game loop, chess clocks, AI & Firebase sync
│   ├── OnlineLobbyActivity.java    # 6-digit room creation & matchmaking lobby
│   ├── HistoryActivity.java        # Searchable/filterable SQLite match history
│   ├── ReplayActivity.java         # Step-by-step & auto-play match viewer
│   ├── SkinShopActivity.java       # Board theme selector & rule toggles
│   └── AboutActivity.java          # Game rules & app info
├── adapters/
│   ├── MatchHistoryAdapter.java    # RecyclerView adapter with context menu actions
│   └── SkinAdapter.java            # Theme preview & selection adapter
├── ai/
│   ├── GomokuAI.java               # Iterative Deepening PVS & search coordinator
│   ├── ThreatSpaceSolver.java      # VCF / VCT & defensive threat-space solver
│   ├── PatternEvaluator.java       # 9-cell directional pattern & fork classifier
│   └── ZobristTT.java              # 64-bit Zobrist hashing, Zero-GC TT, Killer/History
├── database/
│   └── MatchDatabaseHelper.java    # Thread-safe SQLiteOpenHelper DAO
├── models/
│   ├── BoardTheme.java             # Theme color & piece style model
│   ├── MatchRecord.java            # Serializable match history & move log model
│   └── Move.java                   # Coordinate & player tuple
├── online/
│   └── FirebaseRoomManager.java    # Firebase RTDB transactional room manager
├── utils/
│   ├── GamePrefs.java              # SharedPreferences wrapper & locale mappers
│   └── SoundManager.java           # Singleton audio & haptic feedback manager
└── views/
    └── GomokuBoardView.java        # Custom Canvas board with zoom/pan & animations
