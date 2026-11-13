import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.database import SessionLocal
from backend.app.models.models import Farmer, HarvestLot

client = TestClient(app)

def test_farmer_register_with_phone_number_as_id():
    """
    Verify that passing a 10-digit phone number as farmer_id when that phone number
    already belongs to an existing farmer (e.g. FARMER-02 with phone 9845012346)
    does NOT cause a 'UNIQUE constraint failed: farmers.phone' error.
    """
    db = SessionLocal()
    # Ensure FARMER-02 exists with phone 9845012346
    f2 = db.query(Farmer).filter(Farmer.phone == "9845012346").first()
    if not f2:
        f2 = Farmer(
            farmer_id="FARMER-02",
            name="Venkatamma",
            village="Devarayasamudra",
            district="Kolar",
            phone="9845012346"
        )
        db.add(f2)
        db.commit()
    db.close()

    # Mobile app registers a harvest lot passing farmer_id as '9845012346'
    payload = {
        "farmer_id": "9845012346",
        "crop": "Tomato",
        "variety": "Hybrid Sahu",
        "quantity_kg": 350.0,
        "quality_grade": "Grade A",
        "village": "Devarayasamudra",
        "district": "Kolar"
    }

    res = client.post("/api/farmer/register", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert "lot_id" in data

    # Verify query by phone also returns the new harvest lot
    res_harvests = client.get("/api/farmer/harvests?phone=9845012346")
    assert res_harvests.status_code == 200
    h_data = res_harvests.json()
    assert h_data["success"] is True
    assert any(h["lot_id"] == data["lot_id"] for h in h_data["harvests"])

def test_sync_batch_with_phone_number_as_id():
    """
    Verify offline sync batch with phone number as farmer_id does not throw unique constraint errors.
    """
    payload = {
        "device_id": "device-test-phone-001",
        "lots": [
            {
                "client_uuid": "sync-uuid-phone-test-999",
                "farmer_id": "9845012346",
                "crop": "Tomato",
                "variety": "Hybrid",
                "quantity_kg": 200.0,
                "quality_grade": "Grade A",
                "village": "Kolar"
            }
        ]
    }
    res = client.post("/api/sync/harvest-batch", json=payload)
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
