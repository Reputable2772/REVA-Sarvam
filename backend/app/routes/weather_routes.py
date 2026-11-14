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
    language_code: Optional[str] = Query("en-IN"),
    db: Session = Depends(get_db)
):
    """
    Returns proactive harvest weather risk alerts for a farmer and district.
    """
    from backend.app.models.models import Farmer
    clean_fid = str(farmer_id).strip()
    farmer = db.query(Farmer).filter(
        (Farmer.farmer_id == clean_fid) |
        (Farmer.phone == clean_fid) |
        (Farmer.phone.endswith(clean_fid))
    ).first()
    dist = district or (farmer.district if farmer else "Local")
    w_data = WeatherService.get_weather(dist)
    risk_level = w_data.get("weather_risk_level", "LOW")
    has_alert = risk_level in ["MODERATE", "HIGH"]
    return {
        "success": True,
        "has_active_alert": has_alert,
        "warning_id": f"IMD-{dist[:3].upper()}-ALERT",
        "district": dist,
        "alert_type": "WEATHER_ADVISORY",
        "severity": "ORANGE" if risk_level == "HIGH" else "YELLOW",
        "headline": f"Official IMD Weather Report: {w_data.get('condition', 'Cloudy')} with {w_data.get('precip_mm', 0)}mm precipitation in {dist}.",
        "headline_kannada": f"ಐಎಂಡಿ ಹವಾಮಾನ ವರದಿ: {dist} ಪ್ರದೇಶದಲ್ಲಿ {w_data.get('condition', 'ಮೋಡ ಕವಿದ ವಾತಾವರಣ')}.",
        "temperature_c": w_data.get("temperature_c", 26),
        "rainfall_probability_pct": 30 if has_alert else 10,
        "recommended_action": w_data.get("harvest_advisory", "Proceed with scheduled harvest."),
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

