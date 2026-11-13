# FarmRoute AI — REST API Documentation

Base URL: `http://localhost:4000`

---

## 1. System Health
### `GET /health`
Returns health status, active engines, and service version.
* **Response `200 OK`**:
```json
{
  "status": "healthy",
  "service": "FarmRoute AI Unified Engine",
  "version": "1.1.0",
  "modules": ["marketplace", "netback", "virtual-fpo", "logistics"],
  "timestamp": "2026-11-13T08:15:00.000Z"
}
```

---

## 2. Marketplace & Matching (`/api/marketplace`)

### `GET /api/marketplace/buyers`
Returns pre-seeded institutional buyer requirements (Supermarkets, Processors, Hotels, Wholesalers).

### `POST /api/marketplace/buyers`
Creates a new purchase requirement from an institutional buyer.
* **Payload**:
```json
{
  "buyerName": "BigBasket Fresh",
  "buyerType": "Supermarket",
  "crop": "Tomato",
  "requiredGrade": "Grade A",
  "targetQuantityKg": 1000,
  "maxPricePerKg": 28.5,
  "destinationLocation": "Hosakote Hub"
}
```

### `GET /api/marketplace/lots`
Returns active harvest lots available across the farmer network.

### `POST /api/marketplace/match`
Evaluates a single harvest lot or an aggregated pool against active buyer requirements, generating multi-factor compatibility scores (0–100).
* **Payload**:
```json
{
  "lot": {
    "lot_id": "FR-KLR-00251",
    "crop": "Tomato",
    "quantity_kg": 100,
    "quality_grade": "Grade A",
    "village": "Mulbagal"
  }
}
```

### `POST /api/marketplace/negotiate`
Transitions an offer through the negotiation lifecycle: `LISTED` $\rightarrow$ `MATCHED` $\rightarrow$ `OFFER_SENT` $\rightarrow$ `ACCEPTED` $\rightarrow$ `FULFILLED`.

---

## 3. Netback Engine (`/api/netback`)

### `GET /api/netback/compare/:lotId`
Calculates deterministic net revenue across 4 selling routes:
1. Kolar APMC (Modal rate, standard APMC cess & commission)
2. Bangarpet APMC (Higher gross price, but eats margin with high freight)
3. Direct Buyer Solo (Prohibitive individual truck hire)
4. Virtual FPO Pooled Direct Buyer (Maximum net payout via consolidated freight)

### `POST /api/netback/calculate`
Calculates netback comparisons for an arbitrary custom lot.

---

## 4. Virtual FPO Dynamic Pooling (`/api/fpo`)

### `POST /api/fpo/pool`
Combines multiple small farmer lots into a single institutional buyer order.
* **Payload**:
```json
{
  "crop": "Tomato",
  "grade": "Grade A",
  "target_quantity_kg": 600,
  "price_per_kg": 28.5,
  "participating_lots": [
    "FR-KLR-00251",
    "FR-KLR-00252",
    "FR-KLR-00253",
    "FR-KLR-00254",
    "FR-KLR-00255",
    "FR-KLR-00256"
  ]
}
```
* **Response `200 OK`**:
Returns combined order value, proportional transport deduction, and transparent per-farmer payout shares.

---

## 5. Logistics Intelligence & Shared Freight (`/api/logistics`)

### `GET /api/logistics/vehicles`
Lists available vehicle specs (Tata Ace, Bolero Maxi Truck, Tata 407 SFC).

### `GET /api/logistics/villages`
Returns regional geographic hubs with coordinates and baseline distances.

### `POST /api/logistics/quote`
Calculates vehicle recommendation and freight quote for an arbitrary weight and distance.

### `POST /api/logistics/plan`
Generates a complete multi-stop rural collection plan:
* Recommends optimal truck based on weight.
* Generates ordered collection stops across villages.
* Splits total freight proportionally by farmer weight contribution.
* Computes individual farmer freight savings vs solo vehicle hire (>80% savings).
