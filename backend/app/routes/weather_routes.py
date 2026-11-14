from fastapi import APIRouter, Query, Depends, HTTPException
from typing import Optional
from sqlalchemy.orm import Session
from backend.app.database import get_db
from backend.app.services.weather_service import WeatherService
from backend.app.services.weather_protection_service import WeatherProtectionService

router = APIRouter(prefix="/weather", tags=["Agri Weather Intelligence"])

@router.get("/harvest-risk/{farmer_id}")
def check_harvest_weather_risk(
    farmer_id: str,
    language_code: Optional[str] = Query("kn-IN", description="Preferred language code (e.g. kn-IN, hi-IN, te-IN, ta-IN, en-IN)"),
    db: Session = Depends(get_db)
):
    """
    Evaluates weather risk for all lots scheduled for today by the specified farmer.
    If heavy rain or adverse conditions are predicted, returns proactive rescheduling advisory.
    """
    risks = WeatherProtectionService.check_scheduled_harvest_weather_risk(
        farmer_id=farmer_id,
        db=db,
        language_code=language_code or "kn-IN"
    )
    return {
        "success": True,
        "farmer_id": farmer_id,
        "count": len(risks),
        "harvest_weather_risks": risks
    }

@router.get("/harvest-alerts")
def get_harvest_weather_alerts(
    farmer_id: Optional[str] = Query("FARMER-01"),
    crop: Optional[str] = Query("Tomato"),
    district: Optional[str] = Query(None),
    latitude: Optional[float] = Query(None),
    longitude: Optional[float] = Query(None),
    language_code: Optional[str] = Query("en-IN"),
    db: Session = Depends(get_db)
):
    """
    Returns proactive harvest weather risk alerts for a farmer and district.
    """
    from backend.app.models.models import Farmer
    from backend.app.services.distance_service import DistanceService, DISTRICT_COORDINATES

    clean_fid = str(farmer_id).strip() if isinstance(farmer_id, str) else "FARMER-01"
    clean_crop = str(crop).strip() if isinstance(crop, str) else "Produce"
    clean_dist = str(district).strip() if isinstance(district, str) and district else None
    lat_val = float(latitude) if isinstance(latitude, (int, float)) else None
    lon_val = float(longitude) if isinstance(longitude, (int, float)) else None

    farmer = None
    if clean_fid and clean_fid != "FARMER-01":
        farmer = db.query(Farmer).filter(
            (Farmer.farmer_id == clean_fid) |
            (Farmer.phone == clean_fid) |
            (Farmer.phone.endswith(clean_fid))
        ).first()

    # Determine location priority: explicit district -> farmer profile district -> reverse geocode / coords -> default
    dist = clean_dist
    if not dist and farmer and farmer.district:
        dist = farmer.district
    if not dist and lat_val is not None and lon_val is not None and not (abs(lat_val - 13.133) < 0.005 and abs(lon_val - 78.133) < 0.005):
        # Resolve nearest district from coordinates
        closest_dist = None
        min_d = 999999.0
        for d_name, coords in DISTRICT_COORDINATES.items():
            d = DistanceService.haversine_distance_km(lat_val, lon_val, coords[0], coords[1])
            if d < min_d:
                min_d = d
                closest_dist = d_name.title()
        if closest_dist and min_d < 120.0:
            dist = closest_dist
    if not dist:
        fallback_farmer = db.query(Farmer).filter(
            (Farmer.farmer_id == clean_fid) |
            (Farmer.phone == clean_fid) |
            (Farmer.phone.endswith(clean_fid))
        ).first()
        dist = fallback_farmer.district if fallback_farmer else "Local"

    w_data = WeatherService.get_weather(dist)
    risk_level = w_data.get("weather_risk_level", "LOW")
    has_alert = True

    return {
        "success": True,
        "has_active_alert": has_alert,
        "warning_id": f"IMD-{dist[:3].upper()}-ALERT",
        "district": dist,
        "alert_type": "WEATHER_ADVISORY",
        "severity": "ORANGE" if risk_level == "HIGH" else ("YELLOW" if risk_level == "MODERATE" else "ADVISORY"),
        "headline": f"Official IMD Weather Report: {w_data.get('condition', 'Partly Cloudy')} with {w_data.get('precip_mm', 0)}mm precipitation in {dist}.",
        "headline_kannada": f"ಐಎಂಡಿ ಹವಾಮಾನ ವರದಿ: {dist} ಪ್ರದೇಶದಲ್ಲಿ {w_data.get('condition', 'ಭಾಗಶಃ ಮೋಡ ಕವಿದ ವಾತಾವರಣ')}.",
        "temperature_c": w_data.get("temperature_c", 26),
        "rainfall_probability_pct": 30 if risk_level in ["MODERATE", "HIGH"] else 15,
        "recommended_action": w_data.get("harvest_advisory", f"Store harvested {clean_crop} in verified ventilated bays to prevent humidity and moisture spoilage."),
        "vulnerability": f"Harvested {clean_crop} produce is vulnerable to ambient moisture, heat exposure, and humidity rotting.",
        "recommended_facility_name": f"{dist} Agro Cold Chain Hub"
    }

@router.get("")
def get_default_weather(location: Optional[str] = Query(None, description="City, district or village")):
    """
    Returns live weather condition and agricultural advisories for the given or regional location.
    """
    loc = location or ""
    return WeatherService.get_weather(location=loc)

@router.get("/{location}")
def get_weather_for_location(location: str):
    """
    Returns live weather condition and agricultural harvesting advisories via wttr.in.
    """
    return WeatherService.get_weather(location=location)

