from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from datetime import datetime, timezone
from backend.app.database import get_db
from backend.app.models.models import ColdStorageFacility, ColdStorageBooking, AuditLog

router = APIRouter(prefix="/cold-storage", tags=["Phase 14 — Cold Storage Discovery"])

# Seed Kolar corridor facilities if empty
INITIAL_COLD_STORAGE_FACILITIES = [
    {
        "facility_id": "CS-KLR-001",
        "name": "Kolar APMC Agro Cold Chain Hub",
        "district": "Kolar",
        "location": "APMC Yard, Bangarpet Road, Kolar",
        "latitude": 13.1367,
        "longitude": 78.1291,
        "total_capacity_tonnes": 150.0,
        "available_capacity_tonnes": 65.0,
        "daily_rate_per_quintal": 16.50,
        "temperature_celsius": 12.0,
        "contact_phone": "+91 98450 22001"
    },
    {
        "facility_id": "CS-KLR-002",
        "name": "Mulbagal Cooperative Cold Storage",
        "district": "Kolar",
        "location": "NH-75 Bypass, Mulbagal",
        "latitude": 13.1642,
        "longitude": 78.3962,
        "total_capacity_tonnes": 80.0,
        "available_capacity_tonnes": 28.0,
        "daily_rate_per_quintal": 15.00,
        "temperature_celsius": 11.5,
        "contact_phone": "+91 98450 22002"
    },
    {
        "facility_id": "CS-KLR-003",
        "name": "Malur Precision Controlled Atmosphere Store",
        "district": "Kolar",
        "location": "KIADB Industrial Area, Malur",
        "latitude": 13.0044,
        "longitude": 77.9392,
        "total_capacity_tonnes": 200.0,
        "available_capacity_tonnes": 110.0,
        "daily_rate_per_quintal": 18.00,
        "temperature_celsius": 10.5,
        "contact_phone": "+91 98450 22003"
    }
]

def ensure_seeded_facilities(db: Session):
    if db.query(ColdStorageFacility).count() == 0:
        for f in INITIAL_COLD_STORAGE_FACILITIES:
            db.add(ColdStorageFacility(**f))
        db.commit()

class ColdStorageBookingRequest(BaseModel):
    farmer_id: str
    facility_id: str
    crop: str = "Tomato"
    quantity_kg: float = Field(..., gt=0)
    duration_days: int = Field(7, ge=1, le=90)

@router.get("/facilities")
def get_facilities(
    district: Optional[str] = "Kolar",
    db: Session = Depends(get_db)
):
    """
    Lists cold storage facilities with real-time bay capacity tracking and pricing.
    """
    ensure_seeded_facilities(db)
    query = db.query(ColdStorageFacility)
    if district:
        query = query.filter(ColdStorageFacility.district.ilike(f"%{district}%"))

    facilities = query.all()
    return {
        "success": True,
        "count": len(facilities),
        "district": district,
        "facilities": [
            {
                "facility_id": f.facility_id,
                "name": f.name,
                "district": f.district,
                "location": f.location,
                "latitude": f.latitude,
                "longitude": f.longitude,
                "total_capacity_tonnes": f.total_capacity_tonnes,
                "available_capacity_tonnes": f.available_capacity_tonnes,
                "available_crates_estimate": int(f.available_capacity_tonnes * 40), # 25kg crates
                "daily_rate_per_quintal": f.daily_rate_per_quintal,
                "daily_rate_per_crate": round(f.daily_rate_per_quintal * 0.25, 2),
                "temperature_celsius": f.temperature_celsius,
                "contact_phone": f.contact_phone
            }
            for f in facilities
        ]
    }

@router.post("/book")
def book_cold_storage(
    req: ColdStorageBookingRequest,
    db: Session = Depends(get_db)
):
    """
    Farmer cold storage reservation workflow. Enforces capacity validation.
    """
    ensure_seeded_facilities(db)
    facility = db.query(ColdStorageFacility).filter(
        ColdStorageFacility.facility_id == req.facility_id
    ).first()

    if not facility:
        raise HTTPException(status_code=404, detail="Cold storage facility not found.")

    required_tonnes = req.quantity_kg / 1000.0
    if facility.available_capacity_tonnes < required_tonnes:
        raise HTTPException(
            status_code=400,
            detail=f"Insufficient capacity. Available: {facility.available_capacity_tonnes}T, requested: {required_tonnes}T"
        )

    # Generate Booking ID
    date_str = datetime.now(timezone.utc).strftime("%Y%m%d")
    count = db.query(ColdStorageBooking).count() + 1
    booking_id = f"CS-BOOK-{date_str}-{count:04d}"

    booking = ColdStorageBooking(
        booking_id=booking_id,
        facility_id=req.facility_id,
        farmer_id=req.farmer_id,
        crop=req.crop,
        quantity_kg=req.quantity_kg,
        duration_days=req.duration_days,
        status="CONFIRMED"
    )
    db.add(booking)

    # Deduct available capacity
    facility.available_capacity_tonnes = round(facility.available_capacity_tonnes - required_tonnes, 2)

    # Calculate estimated storage fee
    quintals = req.quantity_kg / 100.0
    est_total_fee = round(quintals * facility.daily_rate_per_quintal * req.duration_days, 2)

    log = AuditLog(
        action="COLD_STORAGE_BOOKED",
        actor_role="FARMER",
        actor_id=req.farmer_id,
        details=f"Booked {req.quantity_kg}kg for {req.duration_days} days at {facility.name}. Booking ID {booking_id}"
    )
    db.add(log)
    db.commit()

    return {
        "success": True,
        "booking_id": booking_id,
        "facility_name": facility.name,
        "facility_id": facility.facility_id,
        "crop": req.crop,
        "quantity_kg": req.quantity_kg,
        "duration_days": req.duration_days,
        "estimated_total_fee_inr": est_total_fee,
        "status": "CONFIRMED",
        "remaining_facility_capacity_tonnes": facility.available_capacity_tonnes,
        "advisory": "Produce must be crated and delivered between 08:00 AM and 06:00 PM."
    }

@router.get("/bookings/{farmer_id}")
def get_farmer_bookings(farmer_id: str, db: Session = Depends(get_db)):
    bookings = db.query(ColdStorageBooking).filter(ColdStorageBooking.farmer_id == farmer_id).all()
    return {
        "success": True,
        "farmer_id": farmer_id,
        "bookings": [
            {
                "booking_id": b.booking_id,
                "facility_id": b.facility_id,
                "crop": b.crop,
                "quantity_kg": b.quantity_kg,
                "duration_days": b.duration_days,
                "status": b.status,
                "created_at": b.created_at.isoformat() if b.created_at else None
            }
            for b in bookings
        ]
    }
