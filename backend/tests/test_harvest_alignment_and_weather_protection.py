import pytest
from datetime import datetime, timezone, timedelta
from fastapi.testclient import TestClient

from backend.app.main import app
from backend.app.database import get_db, SessionLocal, Base, engine
from backend.app.models.models import Farmer, HarvestLot, PoolingOpportunity, NotificationRecord

client = TestClient(app)

@pytest.fixture(scope="module", autouse=True)
def setup_test_db():
    Base.metadata.create_all(bind=engine)
    yield

def test_opportunistic_pooling_proposal_generation():
    db = SessionLocal()
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    tomorrow_str = (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%d")

    # Clear any previous test opportunities
    db.query(PoolingOpportunity).filter(
        (PoolingOpportunity.farmer_a_lot_id == "LOT-TEST-A") | (PoolingOpportunity.farmer_b_lot_id == "LOT-TEST-B")
    ).delete()

    # 1. Farmer A (Pre-scheduled for tomorrow at Kolar APMC)
    farmer_a = db.query(Farmer).filter(Farmer.farmer_id == "FARMER-TEST-A").first()
    if not farmer_a:
        farmer_a = Farmer(
            farmer_id="FARMER-TEST-A",
            name="Ramesh Gowda",
            phone="9845011111",
            village="Mulbagal",
            district="Kolar",
            state="Karnataka",
            pincode="563131"
        )
        db.add(farmer_a)
        db.commit()

    lot_a = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-TEST-A").first()
    if not lot_a:
        lot_a = HarvestLot(
            lot_id="LOT-TEST-A",
            farmer_id="FARMER-TEST-A",
            crop="Tomato",
            variety="Hybrid Red",
            quantity_kg=500.0,
            quality_grade="Grade A",
            harvest_date=tomorrow_str,
            harvest_time="07:00 AM",
            expected_date_time=f"{tomorrow_str} 09:00 AM",
            sell_immediately=False,
            status="ready_for_sale"
        )
        db.add(lot_a)
    else:
        lot_a.harvest_date = tomorrow_str
        lot_a.status = "ready_for_sale"
    db.commit()

    # 2. Farmer B (Selling today at Kolar APMC)
    farmer_b = db.query(Farmer).filter(Farmer.farmer_id == "FARMER-TEST-B").first()
    if not farmer_b:
        farmer_b = Farmer(
            farmer_id="FARMER-TEST-B",
            name="Suresh Kumar",
            phone="9845022222",
            village="Devarayasamudra",
            district="Kolar",
            state="Karnataka",
            pincode="563131"
        )
        db.add(farmer_b)
        db.commit()

    lot_b = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-TEST-B").first()
    if not lot_b:
        lot_b = HarvestLot(
            lot_id="LOT-TEST-B",
            farmer_id="FARMER-TEST-B",
            crop="Tomato",
            variety="Hybrid Red",
            quantity_kg=300.0,
            quality_grade="Grade A",
            harvest_date=today_str,
            harvest_time="08:00 AM",
            expected_date_time=f"{today_str} 10:00 AM",
            sell_immediately=True,
            status="ready_for_sale"
        )
        db.add(lot_b)
    else:
        lot_b.harvest_date = today_str
        lot_b.status = "ready_for_sale"
    db.commit()

    db.close()

    # 3. Check Opportunity for Farmer B
    res = client.post("/api/logistics/opportunities/check", json={
        "lot_id": "LOT-TEST-B",
        "target_mandi": "Kolar APMC"
    })
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert data["opportunity_found"] is True

    opp = data["opportunity"]
    assert opp["crop"] == "Tomato"
    assert opp["target_mandi"] == "Kolar APMC"
    assert opp["status"] == "PROPOSED"
    assert opp["combined_weight_kg"] == 800.0
    assert opp["my_savings"] > 0
    assert opp["my_solo_cost"] > opp["my_pooled_cost"]
    # Phone number must be hidden before mutual consent
    assert opp["partner_phone"] is None

    # Check notification inbox for Farmer B
    notif_res = client.get("/api/notifications/inbox/FARMER-TEST-B")
    assert notif_res.status_code == 200
    b_notifs = notif_res.json()["notifications"]
    proposal_notif = next((n for n in b_notifs if n["type"] == "pooling_proposal"), None)
    assert proposal_notif is not None
    assert "Co-Transport" in proposal_notif["title"]
    assert proposal_notif["metadata"]["opportunity_id"] == opp["opportunity_id"]

def test_bilateral_consent_and_contact_sharing():
    # Fetch active opportunity for Farmer B
    res_list = client.get("/api/logistics/opportunities/farmer/FARMER-TEST-B")
    assert res_list.status_code == 200
    opps = res_list.json()["opportunities"]
    assert len(opps) > 0
    opp_id = opps[0]["opportunity_id"]

    # 1. Farmer B consents (ACCEPT)
    res_b_consent = client.post(f"/api/logistics/opportunities/{opp_id}/consent", json={
        "farmer_id": "FARMER-TEST-B",
        "consent": True
    })
    assert res_b_consent.status_code == 200
    assert res_b_consent.json()["status"] == "AWAITING_COUNTERPART"

    # 2. Farmer A consents (ACCEPT) -> Bilateral match!
    res_a_consent = client.post(f"/api/logistics/opportunities/{opp_id}/consent", json={
        "farmer_id": "FARMER-TEST-A",
        "consent": True
    })
    assert res_a_consent.status_code == 200
    data_a = res_a_consent.json()
    assert data_a["status"] == "MUTUALLY_ACCEPTED"
    assert "partner_details" in data_a
    assert data_a["partner_details"]["name"] == "Suresh Kumar"
    assert data_a["partner_details"]["phone"] == "9845022222"

    # Verify contact exchange notification arrived in Farmer A's inbox
    res_a_notif = client.get("/api/notifications/inbox/FARMER-TEST-A")
    assert res_a_notif.status_code == 200
    a_notifs = res_a_notif.json()["notifications"]
    contact_notif_a = next((n for n in a_notifs if n["type"] == "contact_exchange"), None)
    assert contact_notif_a is not None
    assert "9845022222" in contact_notif_a["body"]
    assert contact_notif_a["metadata"]["action"] == "CALL"

    # Verify contact exchange notification arrived in Farmer B's inbox
    res_b_notif = client.get("/api/notifications/inbox/FARMER-TEST-B")
    assert res_b_notif.status_code == 200
    b_notifs = res_b_notif.json()["notifications"]
    contact_notif_b = next((n for n in b_notifs if n["type"] == "contact_exchange"), None)
    assert contact_notif_b is not None
    assert "9845011111" in contact_notif_b["body"]
    assert contact_notif_b["metadata"]["action"] == "CALL"

    # Verify Farmer B's lot was rescheduled to tomorrow
    db = SessionLocal()
    lot_b = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-TEST-B").first()
    tomorrow_str = (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%d")
    assert lot_b.harvest_date == tomorrow_str
    assert lot_b.status == "pooled"
    db.close()

def test_pooling_declined_flow():
    db = SessionLocal()
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    tomorrow_str = (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%d")

    db.query(PoolingOpportunity).filter(
        (PoolingOpportunity.farmer_a_lot_id == "LOT-TEST-C") | (PoolingOpportunity.farmer_b_lot_id == "LOT-TEST-D")
    ).delete()

    # Create two farmers and lots idempotently
    f_c = db.query(Farmer).filter(Farmer.farmer_id == "FARMER-TEST-C").first()
    if not f_c:
        f_c = Farmer(farmer_id="FARMER-TEST-C", name="Channaiah", phone="9845033333", village="Kolar", district="Kolar")
        db.add(f_c)
    f_d = db.query(Farmer).filter(Farmer.farmer_id == "FARMER-TEST-D").first()
    if not f_d:
        f_d = Farmer(farmer_id="FARMER-TEST-D", name="Devendra", phone="9845044444", village="Bangarpet", district="Kolar")
        db.add(f_d)
    db.commit()

    lot_c = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-TEST-C").first()
    if not lot_c:
        lot_c = HarvestLot(
            lot_id="LOT-TEST-C", farmer_id="FARMER-TEST-C", crop="Tomato", variety="Hybrid",
            quantity_kg=400.0, harvest_date=tomorrow_str, status="ready_for_sale"
        )
        db.add(lot_c)
    lot_d = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-TEST-D").first()
    if not lot_d:
        lot_d = HarvestLot(
            lot_id="LOT-TEST-D", farmer_id="FARMER-TEST-D", crop="Tomato", variety="Hybrid",
            quantity_kg=350.0, harvest_date=today_str, status="ready_for_sale"
        )
        db.add(lot_d)
    db.commit()
    db.close()

    # Generate opportunity
    res_check = client.post("/api/logistics/opportunities/check", json={
        "lot_id": "LOT-TEST-D",
        "target_mandi": "Kolar APMC"
    })
    opp_id = res_check.json()["opportunity"]["opportunity_id"]

    # Farmer C declines
    res_decline = client.post(f"/api/logistics/opportunities/{opp_id}/consent", json={
        "farmer_id": "FARMER-TEST-C",
        "consent": False
    })
    assert res_decline.status_code == 200
    assert res_decline.json()["status"] == "DECLINED"

    # Counterpart receives decline notification
    res_d_inbox = client.get("/api/notifications/inbox/FARMER-TEST-D")
    d_notifs = res_d_inbox.json()["notifications"]
    decline_notif = next((n for n in d_notifs if n["type"] == "pooling_declined"), None)
    assert decline_notif is not None

def test_pooling_no_savings_guard():
    db = SessionLocal()
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    tomorrow_str = (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%d")

    # Farmer with massive 2400 kg lot tomorrow
    f_e = db.query(Farmer).filter((Farmer.farmer_id == "FARMER-TEST-E") | (Farmer.phone == "9845066666")).first()
    if not f_e:
        f_e = Farmer(farmer_id="FARMER-TEST-E", name="Eshwar", phone="9845066666", village="Mulbagal", district="Kolar")
        db.add(f_e)
    # Farmer with 1200 kg lot today
    f_f = db.query(Farmer).filter((Farmer.farmer_id == "FARMER-TEST-F") | (Farmer.phone == "9845077777")).first()
    if not f_f:
        f_f = Farmer(farmer_id="FARMER-TEST-F", name="Farooq", phone="9845077777", village="Kolar", district="Kolar")
        db.add(f_f)
    db.commit()

    lot_e = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-TEST-E").first()
    if not lot_e:
        lot_e = HarvestLot(
            lot_id="LOT-TEST-E", farmer_id="FARMER-TEST-E", crop="Tomato", variety="Hybrid",
            quantity_kg=2400.0, harvest_date=tomorrow_str, status="ready_for_sale"
        )
        db.add(lot_e)
    else:
        lot_e.harvest_date = tomorrow_str
        lot_e.status = "ready_for_sale"

    lot_f = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-TEST-F").first()
    if not lot_f:
        lot_f = HarvestLot(
            lot_id="LOT-TEST-F", farmer_id="FARMER-TEST-F", crop="Tomato", variety="Hybrid",
            quantity_kg=1200.0, harvest_date=today_str, status="ready_for_sale"
        )
        db.add(lot_f)
    else:
        lot_f.harvest_date = today_str
        lot_f.status = "ready_for_sale"
    db.commit()
    db.close()

    # Total weight would be 3600 kg > 2500 kg max payload -> must NOT propose pooling
    res_check = client.post("/api/logistics/opportunities/check", json={
        "lot_id": "LOT-TEST-F",
        "target_mandi": "Kolar APMC"
    })
    assert res_check.status_code == 200
    assert res_check.json()["opportunity_found"] is False


def test_weather_harvest_risk_and_reschedule():
    db = SessionLocal()
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    tomorrow_str = (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%d")

    farmer_w = db.query(Farmer).filter(Farmer.farmer_id == "FARMER-WEATHER-01").first()
    if not farmer_w:
        farmer_w = Farmer(
            farmer_id="FARMER-WEATHER-01",
            name="Manjunath",
            phone="9845055555",
            village="Mulbagal",
            district="Kolar",
            state="Karnataka",
            pincode="563131"
        )
        db.add(farmer_w)
        db.commit()

    lot_w = db.query(HarvestLot).filter(HarvestLot.lot_id == "LOT-WEATHER-01").first()
    if not lot_w:
        lot_w = HarvestLot(
            lot_id="LOT-WEATHER-01",
            farmer_id="FARMER-WEATHER-01",
            crop="Tomato",
            variety="Hybrid Red",
            quantity_kg=600.0,
            harvest_date=today_str,
            status="ready_for_sale"
        )
        db.add(lot_w)
    else:
        lot_w.harvest_date = today_str
        lot_w.status = "ready_for_sale"
    db.commit()
    db.close()

    # 1. Check harvest weather risk
    res_risk = client.get("/api/weather/harvest-risk/FARMER-WEATHER-01")
    assert res_risk.status_code == 200
    data_risk = res_risk.json()
    assert data_risk["success"] is True
    assert data_risk["count"] >= 1
    risk = data_risk["harvest_weather_risks"][0]
    assert risk["lot_id"] == "LOT-WEATHER-01"
    assert risk["recommended_reschedule_date"] == tomorrow_str
    assert "advisory_en" in risk

    # 2. Reschedule harvest to tomorrow
    res_resched = client.post("/api/farmer/lot/LOT-WEATHER-01/weather-reschedule", json={
        "new_date": tomorrow_str
    })
    assert res_resched.status_code == 200
    resched_data = res_resched.json()
    assert resched_data["success"] is True
    assert resched_data["new_harvest_date"] == tomorrow_str
    assert resched_data["status"] == "RESCHEDULED"

    # Verify confirmation notification
    res_notif = client.get("/api/notifications/inbox/FARMER-WEATHER-01")
    w_notifs = res_notif.json()["notifications"]
    confirmed_notif = next((n for n in w_notifs if n["type"] == "weather_reschedule_confirmed"), None)
    assert confirmed_notif is not None
    assert tomorrow_str in confirmed_notif["body"]
