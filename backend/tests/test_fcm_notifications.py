import pytest
from fastapi.testclient import TestClient
from backend.app.main import app
from backend.app.services.fcm_service import FCMService

client = TestClient(app)

def test_fcm_status_endpoint():
    res = client.get("/api/notifications/fcm-status")
    assert res.status_code == 200
    data = res.json()
    assert data["success"] is True
    assert "is_live_fcm" in data
    assert "mode" in data
    assert data["channel_id"] == "farmroute_alerts"

def test_fcm_token_registration_and_dispatch():
    # 1. Register a device token for farmer
    device_id = "test-android-device-999"
    farmer_id = "FR-KLR-TEST-FCM-01"
    token = "fcm_mock_token_abc123xyz"

    reg_res = client.post("/api/notifications/register-token", json={
        "device_id": device_id,
        "user_id": farmer_id,
        "fcm_token": token,
        "platform": "android"
    })
    assert reg_res.status_code == 200
    reg_data = reg_res.json()
    assert reg_data["success"] is True

    # 2. Send push alert
    send_res = client.post("/api/notifications/send-alert", json={
        "recipient_id": farmer_id,
        "title": "Severe Rain Alert",
        "body": "Heavy rain forecast for Mulbagal tomorrow. Reschedule recommended.",
        "notification_type": "weather_alert",
        "metadata": {"crop": "Tomato", "lot_id": "FR-KLR-20261114-0001"}
    })
    assert send_res.status_code == 200
    send_data = send_res.json()
    assert send_data["success"] is True
    assert send_data["dispatched_to_devices"] >= 1
    assert send_data["mode"] in ["FCM Live Dispatch", "FCM Simulated Broadcast"]

    # 3. Check inbox retrieval
    inbox_res = client.get(f"/api/notifications/inbox/{farmer_id}")
    assert inbox_res.status_code == 200
    inbox_data = inbox_res.json()
    assert inbox_data["success"] is True
    assert inbox_data["count"] >= 1
    latest = inbox_data["notifications"][0]
    assert latest["title"] == "Severe Rain Alert"
    assert latest["metadata"]["crop"] == "Tomato"

def test_fcm_service_mock_dispatch(monkeypatch):
    monkeypatch.setattr(FCMService, "get_firebase_app", classmethod(lambda cls: None))
    res = FCMService.send_push_notification(
        tokens=["token_1", "token_2"],
        title="Test Title",
        body="Test Body",
        notification_type="pooling_proposal"
    )
    assert res["success"] is True
    assert res["mode"] == "FCM Simulated Broadcast"
    assert res["dispatched_count"] == 2
