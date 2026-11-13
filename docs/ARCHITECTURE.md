# FarmRoute AI — Technical Architecture Specification

## 1. System Overview & Core Philosophy
FarmRoute AI is a multi-tier, farmer-first agricultural marketplace and logistics coordination platform designed to eliminate unnecessary intermediaries, solve the "headline price trap", and enable smallholder farmers to access institutional bulk buyers through Virtual FPO aggregation.

### Key Architectural Tenets
1. **Deterministic Core Calculations**: The LLM (Sarvam AI / Indic LLM) is strictly restricted to multilingual conversation, speech-to-text, field extraction, and natural-language explanations. All price evaluations, match scores, net returns, and logistics allocations are calculated by deterministic backend engines.
2. **Strict Privacy Boundary**: Farmer contact numbers and exact GPS coordinates are masked from institutional buyers until mutual offer confirmation.
3. **No Overselling Guarantee**: Quantity allocations are atomic and audited with state transitions (`DECLARED` $\rightarrow$ `POOLED` $\rightarrow$ `WEIGHED` $\rightarrow$ `DELIVERED`).
4. **Honest Labeling**: Demonstration datasets are explicitly tagged as `DEMO DATA`, and payment milestones are marked as `SIMULATED`.

---

## 2. Multi-Tier Component Architecture

```
┌────────────────────────────────────────────────────────┐
│               Android Farmer Application               │
│            (Flutter / Dart - Material 3)               │
│  - Multilingual Voice UI (Kannada / English)           │
│  - Audio Recording & Sarvam TTS Playback               │
│  - Harvest Lot Management & Status Badges              │
│  - Netback Comparison Cards                            │
│  - Virtual FPO Offer Acceptance                        │
└───────────────────────────┬────────────────────────────┘
                            │ REST APIs / JSON
                            ▼
┌────────────────────────────────────────────────────────┐
│                 Unified Backend Services               │
│               (Express / Node / TypeScript)            │
│  ├── Marketplace Router (/api/marketplace)             │
│  │   └── Buyer requirement matching engine             │
│  ├── Netback Router (/api/netback)                     │
│  │   └── 4-way comparative net revenue engine          │
│  ├── Virtual FPO Router (/api/fpo)                     │
│  │   └── Multi-farmer pooling & payout allocator       │
│  ├── Logistics Router (/api/logistics)                 │
│  │   └── Fleet matching (Tata Ace/Bolero/407)          │
│  │   └── Road curvature pickup sequence generator      │
│  │   └── Proportional shared freight allocator         │
│  └── Sarvam AI Service Layer                           │
│      ├── Saaras STT (Speech to Text)                   │
│      ├── Chat Completion (Structured Extraction)       │
│      └── Bulbul TTS (Text to Speech Audio)             │
└───────────────────────────┬────────────────────────────┘
                            │ REST APIs / JSON
                            ▼
┌────────────────────────────────────────────────────────┐
│                  Buyer Web Dashboard                   │
│               (React + TypeScript + Tailwind)          │
│  - Bulk Requirement Creator                            │
│  - Aggregated Farmer Supply Proposal Inspector         │
│  - Delivery-App Lifecycle Tracking                     │
│  - Quality Inspection & Weighment Grade Split          │
└────────────────────────────────────────────────────────┘
```

---

## 3. Mathematical Models & Algorithms

### 3.1 Netback Engine
$$\text{Net Revenue} = (\text{Accepted Qty} \times \text{Price}) - (\text{Transport} + \text{Loading} + \text{Commission} + \text{APMC Fee} + \text{Rejection Loss})$$

### 3.2 Road Curvature Distance Model
Given coordinates $(\text{lat}_1, \text{lon}_1)$ and $(\text{lat}_2, \text{lon}_2)$, the straight-line Haversine distance $d$ is multiplied by an empirical rural road tortuosity factor:
$$D_{\text{road}} = 1.25 \times d_{\text{haversine}}$$

### 3.3 Proportional Shared Freight Allocation
For a pooled order with $N$ farmers with quantities $q_1, q_2, \dots, q_N$, where $Q_{\text{total}} = \sum q_i$:
$$\text{Freight Share}_i = \left(\frac{q_i}{Q_{\text{total}}}\right) \times C_{\text{total freight}}$$
Where:
$$C_{\text{total freight}} = C_{\text{base}} + (D_{\text{total route}} \times R_{\text{per km}}) + C_{\text{handling}}$$

---

## 4. Hardware Test Harness Configuration
- **Connected Host**: Agent C
- **Device Model**: CMF / Nothing Phone (Model A015)
- **OS Version**: Android 16 (API 36)
- **Serial**: `00116646X000648`
- **Verification Tools**: `adb`, `android layout`, `android screen`
