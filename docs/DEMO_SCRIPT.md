# FarmRoute AI — 3-Minute Hackathon Demonstration Script

**Project:** FarmRoute AI — Intelligent Agricultural Market Decision Support & Virtual FPO Platform  
**Target Scenario:** Kolar District Tomato Belt (Mulbagal Cluster, 6 Farmers, 600 kg Total)  
**Hardware Harness:** Physical Android 16 Device (Model A015) via ADB + Buyer Web Dashboard

---

## Act 1: The Voice Harvest Registration (0:00 – 0:45)
*Persona: Farmer Manjunath Gowda from Mulbagal, Kolar.*

1. **Farmer Voice Input (Kannada)**:
   - Farmer taps the floating microphone icon on the Android Farmer App.
   - Farmer speaks in Kannada:
     > *"ನನ್ನ ಬಳಿ 100 ಕೆಜಿ ಗ್ರೇಡ್ ಎ ಹೈಬ್ರಿಡ್ ಟೊಮೇಟೊ ಇದೆ, ನಾಳೆ ಡೆಲಿವರಿ ಮಾಡಲು ಸಿದ್ಧ."*  
     > *(English translation: "I have 100 kg of Grade A hybrid tomatoes ready for delivery tomorrow.")*
   - *Fallback:* If audio hardware/network is muted, farmer types or selects quick chips: `[Crop: Tomato] [Grade: Grade A] [Qty: 100 kg]`.

2. **Sarvam AI Saaras STT & Field Extraction**:
   - Backend processes audio through Sarvam Saaras STT $\rightarrow$ Chat Completion extracts:
     ```json
     {
       "crop": "Tomato",
       "variety": "Hybrid Red",
       "quantity_kg": 100,
       "quality_grade": "Grade A",
       "village": "Mulbagal",
       "harvest_date": "2026-11-13"
     }
     ```

3. **Confirmation & Audio Readback**:
   - App displays a high-contrast confirmation card with status: **`DECLARED`** and verification: **`self-declared`**.
   - Sarvam Bulbul TTS speaks confirmation in natural Kannada:
     > *"ನಿಮ್ಮ 100 ಕೆಜಿ ಟೊಮೇಟೊ ಬೆಳೆಯನ್ನು ಯಶಸ್ವಿಯಾಗಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ. ನಿಮ್ಮ ಲಾಟ್ ಸಂಖ್ಯೆ FR-KLR-00251."*
   - Unique identifier generated: **`FR-KLR-00251`**.

---

## Act 2: Netback Comparison & The "Headline Price Trap" (0:45 – 1:30)

1. **Viewing Route Options**:
   - Farmer opens "Compare Markets & Selling Routes" for Lot `FR-KLR-00251`.
   - Four distinct routes are calculated deterministically:
     - **Option 1: Kolar APMC**: ₹24/kg | Transport: ₹350 | Comm: 4% | Net: **₹1,756**
     - **Option 2: Bangarpet APMC**: ₹28/kg | Transport: ₹750 | Comm: 5% | Net: **₹1,604**
     - **Option 3: Solo Supermarket**: ₹29/kg | Transport: ₹1,800 | Net: **₹992** *(Prohibitive)*
     - **Option 4: Virtual FPO Pooled Supermarket**: ₹28.5/kg | Shared Transport: ₹370 | Comm: ₹0 | Net: **₹2,388**

2. **The Decision Reveal**:
   - The UI reveals the **Headline Price Trap**: Bangarpet advertises ₹28/kg vs Kolar's ₹24/kg, but eats margins with high transport and 5% commission.
   - **Virtual FPO Pooling** delivers **+₹632 extra cash per 100kg (+36% net increase)** right into the farmer's pocket.

---

## Act 3: Virtual FPO Pooling & Logistics Coordination (1:30 – 2:15)

1. **Buyer Bulk Requirement**:
   - Supermarket (Reliance Retail Whitefield Hub) requires **600 kg Grade A Tomatoes** at ₹28.50/kg.
2. **Matching & Dynamic Pooling**:
   - Matching Engine matches 6 nearby farmers in the Mulbagal corridor (100 kg each = 600 kg total).
   - Each farmer receives a discrete offer card with individual net payout transparency.
   - **Privacy Rule**: Buyer sees only aggregated statistics; personal names and phones remain masked until mutual acceptance.
3. **Logistics Engine (Task 5)**:
   - Recommended Vehicle: **Tata Ace (Chhota Hathi)** — 1,000 kg capacity (60% payload utilization).
   - Sequenced Pickup Route: *Nangali $\rightarrow$ Byrakur $\rightarrow$ Tayalur $\rightarrow$ Avani $\rightarrow$ Devarayasamudra $\rightarrow$ Mulbagal $\rightarrow$ Bengaluru DC*.
   - Proportional Shared Freight: **₹370 per farmer** vs ₹1,800 individual solo hire (>79% freight savings).

---

## Act 4: Weighing, Quality Split & Simulated Settlement (2:15 – 3:00)

1. **Collection Center Weighing**:
   - Field collection agent enters Lot ID `FR-KLR-00251`.
   - Scale records: **98 kg actual weight** (Discrepancy: -2 kg recorded transparently without fraud assumption).
   - Status updates: `DECLARED` $\rightarrow$ **`WEIGHED`**; Verification upgraded to **`weighed`**.
2. **Grade-Split Routing**:
   - Grade A (85 kg) $\rightarrow$ Supermarket (₹28.50/kg)
   - Grade B (10 kg) $\rightarrow$ Empire Hotel Bulk Kitchen (₹22.00/kg)
   - Grade C (3 kg) $\rightarrow$ Nandi Tomato Processor (₹17.00/kg)
3. **Simulated Payment State Machine**:
   - `ORDER_CONFIRMED` $\rightarrow$ `PICKUP_VERIFIED` $\rightarrow$ `ADVANCE_RELEASED (SIMULATED)` $\rightarrow$ `DELIVERED` $\rightarrow$ `FINAL_PAYMENT (SIMULATED)`.
   - Display disclaimer: *"In production, advances are funded by partner NBFCs against confirmed buyer POs."*
4. **Actual vs. Expected Final Receipt**:
   - Farmer receives itemized digital slip:
     - Estimated Net: ₹2,388 (100 kg)
     - Actual Delivered: 98 kg (Grade A/B split)
     - Final Payout: **₹2,314**
     - Variance explained: ₹74 difference due to 2 kg natural moisture loss during transit.
