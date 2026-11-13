import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.allocation_service import AllocationService
from backend.app.services.netback_service import NetbackService
from backend.app.services.dataset_importer import DatasetImporter
from backend.app.database import SessionLocal

client = TestClient(app)

def test_allocation_largest_remainder():
    lots = [
        {"farmer_id": f"FARMER-0{i}", "lot_id": f"LOT-0{i}", "quantity_kg": 200.0}
        for i in range(1, 7)
    ]
    # Total declared = 1,200kg. Target = 800kg
    allocations = AllocationService.allocate_target(lots, target_kg=800.0)
    assert len(allocations) == 6

    total_allocated = sum(a["allocated_kg"] for a in allocations)
    # Must sum to exactly 800.00kg
    assert total_allocated == 800.0
    for a in allocations:
        assert a["allocated_kg"] in [133.33, 133.34]

def test_netback_bangarpet_trap():
    comparison = NetbackService.calculate_comparison(
        quantity_kg=100.0,
        crop="Tomato",
        kolar_rate=24.0,
        bangarpet_rate=28.0,
        direct_rate=28.50,
        shared_freight_cost=549.0
    )
    channels = {c["id"]: c for c in comparison["channels"]}

    kolar_net = channels["local_mandi"]["net_earnings"]
    bangarpet_net = channels["distant_mandi"]["net_earnings"]
    direct_net = channels["virtual_fpo_direct"]["net_earnings"]

    # Bangarpet headline price is higher (28 > 24)
    assert channels["distant_mandi"]["headline_rate_per_kg"] > channels["local_mandi"]["headline_rate_per_kg"]
    # BUT net return is LOWER due to transport & deductions
    assert bangarpet_net < kolar_net
    assert comparison["headline_trap_detected"] is True

    # Virtual FPO direct net is significantly higher than both
    assert direct_net > kolar_net
    assert direct_net > bangarpet_net
    assert comparison["net_advantage_amount"] > 0

def test_health_check_endpoint():
    res = client.get("/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "healthy"
    assert "FastAPI" in data["service"]

def test_demo_reset_and_checkpoints():
    # 1. Reset
    res = client.post("/api/demo/reset")
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["total_declared_kg"] == 1200.0

    # 2. Checkpoint 1
    cp1 = client.post("/api/demo/checkpoint/1").json()
    assert cp1["checkpoint"] == 1
    assert cp1["total_declared_kg"] == 1200.0

    # 3. Checkpoint 2 (800kg FPO allocation)
    cp2 = client.post("/api/demo/checkpoint/2").json()
    assert cp2["checkpoint"] == 2
    assert cp2["total_allocated_kg"] == 800.0

    # 4. Checkpoint 3 (Mutual acceptance & Tata Ace dispatch)
    cp3 = client.post("/api/demo/checkpoint/3").json()
    assert cp3["checkpoint"] == 3
    assert "Tata Ace" in cp3["vehicle"]

    # 5. Checkpoint 4 (Operator weighment & simulated escrow)
    cp4 = client.post("/api/demo/checkpoint/4").json()
    assert cp4["checkpoint"] == 4
    assert cp4["status"] == "SETTLED"
    assert cp4["simulated"] is True

def test_flexible_dataset_importer():
    sample_csv = """Market,Commodity,Variety,Grade,Arrival_Date,Arrivals,Min_Price,Max_Price,Modal_Price
Kolar,Tomato,Hybrid,FAQ,2026-11-13,120,2000,2800,2400
Bangarpet,Tomato,Hybrid,FAQ,2026-11-13,45,2400,3200,2800
Malur,Tomato,Hybrid,FAQ,2026-11-13,60,1800,2600,2200
"""
    res = client.post(
        "/api/data/import/mandi-prices-csv",
        content=sample_csv,
        headers={"Content-Type": "text/plain"}
    )
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["total_processed"] == 3

    # Query summary
    summary = client.get("/api/data/summary").json()
    assert summary["success"] is True
    assert summary["summary"]["total_mandi_price_records"] >= 3
    assert "Tomato" in summary["summary"]["commodities_covered"]

def test_logistics_quote_and_route():
    # 600kg -> Tata Ace
    q1 = client.post("/api/logistics/quote", json={"weight_kg": 600.0}).json()
    assert q1["success"] is True
    assert q1["recommended_vehicle"]["id"] == "VEH-ACE"

    # 1200kg -> Bolero
    q2 = client.post("/api/logistics/quote", json={"weight_kg": 1200.0}).json()
    assert q2["success"] is True
    assert q2["recommended_vehicle"]["id"] == "VEH-BOLERO"

    # Multi-village route plan
    route = client.post("/api/logistics/route-plan", json={
        "villages": ["Mulbagal", "Devarayasamudra", "Bangarpet"],
        "destination": "Bengaluru DC, Whitefield",
        "total_payload_kg": 800.0
    }).json()
    assert route["success"] is True
    assert route["total_route_distance_km"] > 50
    assert route["shared_cost_per_farmer"] < 3292.0
