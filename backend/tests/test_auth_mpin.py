import pytest
from fastapi.testclient import TestClient
from backend.app.main import app

client = TestClient(app)

def test_geo_states():
    """Verify official states list from AGMARKNET dataset"""
    response = client.get("/api/auth/geo/states")
    assert response.status_code == 200
    states = response.json()
    assert isinstance(states, list)
    assert len(states) > 0
    assert "Karnataka" in states

def test_geo_districts_valid_state():
    """Verify districts list for Karnataka from AGMARKNET dataset"""
    response = client.get("/api/auth/geo/districts?state=Karnataka")
    assert response.status_code == 200
    districts = response.json()
    assert isinstance(districts, list)
    assert "Kolar" in districts

def test_geo_districts_invalid_state():
    """Verify districts lookup for unknown state returns empty list"""
    response = client.get("/api/auth/geo/districts?state=Atlantis")
    assert response.status_code == 200
    assert response.json() == []

def test_check_account_unregistered():
    """Verify check-account returns exists=False for unknown mobile number"""
    response = client.post("/api/auth/check-account", json={"phone": "9999988888"})
    assert response.status_code == 200
    data = response.json()
    assert data["exists"] is False
    assert data["phone"] == "9999988888"

def test_register_invalid_location_state():
    """Reject registration with an invalid state not in AGMARKNET"""
    response = client.post("/api/auth/register", json={
        "phone": "9845088888",
        "mpin": "1234",
        "name": "Test Farmer",
        "state": "UnknownState",
        "district": "Kolar",
        "pincode": "563101"
    })
    assert response.status_code == 400
    assert "not found in official AGMARKNET records" in response.json()["detail"]

def test_register_invalid_district_in_state():
    """Reject registration with district that does not belong to the state"""
    response = client.post("/api/auth/register", json={
        "phone": "9845088888",
        "mpin": "1234",
        "name": "Test Farmer",
        "state": "Karnataka",
        "district": "Patna", # Bihar district
        "pincode": "563101"
    })
    assert response.status_code == 400
    assert "does not exist in State" in response.json()["detail"]

def test_register_invalid_pincode_format():
    """Reject registration with malformed pincode"""
    response = client.post("/api/auth/register", json={
        "phone": "9845088888",
        "mpin": "1234",
        "name": "Test Farmer",
        "state": "Karnataka",
        "district": "Kolar",
        "pincode": "056310" # Starts with 0
    })
    assert response.status_code == 400

def test_register_pincode_mismatch_state():
    """Reject registration with valid 6-digit pin from another state"""
    response = client.post("/api/auth/register", json={
        "phone": "9845088888",
        "mpin": "1234",
        "name": "Test Farmer",
        "state": "Karnataka",
        "district": "Kolar",
        "pincode": "110001" # Delhi postal code
    })
    assert response.status_code == 400
    assert "outside the postal code range for Karnataka" in response.json()["detail"]

def test_register_success():
    """Successful onboarding with valid geo-dataset verification"""
    response = client.post("/api/auth/register", json={
        "phone": "9845099999",
        "mpin": "5678",
        "name": "Shankar Gowda",
        "state": "Karnataka",
        "district": "Kolar",
        "pincode": "563101"
    })
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "registered"
    assert "token" in data
    assert data["farmer"]["name"] == "Shankar Gowda"
    assert data["farmer"]["district"] == "Kolar"
    assert data["farmer"]["state"] == "Karnataka"
    assert data["farmer"]["pincode"] == "563101"

def test_check_account_registered():
    """Verify check-account recognizes newly registered user"""
    response = client.post("/api/auth/check-account", json={"phone": "9845099999"})
    assert response.status_code == 200
    data = response.json()
    assert data["exists"] is True
    assert data["name"] == "Shankar Gowda"
    assert data["district"] == "Kolar"

def test_login_wrong_mpin():
    """Reject login with incorrect MPIN"""
    response = client.post("/api/auth/login", json={
        "phone": "9845099999",
        "mpin": "0000"
    })
    assert response.status_code == 401
    assert "Invalid MPIN" in response.json()["detail"]

def test_login_success():
    """Successful login with correct MPIN"""
    response = client.post("/api/auth/login", json={
        "phone": "9845099999",
        "mpin": "5678"
    })
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "authenticated"
    assert "token" in data
    assert data["farmer"]["name"] == "Shankar Gowda"

def test_login_unregistered_phone():
    """Reject login for non-existent account"""
    response = client.post("/api/auth/login", json={
        "phone": "9876543210",
        "mpin": "1234"
    })
    assert response.status_code == 404

def test_demo_reset_seeds_demo_chip_farmers():
    """Verify demo reset seeds farmers compatible with Android demo quick-chips"""
    reset_res = client.post("/api/demo/reset")
    assert reset_res.status_code == 200

    # Test Manjunath Gowda (9845012345, MPIN 1234)
    check_res = client.post("/api/auth/check-account", json={"phone": "9845012345"})
    assert check_res.status_code == 200
    assert check_res.json()["exists"] is True
    assert check_res.json()["name"] == "Manjunath Gowda"

    # Test login with seed MPIN 1234
    login_res = client.post("/api/auth/login", json={"phone": "9845012345", "mpin": "1234"})
    assert login_res.status_code == 200
    assert login_res.json()["status"] == "authenticated"
    assert login_res.json()["farmer"]["district"] == "Kolar"
