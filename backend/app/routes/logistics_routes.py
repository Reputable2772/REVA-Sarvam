import math
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field
from typing import List, Dict, Any, Optional

router = APIRouter(prefix="/logistics", tags=["Logistics Intelligence & Shared Freight"])

VILLAGE_COORDINATES = {
    "Mulbagal": (13.1645, 78.3957),
    "Devarayasamudra": (13.1250, 78.2917),
    "Bangarpet": (12.9833, 78.2000),
    "Srinivaspur": (13.3333, 78.2167),
    "Malur": (13.0033, 77.9400),
    "Kolar Central Hub": (13.1378, 78.1294),
    "Bengaluru DC, Whitefield": (12.9698, 77.7500)
}

FLEET_SPECS = [
    {
        "id": "VEH-ACE",
        "name": "Tata Ace (Chhota Hathi)",
        "model": "Tata Ace Gold Petrol/CNG",
        "max_payload_kg": 1000,
        "base_charge": 500.0,
        "per_km_rate": 18.0,
        "handling_cost": 250.0,
        "description": "Nimble rural aggregation vehicle for smallholder lots up to 1.0 Ton"
    },
    {
        "id": "VEH-BOLERO",
        "name": "Mahindra Bolero Maxi Truck",
        "model": "Bolero Maxi Truck Plus",
        "max_payload_kg": 1500,
        "base_charge": 750.0,
        "per_km_rate": 22.0,
        "handling_cost": 350.0,
        "description": "Heavy rural pickup capacity for clusters up to 1.5 Tons"
    },
    {
        "id": "VEH-407",
        "name": "Tata 407 Light Truck",
        "model": "Tata 407 SFC 29 WB",
        "max_payload_kg": 2500,
        "base_charge": 1200.0,
        "per_km_rate": 28.0,
        "handling_cost": 500.0,
        "description": "Sub-regional corridor consolidator for clusters up to 2.5 Tons"
    }
]

def calculate_haversine_road_distance(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    from backend.app.services.distance_service import DistanceService
    return DistanceService.calculate_haversine_road_distance(lat1, lon1, lat2, lon2)

class QuoteRequest(BaseModel):
    weight_kg: float = Field(..., gt=0)
    distance_km: Optional[float] = None

@router.get("/fleet")
def get_fleet():
    return {"success": True, "fleet": FLEET_SPECS}

@router.post("/quote")
def quote_freight(req: QuoteRequest):
    w = req.weight_kg
    if w > 2500:
        raise HTTPException(status_code=400, detail=f"Total load ({w} kg) exceeds maximum single-vehicle fleet capacity of 2500 kg. Multi-truck split required.")

    if w <= 1000:
        vehicle = FLEET_SPECS[0]
    elif w <= 1500:
        vehicle = FLEET_SPECS[1]
    else:
        vehicle = FLEET_SPECS[2]

    dist = req.distance_km if req.distance_km and req.distance_km > 0 else 80.0
    total_freight = vehicle["base_charge"] + (dist * vehicle["per_km_rate"]) + vehicle["handling_cost"]
    freight_per_kg = round(total_freight / w, 2)

    return {
        "success": True,
        "weight_kg": w,
        "recommended_vehicle": vehicle,
        "estimated_distance_km": dist,
        "estimated_total_freight": total_freight,
        "freight_per_kg": freight_per_kg
    }

class RoutePlanRequest(BaseModel):
    villages: List[str] = Field(default=["Mulbagal", "Devarayasamudra", "Bangarpet", "Srinivaspur", "Malur"])
    destination: str = "Bengaluru DC, Whitefield"
    total_payload_kg: float = 800.0

@router.post("/route-plan")
def plan_route(req: RoutePlanRequest):
    if not req.villages:
        raise HTTPException(status_code=400, detail="At least one village required in route plan.")

    from backend.app.services.distance_service import DistanceService
    dest_coords = VILLAGE_COORDINATES.get(req.destination) or DistanceService.resolve_coordinates(req.destination)
    
    waypoints = []
    total_km = 0.0
    prev_coords = None

    for v in req.villages:
        coords = VILLAGE_COORDINATES.get(v) or DistanceService.resolve_coordinates(v)
        if prev_coords is None:
            leg_km = 0.0
        else:
            leg_km = calculate_haversine_road_distance(prev_coords[0], prev_coords[1], coords[0], coords[1])
            total_km += leg_km

        waypoints.append({
            "stop_name": v,
            "coordinates": {"lat": coords[0], "lon": coords[1]},
            "leg_distance_km": leg_km,
            "cumulative_distance_km": round(total_km, 1)
        })
        prev_coords = coords

    # Final leg to destination DC
    final_leg = calculate_haversine_road_distance(prev_coords[0], prev_coords[1], dest_coords[0], dest_coords[1])
    total_km += final_leg
    waypoints.append({
        "stop_name": req.destination,
        "coordinates": {"lat": dest_coords[0], "lon": dest_coords[1]},
        "leg_distance_km": final_leg,
        "cumulative_distance_km": round(total_km, 1)
    })

    # Pick vehicle
    w = req.total_payload_kg
    if w <= 1000:
        vehicle = FLEET_SPECS[0]
    elif w <= 1500:
        vehicle = FLEET_SPECS[1]
    else:
        vehicle = FLEET_SPECS[2]

    total_freight = vehicle["base_charge"] + (total_km * vehicle["per_km_rate"]) + vehicle["handling_cost"]
    num_stops = len(req.villages)
    shared_per_farmer = round(total_freight / max(num_stops, 1), 2)

    return {
        "success": True,
        "destination": req.destination,
        "total_route_distance_km": round(total_km, 1),
        "road_curvature_factor": 1.25,
        "vehicle": vehicle,
        "total_freight_cost": round(total_freight, 2),
        "shared_cost_per_farmer": shared_per_farmer,
        "individual_transport_benchmark": 3292.0,
        "farmer_logistics_savings": round(3292.0 - shared_per_farmer, 2),
        "waypoints": waypoints
    }
