# FarmRoute AI — Master Build Specification & Technical Blueprint
**Architecture**: Android-First Flutter Application + Python FastAPI Backend + Sarvam AI Indic Engine

---

## 1. System Overview & Core Philosophy
FarmRoute AI is a multi-tier, farmer-first agricultural marketplace and logistics coordination platform designed to eliminate unnecessary intermediaries, solve the "headline price trap", and enable smallholder farmers to access institutional bulk buyers through Virtual FPO aggregation.

### Key Architectural Tenets
1. **Deterministic Core Calculations**: The LLM (Sarvam AI / Indic LLM) is strictly restricted to multilingual conversation, speech-to-text, field extraction, and natural-language explanations. All price evaluations, match scores, net returns, proportional allocations, and logistics routing are calculated deterministically by the backend.
2. **Single Android-First Flutter Client**: A unified, responsive Material 3 mobile application supporting three distinct roles: Farmer, Buyer, and Operator.
3. **Strict Privacy Boundary**: Farmer contact numbers and exact GPS coordinates are masked from institutional buyers until mutual offer confirmation.
4. **No Overselling Guarantee**: Quantity allocations are atomic and audited with strict state transitions (`DECLARED` → `POOLED` → `WEIGHED` → `DELIVERED`).
5. **Honest Labeling**: Demonstration datasets are explicitly tagged as `DEMO DATA`, and payment milestones are marked as `SIMULATED`.

---

## 2. Updated Technology Stack

### Frontend Architecture (Mobile Client)
- **Framework**: Flutter 3.x (Dart 3.x)
- **UI System**: Material 3 (Material Design with dynamic theming)
- **State Management**: Riverpod (code-generation and StateNotifier providers)
- **Networking**: Dio (HTTP client with interceptors, timeout handling, and multipart upload)
- **Navigation**: GoRouter (declarative routing with role-based redirects)
- **Audio Recording**: `record` package (16kHz mono WAV capture)
- **Audio Playback**: `just_audio` (low-latency streaming and local playback)
- **Secure Storage**: `flutter_secure_storage` (encrypted shared preferences for auth tokens)
- **Localization**: `flutter_localizations` with ARB files (`app_en.arb`, `app_kn.arb`)
- **Typography**: Noto Sans Kannada (bundled asset for offline Kannada text rendering)
- **Platform**: Android-first (API 24 to API 36 / Android 16 target)

### Backend Architecture
- **Language & Runtime**: Python 3.11+
- **API Framework**: FastAPI (asynchronous endpoints with Pydantic v2 schemas)
- **Database & ORM**: SQLite (WAL mode enabled) with SQLAlchemy 2.0
- **Testing**: pytest, pytest-asyncio, httpx

### AI & Speech Infrastructure
- **Provider**: Sarvam AI via FastAPI Gateway (API keys never exposed to mobile client)
- **STT (Speech-to-Text)**: Sarvam Saaras (`saaras:v2` / `saaras:v1`)
- **Extraction**: Sarvam Chat Completions (`sarvam-m` / structured JSON extraction)
- **TTS (Text-to-Speech)**: Sarvam Bulbul (`bulbul:v1` / `kavya` Kannada voice)
- **Verification**: Real Kannada and English WAV verification tests with deterministic typed fallback

---

## 3. Flutter Application Structure

```
frontend/farmroute_farmer/
├── android/
│   ├── app/
│   │   ├── src/
│   │   │   └── main/
│   │   │       ├── AndroidManifest.xml (RECORD_AUDIO, INTERNET)
│   │   │       └── res/
│   │   │           └── xml/
│   │   │               └── network_security_config.xml (debug cleartext support)
│   │   └── build.gradle
├── assets/
│   ├── fonts/
│   │   └── NotoSansKannada-Regular.ttf
│   └── audio/
│       ├── sample_kannada_harvest.wav
│       └── sample_english_harvest.wav
├── lib/
│   ├── app/
│   │   ├── app.dart (MaterialApp.router, theme, localization delegates)
│   │   └── routes.dart (GoRouter configuration with role guards)
│   ├── core/
│   │   ├── config/ (environment, API base URL configuration: 10.0.2.2 vs LAN)
│   │   ├── constants/ (route names, storage keys, asset paths)
│   │   ├── errors/ (failure types, exception handlers)
│   │   ├── network/ (Dio client, auth interceptor, retry policy)
│   │   ├── storage/ (secure storage wrapper for tokens and role state)
│   │   └── theme/ (Material 3 colors, typography, input decorations)
│   ├── l10n/
│   │   ├── app_en.arb (English localization strings)
│   │   └── app_kn.arb (Kannada localization strings)
│   ├── shared/
│   │   ├── widgets/ (app bar, demo banner, buttons, chips, status badges)
│   │   └── models/ (shared DTOs)
│   └── features/
│       ├── authentication/ (login, session persistence, role token)
│       ├── role_selection/ (switch between Farmer, Buyer, Operator)
│       ├── farmer/
│       │   ├── presentation/
│       │   │   ├── farmer_dashboard_screen.dart
│       │   │   ├── voice_registration_screen.dart (audio record & waveform)
│       │   │   ├── manual_registration_screen.dart (typed fallback)
│       │   │   ├── netback_comparison_screen.dart (Bangarpet trap vs FPO)
│       │   │   ├── pool_offer_screen.dart (Virtual FPO acceptance)
│       │   │   └── earnings_screen.dart (actual vs expected payout)
│       │   └── providers/ (farmer_state_provider.dart, voice_provider.dart)
│       ├── buyer/
│       │   ├── presentation/
│       │   │   ├── buyer_dashboard_screen.dart
│       │   │   ├── requirement_creation_screen.dart
│       │   │   └── pool_review_screen.dart (masked farmer list & acceptance)
│       │   └── providers/ (buyer_provider.dart)
│       ├── operator/
│       │   ├── presentation/
│       │   │   ├── operator_dashboard_screen.dart
│       │   │   ├── weighment_screen.dart (digital scale entry)
│       │   │   └── grading_screen.dart (Grade A/B/C split & order generation)
│       │   └── providers/ (operator_provider.dart)
│       └── demo_controls/
│           ├── presentation/
│           │   └── demo_checkpoint_sheet.dart (Reset + Checkpoints 1-4)
│           └── providers/ (demo_provider.dart)
└── pubspec.yaml
```

---

## 4. Unchanged Business Logic & Invariants
1. **6 Seeded Farmers & 1,200 kg Tomato Baseline**:
   - Manjunath Gowda (Mulbagal, 200 kg)
   - Ramesh Reddy (Devarayasamudra, 200 kg)
   - Venkatesh Murthy (Bangarpet, 200 kg)
   - Krishnappa Gowda (Srinivaspur, 200 kg)
   - Anjanappa Reddy (Malur, 200 kg)
   - Narayanaswamy (Kolar Rural, 200 kg)
2. **Supermarket Demand & Exact Allocation**:
   - Reliance Retail demand: 800 kg Grade A.
   - Proportional allocation: Each farmer gets $\frac{200}{1200} \times 800 = 133.33 \text{ kg}$.
   - Largest-remainder rounding ensures the sum across all 6 farmers equals **exactly 800 kg** without losing or creating a single gram.
3. **Headline Price Trap Math**:
   - Bangarpet headline price: ₹28.00/kg; Transport: -₹750; Commission: -5% (₹140); Weighing: -₹30; Rejection: -10% loss; Net Return = ₹1,604/100kg.
   - Kolar APMC: ₹24.00/kg; Transport: -₹350; Commission: -6.5% (₹156); Weighing: -₹30; Rejection: -5% loss; Net Return = ₹1,756/100kg.
   - Virtual FPO Direct Buyer: ₹28.50/kg; Shared Transport: -₹549; Commission: ₹0; Net Return = ₹2,301/100kg (+36% gain over Kolar, +43% gain over Bangarpet).
4. **Physical Weighing & Grade Splitting**:
   - Operator records actual weighment (e.g. 780 kg verified out of 800 kg declared).
   - Grade Split: Grade A (Table Supermarket) @ ₹28.50; Grade B (Hotel Kitchens) @ ₹22.00; Grade C (Food Processors) @ ₹17.00.
   - Shortfall on Supermarket Grade A order is transparently marked `UNFULFILLED`; Grade B/C are never repriced as Grade A.
5. **Independent Payment State Machines**:
   - Orders maintain strict lifecycle: `PENDING` → `ESCROW_LOCKED` → `DELIVERED` → `SETTLED`.
   - Every transaction is tagged `SIMULATED` with full audit logs.
