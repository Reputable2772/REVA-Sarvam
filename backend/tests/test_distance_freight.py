import pytest
from backend.app.services.distance_service import DistanceService
from backend.app.services.netback_service import NetbackService
from backend.app.services.pan_india_mandi_service import PanIndiaMandiService

def test_flexible_coordinates_resolution():
    # 1. Direct GPS coordinates tuple
    gps_point = (13.2500, 77.8500)
    resolved = DistanceService.resolve_coordinates(gps_point)
    assert resolved == gps_point

    # 2. Named district in Karnataka
    belagavi = DistanceService.resolve_coordinates("Belagavi")
    assert abs(belagavi[0] - 15.8497) < 0.05

    # 3. Named APMC in Maharashtra
    vashi = DistanceService.resolve_coordinates("Vashi APMC")
    assert abs(vashi[0] - 19.0760) < 0.05

    # 4. Andhra Pradesh market
    madanapalle = DistanceService.resolve_coordinates("Madanapalle APMC")
    assert abs(madanapalle[0] - 13.5500) < 0.05

    # 5. State centroid fallback for unlisted town
    unknown_mp = DistanceService.resolve_coordinates("UnknownVillage", state="Madhya Pradesh")
    assert abs(unknown_mp[0] - 22.9734) < 0.05

def test_haversine_road_distance_calculation():
    # Road distance from Belagavi to Hubballi (~95 km direct * 1.25 ~= 100-115 km)
    dist = DistanceService.calculate_road_distance("Belagavi", "Hubballi")
    assert 90.0 <= dist <= 130.0

    # Road distance from Pune to Mumbai Vashi (~120 km direct * 1.25 ~= 140-160 km)
    dist_pune_vashi = DistanceService.calculate_road_distance("Pune", "Vashi")
    assert 120.0 <= dist_pune_vashi <= 180.0

    # Same location intra-village
    local_dist = DistanceService.calculate_road_distance((13.1000, 78.1000), (13.1000, 78.1000))
    assert local_dist == 3.0

def test_dynamic_fleet_selection_and_freight():
    # 1. 250 kg lot -> 3-Wheeler Auto
    freight_small = DistanceService.calculate_freight(distance_km=20.0, quantity_kg=250.0, haul_type="solo")
    assert freight_small["vehicle_id"] == "VEH-3W-AUTO"
    assert freight_small["total_freight"] == 150.0 + (20.0 * 11.0) # Base ₹150 + ₹220 = ₹370

    # 2. 800 kg lot -> Mini Truck / Tata Ace
    freight_med = DistanceService.calculate_freight(distance_km=30.0, quantity_kg=800.0, haul_type="solo")
    assert freight_med["vehicle_id"] == "VEH-ACE"
    assert freight_med["total_freight"] > freight_small["total_freight"]

    # 3. 1500 kg lot -> Mahindra Bolero Pickup
    freight_bolero = DistanceService.calculate_freight(distance_km=40.0, quantity_kg=1500.0, haul_type="solo")
    assert freight_bolero["vehicle_id"] == "VEH-BOLERO"

    # 4. 2500 kg lot -> Tata 407
    freight_407 = DistanceService.calculate_freight(distance_km=50.0, quantity_kg=2500.0, haul_type="solo")
    assert freight_407["vehicle_id"] == "VEH-407"

def test_solo_vs_pooled_freight_savings():
    distance_km = 35.0
    quantity_kg = 500.0

    solo = DistanceService.calculate_freight(distance_km, quantity_kg, haul_type="solo")
    pooled = DistanceService.calculate_freight(distance_km, quantity_kg, haul_type="pooled")

    # Pooled consolidation via Virtual FPO must achieve significant savings
    assert pooled["total_freight"] < solo["total_freight"]
    assert pooled["savings_vs_solo"] > 0
    assert pooled["per_kg_transport_cost"] < solo["per_kg_transport_cost"]

def test_flexible_netback_any_location():
    # Test completely flexible NetbackService with arbitrary location:
    # Farmer in Belagavi comparing Belagavi APMC vs distant Hubballi APMC
    comparison = NetbackService.calculate_comparison(
        quantity_kg=600.0,
        crop="Cabbage",
        local_rate=14.0,
        distant_rate=17.0,
        direct_rate=17.50,
        origin_location="Belagavi",
        origin_district="Belagavi",
        local_mandi_name="Belagavi APMC",
        local_mandi_location="Belagavi",
        distant_mandi_name="Hubballi APMC",
        distant_mandi_location="Hubballi"
    )

    channels = {c["id"]: c for c in comparison["channels"]}
    assert "local_mandi" in channels
    assert "distant_mandi" in channels
    assert "virtual_fpo_direct" in channels

    # Distance to distant mandi (Hubballi) must be longer than local mandi
    assert channels["distant_mandi"]["distance_km"] > channels["local_mandi"]["distance_km"]
    assert channels["distant_mandi"]["deductions"]["transport"] > channels["local_mandi"]["deductions"]["transport"]

    # Virtual FPO direct net should beat both
    assert channels["virtual_fpo_direct"]["net_earnings"] > channels["local_mandi"]["net_earnings"]

def test_pan_india_mandi_evaluation_with_gps_coords():
    # Farmer located at GPS coordinates (15.8500, 74.5000) in northern Karnataka
    results = PanIndiaMandiService.evaluate_mandi_options(
        crop="Tomato",
        quantity_kg=400.0,
        farmer_district="Belagavi",
        farmer_state="Karnataka",
        farmer_coords=(15.8500, 74.5000)
    )

    assert len(results) > 0
    first = results[0]
    assert "mandi_name" in first
    assert "distance_km" in first
    assert "transport_vehicle" in first
    assert "itemized_deductions" in first
    assert "transport_cost" in first["itemized_deductions"]
    assert first["distance_km"] > 0
    assert first["itemized_deductions"]["transport_cost"] > 0
