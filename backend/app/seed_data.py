from typing import List, Dict, Any

SEED_FARMERS: List[Dict[str, Any]] = [
    {
        "farmer_id": "FARMER-01",
        "name": "Manjunath Gowda",
        "village": "Mulbagal",
        "district": "Kolar",
        "phone": "+91 98450 11001"
    },
    {
        "farmer_id": "FARMER-02",
        "name": "Ramesh Reddy",
        "village": "Devarayasamudra",
        "district": "Kolar",
        "phone": "+91 98450 11002"
    },
    {
        "farmer_id": "FARMER-03",
        "name": "Venkatesh Murthy",
        "village": "Bangarpet",
        "district": "Kolar",
        "phone": "+91 98450 11003"
    },
    {
        "farmer_id": "FARMER-04",
        "name": "Krishnappa Gowda",
        "village": "Srinivaspur",
        "district": "Kolar",
        "phone": "+91 98450 11004"
    },
    {
        "farmer_id": "FARMER-05",
        "name": "Anjanappa Reddy",
        "village": "Malur",
        "district": "Kolar",
        "phone": "+91 98450 11005"
    },
    {
        "farmer_id": "FARMER-06",
        "name": "Narayanaswamy",
        "village": "Kolar Rural",
        "district": "Kolar",
        "phone": "+91 98450 11006"
    }
]

# 6 Smallholder Farmers with 200kg each = 1,200kg total declared supply
SEED_HARVEST_LOTS: List[Dict[str, Any]] = [
    {
        "lot_id": "FR-KLR-20261113-0001",
        "farmer_id": "FARMER-01",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "quantity_kg": 200.0,
        "quality_grade": "Grade A",
        "harvest_date": "2026-11-13",
        "status": "ready_for_sale"
    },
    {
        "lot_id": "FR-KLR-20261113-0002",
        "farmer_id": "FARMER-02",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "quantity_kg": 200.0,
        "quality_grade": "Grade A",
        "harvest_date": "2026-11-13",
        "status": "ready_for_sale"
    },
    {
        "lot_id": "FR-KLR-20261113-0003",
        "farmer_id": "FARMER-03",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "quantity_kg": 200.0,
        "quality_grade": "Grade A",
        "harvest_date": "2026-11-13",
        "status": "ready_for_sale"
    },
    {
        "lot_id": "FR-KLR-20261113-0004",
        "farmer_id": "FARMER-04",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "quantity_kg": 200.0,
        "quality_grade": "Grade A",
        "harvest_date": "2026-11-13",
        "status": "ready_for_sale"
    },
    {
        "lot_id": "FR-KLR-20261113-0005",
        "farmer_id": "FARMER-05",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "quantity_kg": 200.0,
        "quality_grade": "Grade A",
        "harvest_date": "2026-11-13",
        "status": "ready_for_sale"
    },
    {
        "lot_id": "FR-KLR-20261113-0006",
        "farmer_id": "FARMER-06",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "quantity_kg": 200.0,
        "quality_grade": "Grade A",
        "harvest_date": "2026-11-13",
        "status": "ready_for_sale"
    }
]

SEED_BUYER_REQUIREMENTS: List[Dict[str, Any]] = [
    {
        "id": "BUYER-REQ-001",
        "buyer_name": "Reliance Retail (Bengaluru Distribution Hub)",
        "buyer_type": "Supermarket",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "required_grade": "Grade A",
        "target_quantity_kg": 800.0,
        "min_acceptable_kg": 600.0,
        "max_price_per_kg": 30.0,
        "min_price_per_kg": 26.0,
        "destination_location": "Bengaluru DC, Whitefield",
        "preferred_pickup_hub": "Kolar Central Hub",
        "payment_terms": "T+1 direct bank transfer",
        "active": True
    },
    {
        "id": "BUYER-REQ-002",
        "buyer_name": "BigBasket Fresh (Hosakote Hub)",
        "buyer_type": "Supermarket",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "required_grade": "Grade A",
        "target_quantity_kg": 1000.0,
        "min_acceptable_kg": 800.0,
        "max_price_per_kg": 28.5,
        "min_price_per_kg": 25.0,
        "destination_location": "Hosakote Sorting Center",
        "preferred_pickup_hub": "Kolar Central Hub",
        "payment_terms": "T+2 escrow clearance",
        "active": True
    },
    {
        "id": "BUYER-REQ-003",
        "buyer_name": "Nandi Food Processing Ltd",
        "buyer_type": "Processor",
        "crop": "Tomato",
        "variety": "Any",
        "required_grade": "Grade C",
        "target_quantity_kg": 2500.0,
        "min_acceptable_kg": 1500.0,
        "max_price_per_kg": 19.0,
        "min_price_per_kg": 16.0,
        "destination_location": "Malur Industrial Area",
        "preferred_pickup_hub": "Mulbagal APMC",
        "payment_terms": "Immediate upon weight check",
        "active": True
    },
    {
        "id": "BUYER-REQ-004",
        "buyer_name": "Empire Restaurant Group (Bulk Kitchen)",
        "buyer_type": "Hotel",
        "crop": "Tomato",
        "variety": "Hybrid Red",
        "required_grade": "Grade B",
        "target_quantity_kg": 400.0,
        "min_acceptable_kg": 300.0,
        "max_price_per_kg": 24.0,
        "min_price_per_kg": 21.0,
        "destination_location": "Shivajinagar Central Kitchen",
        "preferred_pickup_hub": "Kolar Central Hub",
        "payment_terms": "Cashless on delivery",
        "active": True
    }
]
