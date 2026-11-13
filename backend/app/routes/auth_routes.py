import hashlib
import uuid
import re
from fastapi import APIRouter, HTTPException, Depends, Query
from pydantic import BaseModel, Field
from typing import Optional, List, Dict, Any
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.models.models import Farmer, AuditLog
from backend.app.services.geo_dataset_service import GeoDatasetService

router = APIRouter(prefix="/auth", tags=["Mobile & MPIN Authentication"])

SALT = "farmroute_kisan_secure_salt"

def hash_mpin(mpin: str) -> str:
    return hashlib.sha256(f"{SALT}_{mpin}".encode("utf-8")).hexdigest()

def verify_mpin(mpin: str, mpin_hash: str) -> bool:
    return hash_mpin(mpin) == mpin_hash

def clean_phone_number(phone: str) -> str:
    digits = re.sub(r"\D", "", phone)
    if digits.startswith("91") and len(digits) == 12:
        digits = digits[2:]
    return digits

class CheckAccountRequest(BaseModel):
    phone: str = Field(..., description="10-digit mobile number")

class LoginRequest(BaseModel):
    phone: str = Field(..., description="10-digit mobile number")
    mpin: str = Field(..., min_length=4, max_length=6, description="4 to 6 digit MPIN")

class RegisterRequest(BaseModel):
    phone: str = Field(..., description="10-digit mobile number")
    mpin: str = Field(..., min_length=4, max_length=6, description="4 to 6 digit MPIN")
    name: str = Field(..., min_length=2, max_length=100, description="Farmer full name")
    state: str = Field(..., description="State name from AGMARKNET dataset")
    district: str = Field(..., description="District name in state from AGMARKNET dataset")
    pincode: str = Field(..., min_length=6, max_length=6, description="6-digit postal code")

@router.post("/check-account")
def check_account(req: CheckAccountRequest, db: Session = Depends(get_db)):
    phone = clean_phone_number(req.phone)
    if not phone or len(phone) < 10:
        raise HTTPException(status_code=400, detail="Invalid phone number format. Must contain 10 digits.")
    
    farmer = db.query(Farmer).filter(Farmer.phone == phone).first()
    if not farmer:
        farmer = db.query(Farmer).filter(Farmer.phone.endswith(phone)).first()

    if farmer:
        return {
            "exists": True,
            "phone": phone,
            "name": farmer.name,
            "district": farmer.district,
            "state": farmer.state,
            "pincode": farmer.pincode
        }
    return {
        "exists": False,
        "phone": phone,
        "name": "",
        "district": "",
        "state": "",
        "pincode": ""
    }

@router.post("/login")
def login(req: LoginRequest, db: Session = Depends(get_db)):
    phone = clean_phone_number(req.phone)
    farmer = db.query(Farmer).filter(Farmer.phone == phone).first()
    if not farmer:
        farmer = db.query(Farmer).filter(Farmer.phone.endswith(phone)).first()
    
    if not farmer:
        raise HTTPException(
            status_code=404,
            detail="Account does not exist. Please register first."
        )

    if not verify_mpin(req.mpin, farmer.mpin_hash) and farmer.mpin_hash != req.mpin:
        raise HTTPException(
            status_code=401,
            detail="Invalid MPIN. Please try again."
        )

    token = f"fr-token-{phone}-{uuid.uuid4().hex[:12]}"
    
    db.add(AuditLog(
        action="FARMER_LOGGED_IN",
        actor_role="FARMER",
        actor_id=farmer.farmer_id,
        details=f"Successful MPIN authentication for {farmer.name} ({phone})"
    ))
    db.commit()

    return {
        "status": "authenticated",
        "token": token,
        "farmer": {
            "farmer_id": farmer.farmer_id,
            "name": farmer.name,
            "phone": phone,
            "district": farmer.district,
            "state": farmer.state,
            "pincode": farmer.pincode
        }
    }

@router.post("/register")
def register(req: RegisterRequest, db: Session = Depends(get_db)):
    phone = clean_phone_number(req.phone)
    if len(phone) != 10 or not phone.isdigit():
        raise HTTPException(status_code=400, detail="Phone number must be exactly 10 digits.")

    if not req.mpin.isdigit() or len(req.mpin) < 4:
        raise HTTPException(status_code=400, detail="MPIN must be 4 to 6 numeric digits.")

    # Spatial verification using AGMARKNET dataset
    is_valid, err_msg = GeoDatasetService.validate_location(
        state=req.state,
        district=req.district,
        pincode=req.pincode
    )
    if not is_valid:
        raise HTTPException(status_code=400, detail=err_msg)

    # Check if account already exists by phone
    existing = db.query(Farmer).filter(
        (Farmer.phone == phone) | (Farmer.phone.endswith(phone))
    ).first()
    if existing:
        existing.name = req.name.strip()
        existing.state = req.state.strip()
        existing.district = req.district.strip()
        existing.pincode = req.pincode.strip()
        existing.mpin_hash = hash_mpin(req.mpin)
        db.commit()
        farmer = existing
    else:
        dist_code = req.district[:3].upper() if len(req.district) >= 3 else "KLR"
        base_id = f"FR-{dist_code}-{phone[-6:]}"
        if db.query(Farmer).filter(Farmer.farmer_id == base_id).first():
            farmer_id = f"FR-{dist_code}-{phone[-6:]}-{uuid.uuid4().hex[:4].upper()}"
        else:
            farmer_id = base_id

        farmer = Farmer(
            farmer_id=farmer_id,
            phone=phone,
            name=req.name.strip(),
            village="",
            district=req.district.strip(),
            state=req.state.strip(),
            pincode=req.pincode.strip(),
            mpin_hash=hash_mpin(req.mpin)
        )
        db.add(farmer)
        db.commit()

    token = f"fr-token-{phone}-{uuid.uuid4().hex[:12]}"
    
    db.add(AuditLog(
        action="FARMER_REGISTERED",
        actor_role="FARMER",
        actor_id=farmer.farmer_id,
        details=f"New farmer onboarding: {farmer.name}, {farmer.district}, {farmer.state} ({farmer.pincode})"
    ))
    db.commit()

    return {
        "status": "registered",
        "token": token,
        "farmer": {
            "farmer_id": farmer.farmer_id,
            "name": farmer.name,
            "phone": phone,
            "district": farmer.district,
            "state": farmer.state,
            "pincode": farmer.pincode
        }
    }

@router.get("/geo/states", response_model=List[str])
def get_states():
    return GeoDatasetService.get_valid_states()

@router.get("/geo/districts", response_model=List[str])
def get_districts(state: str = Query(..., description="State name")):
    return GeoDatasetService.get_districts_for_state(state)
