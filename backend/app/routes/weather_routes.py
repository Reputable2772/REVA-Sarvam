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
    db: Session = Depends(get_db)
):
    """
    Evaluates weather risk for all lots scheduled for today by the specified farmer.
    If heavy rain or adverse conditions are predicted, returns proactive rescheduling advisory.
    """
    risks = WeatherProtectionService.check_scheduled_harvest_weather_risk(farmer_id=farmer_id, db=db)
    return {
        "success": True,
        "farmer_id": farmer_id,
        "count": len(risks),
        "harvest_weather_risks": risks
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

