import pytest
import uuid
import time
from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

def test_mobile_mpin_auth_flow():
    test_phone = f"98{int(time.time()) % 100000000:08d}"
    # 1. Check account for new phone
    check_new = client.post("/api/auth/check-account", json={"phone": test_phone})
    assert check_new.status_code == 200
    assert check_new.json()["exists"] is False

    # 2. Register new farmer with AGMARKNET verified location
    reg = client.post("/api/auth/register", json={
        "phone": test_phone,
        "mpin": "4321",
        "name": "Kolar Test Farmer",
        "state": "Karnataka",
        "district": "Kolar",
        "pincode": "563101"
    })
    assert reg.status_code == 200
    reg_data = reg.json()
    assert reg_data["status"] == "registered"
    assert "token" in reg_data

    # 3. Login with MPIN
    login = client.post("/api/auth/login", json={"phone": test_phone, "mpin": "4321"})
    assert login.status_code == 200
    assert login.json()["status"] == "authenticated"

def test_weather_wttr_in_service():
    res = client.get("/api/weather/Kolar")
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["location"] == "Kolar"
    assert "temperature_c" in data
    assert "humidity_pct" in data
    assert "harvest_advisory" in data
    assert data["weather_risk_level"] in ["LOW", "MODERATE", "HIGH"]
    assert len(data["forecast_3day"]) >= 3

def test_local_agmarknet_dataset_loading():
    res = client.post("/api/data/load-local-agmarknet")
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    # Query mandi prices to verify they are stored
    prices_res = client.get("/api/data/mandi-prices?commodity=Tomato")
    assert prices_res.status_code == 200
    prices_data = prices_res.json()
    assert prices_data["success"] is True

def test_cold_storage_discovery_and_booking():
    # 1. List facilities
    res = client.get("/api/cold-storage/facilities?district=Kolar")
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["count"] >= 3
    first_facility = data["facilities"][0]
    fac_id = first_facility["facility_id"]
    avail_tonnes_before = first_facility["available_capacity_tonnes"]

    # 2. Book storage for 500kg (0.5 tonnes)
    book_res = client.post("/api/cold-storage/book", json={
        "farmer_id": "FR-KLR-00251",
        "facility_id": fac_id,
        "crop": "Tomato",
        "quantity_kg": 500.0,
        "duration_days": 5
    })
    assert book_res.status_code == 200
    book_data = book_res.json()
    assert book_data["success"] is True
    assert book_data["booking_id"].startswith("CS-BOOK-")
    assert book_data["remaining_facility_capacity_tonnes"] == round(avail_tonnes_before - 0.5, 2)

    # 3. Query bookings
    query_res = client.get("/api/cold-storage/bookings/FR-KLR-00251")
    assert query_res.status_code == 200
    assert len(query_res.json()["bookings"]) >= 1

def test_offline_batch_sync_idempotency():
    u_prefix = str(uuid.uuid4())[:8]
    batch_req = {
        "device_id": f"NOTHING-PHONE-1-TEST-{u_prefix}",
        "lots": [
            {
                "client_uuid": f"offline-uuid-{u_prefix}-111",
                "farmer_id": f"FR-KLR-OFFLINE-{u_prefix}",
                "crop": "Tomato",
                "variety": "Hybrid Red",
                "quantity_kg": 450.0,
                "quality_grade": "Grade A"
            },
            {
                "client_uuid": f"offline-uuid-{u_prefix}-222",
                "farmer_id": f"FR-KLR-OFFLINE-{u_prefix}",
                "crop": "Tomato",
                "variety": "Hybrid Red",
                "quantity_kg": 300.0,
                "quality_grade": "Grade B"
            }
        ]
    }

    # 1. First sync -> 2 newly synced
    res1 = client.post("/api/sync/harvest-batch", json=batch_req)
    assert res1.status_code == 200
    data1 = res1.json()
    assert data1["success"] is True
    assert data1["newly_synced"] == 2
    assert data1["skipped_duplicates"] == 0

    # 2. Second sync with same client_uuid -> 2 skipped duplicates (idempotency enforced)
    res2 = client.post("/api/sync/harvest-batch", json=batch_req)
    assert res2.status_code == 200
    data2 = res2.json()
    assert data2["success"] is True
    assert data2["newly_synced"] == 0
    assert data2["skipped_duplicates"] == 2

def test_push_notifications_flow():
    # 1. Register device token
    reg_res = client.post("/api/notifications/register-token", json={
        "device_id": "DEV-NOTHING-01",
        "user_id": "FR-KLR-00251",
        "fcm_token": "fcm_test_token_abc_xyz_123"
    })
    assert reg_res.status_code == 200
    assert reg_res.json()["success"] is True

    # 2. Dispatch simulated notification
    send_res = client.post("/api/notifications/send-alert", json={
        "recipient_id": "FR-KLR-00251",
        "title": "ಬೆಳೆ ಖರೀದಿ ಆಫರ್ (Offer Match)",
        "body": "800 kg Tomato Virtual FPO allocation confirmed at Rs 28.50/kg",
        "notification_type": "offer_match"
    })
    assert send_res.status_code == 200
    assert send_res.json()["dispatched_to_devices"] >= 1

    # 3. Check inbox
    inbox_res = client.get("/api/notifications/inbox/FR-KLR-00251")
    assert inbox_res.status_code == 200
    inbox_data = inbox_res.json()
    assert inbox_data["count"] >= 1
    assert inbox_data["notifications"][0]["title"] == "ಬೆಳೆ ಖರೀದಿ ಆಫರ್ (Offer Match)"
