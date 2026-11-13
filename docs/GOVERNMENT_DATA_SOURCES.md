# FarmRoute AI — Government & Public Agricultural Data Sources

## 1. Overview
FarmRoute AI integrates official agricultural market datasets to ground pricing, benchmarking, and farmer net earnings in verifiable data. All seeded baseline datasets adhere strictly to the AGMARKNET commodity schema.

---

## 2. Source Inventory & Integration Strategy

### A. AGMARKNET (Agricultural Marketing Information Network)
* **Organization**: Directorate of Marketing & Inspection (DMI), Ministry of Agriculture & Farmers Welfare, Government of India.
* **Portal**: [https://agmarknet.gov.in/](https://agmarknet.gov.in/)
* **Commodity Focus**: Tomato (Local & Hybrid varieties).
* **Target Mandis**:
  1. Kolar APMC (Karnataka) — Modal Price baseline: ₹24.00/kg
  2. Bangarpet APMC (Karnataka) — Modal Price baseline: ₹28.00/kg
  3. Mulbagal APMC (Karnataka) — Modal Price baseline: ₹22.50/kg
* **Integration Mode**:
  - Live API fetch attempted via Open Government Data Platform (`data.gov.in`).
  - Labeled `DEMO DATA` fallback verified with historical November seasonal mandi modal rates if live government portal experiences downtime.
* **Data Fields**: State, District, Market Name, Commodity, Variety, Arrival Date, Min Price, Max Price, Modal Price.

---

### B. Open Government Data Platform (data.gov.in)
* **Dataset Name**: Current Daily Price of Various Commodities from Various Markets (Mandi).
* **Resource URL**: [https://data.gov.in/catalog/current-daily-price-various-commodities-various-markets-mandi](https://data.gov.in/catalog/current-daily-price-various-commodities-various-markets-mandi)
* **API Key Format**: Registered API key passed via query parameter `api-key`.
* **Freshness & Provenance**: Every price displayed in the farmer UI explicitly displays the source (`Agmarknet` vs `DEMO DATA`) and observation date to prevent misrepresenting estimates as guaranteed prices.

---

### C. e-NAM (National Agriculture Market)
* **Organization**: Small Farmers Agribusiness Consortium (SFAC).
* **Portal**: [https://enam.gov.in/](https://enam.gov.in/)
* **Role**: Quality grading parameters (Grade A, Grade B, Grade C) and national price index comparison.

---

### D. Weather Advisory (Open-Meteo & IMD Fallback)
* **Provider**: Open-Meteo (Open-access lat/lon weather modeling) with IMD standard rainfall advisory categories.
* **Coordinating Coordinates**: Kolar District (Lat: `13.1367`, Lon: `78.1292`).
* **Advisory Rules**: Rain precipitation $> 15 \text{ mm}$ during harvest window flags an advisory chip on the lot card to warn of moisture spoilage and recommend early dispatch.
