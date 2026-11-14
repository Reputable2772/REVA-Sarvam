from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List
from datetime import datetime, timezone, timedelta
from backend.app.database import get_db
from backend.app.models.models import Farmer, HarvestLot, VirtualFpoPool, FarmerPoolShare, AuditLog
from backend.app.services.netback_service import NetbackService

router = APIRouter(prefix="/farmer", tags=["Farmer Harvest & Earnings"])

class HarvestRegistrationRequest(BaseModel):
    farmer_id: str
    crop: str = "Tomato"
    variety: Optional[str] = "Hybrid Red"
    quantity_kg: float = Field(200.0, gt=0)
    quality_grade: Optional[str] = "Grade A"
    harvest_date: Optional[str] = None
    harvest_time: Optional[str] = None
    expected_date_time: Optional[str] = None
    sell_immediately: Optional[bool] = True
    village: Optional[str] = None
    district: Optional[str] = None
    state: Optional[str] = None
    metadata: Optional[Dict[str, Any]] = None

@router.post("/register")
def register_harvest(
    req: HarvestRegistrationRequest,
    db: Session = Depends(get_db)
):
    # Ensure farmer exists by farmer_id or phone
    clean_id = str(req.farmer_id).strip()
    farmer = db.query(Farmer).filter(
        (Farmer.farmer_id == clean_id) |
        (Farmer.phone == clean_id) |
        (Farmer.phone.endswith(clean_id))
    ).first()

    if not farmer:
        # Determine unique phone number
        if clean_id.isdigit() and len(clean_id) == 10:
            target_phone = clean_id
        elif len(clean_id) >= 5 and clean_id[-5:].isdigit():
            target_phone = f"98450{clean_id[-5:]}"
        else:
            target_phone = f"98{abs(hash(clean_id)) % 100000000:08d}"

        # Guard against duplicate phone constraint
        existing_by_phone = db.query(Farmer).filter(
            (Farmer.phone == target_phone) | (Farmer.phone.endswith(target_phone))
        ).first()

        if existing_by_phone:
            farmer = existing_by_phone
            if req.district and not farmer.district:
                farmer.district = req.district
            if req.state and not farmer.state:
                farmer.state = req.state
        else:
            from backend.app.services.distance_service import DistanceService
            f_dist = req.district or req.village or ""
            f_state = req.state or "Karnataka"
            f_lat, f_lon = DistanceService.resolve_coordinates(f_dist or f_state, f_dist, f_state)
            farmer = Farmer(
                farmer_id=clean_id,
                name=f"Farmer {clean_id}",
                village=req.village or "",
                district=f_dist,
                state=f_state,
                pincode="",
                phone=target_phone,
                latitude=f_lat,
                longitude=f_lon
            )
            db.add(farmer)
            db.commit()

        if not farmer.latitude or not farmer.longitude:
            from backend.app.services.distance_service import DistanceService
            farmer.latitude, farmer.longitude = DistanceService.resolve_coordinates(farmer.district or farmer.state, farmer.district, farmer.state)
            db.commit()

    # Generate dynamic unique Lot ID: FR-{DIST}-{YYYYMMDD}-{XXXX}
    dist_map = {
        "kolar": "KLR", "bangalore": "BLR", "bengaluru": "BLR", "mysore": "MYS", "mysuru": "MYS",
        "belgaum": "BEL", "belagavi": "BEL", "tumkur": "TUM", "mandya": "MDY", "hassan": "HAS",
        "chengalpet": "CHE", "chengalpattu": "CHE", "chennai": "CHN", "kanchipuram": "KNC", "kancheepuram": "KNC",
        "nashik": "NSK", "pune": "PUN", "mumbai": "MUM"
    }
    d_clean = (farmer.district or "").lower().strip()
    dist_code = dist_map.get(d_clean, (farmer.district[:3] if farmer and farmer.district else "KLR").upper())
    date_str = datetime.now(timezone.utc).strftime("%Y%m%d")
    lot_count = db.query(HarvestLot).count() + 1
    lot_id = f"FR-{dist_code}-{date_str}-{lot_count:04d}"

    h_date = req.harvest_date or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    h_time = req.harvest_time or datetime.now(timezone.utc).strftime("%I:%M %p")
    tomorrow = datetime.now(timezone.utc) + timedelta(days=1)
    exp_dt = req.expected_date_time or tomorrow.strftime("%Y-%m-%d 09:00 AM")
    sell_imm = req.sell_immediately if req.sell_immediately is not None else True

    meta = req.metadata or {}
    meta["harvest_time"] = h_time
    meta["expected_date_time"] = exp_dt
    meta["sell_immediately"] = sell_imm

    new_lot = HarvestLot(
        lot_id=lot_id,
        farmer_id=farmer.farmer_id,
        crop=req.crop,
        variety=req.variety or "Hybrid Red",
        quantity_kg=req.quantity_kg,
        quality_grade=req.quality_grade or "Grade A",
        harvest_date=h_date,
        harvest_time=h_time,
        expected_date_time=exp_dt,
        sell_immediately=sell_imm,
        status="ready_for_sale",
        metadata_json=str(meta)
    )
    db.add(new_lot)

    # Log action
    log = AuditLog(
        action="HARVEST_REGISTERED",
        actor_role="FARMER",
        actor_id=farmer.farmer_id,
        details=f"Registered {req.quantity_kg}kg {req.variety} {req.crop} with Lot ID {lot_id} (Date: {h_date} {h_time}, Immediate: {sell_imm})"
    )
    db.add(log)
    db.commit()
    db.refresh(new_lot)

    # Calculate instant comparative netback
    netback = NetbackService.calculate_comparison(quantity_kg=req.quantity_kg, crop=req.crop)

    # Check for opportunistic co-transport pooling if selling immediately
    pooling_opportunity = None
    try:
        from backend.app.services.harvest_alignment_service import HarvestAlignmentService
        pooling_opportunity = HarvestAlignmentService.evaluate_alignment_opportunity(
            lot=new_lot,
            target_mandi=req.village or (f"{farmer.district} APMC" if farmer and farmer.district else "Local APMC"),
            db=db
        )
    except Exception:
        pooling_opportunity = None

    return {
        "success": True,
        "lot_id": lot_id,
        "harvest_id": lot_id, # Prominently exposed Unique Harvest ID
        "lot": {
            "lot_id": new_lot.lot_id,
            "harvest_id": new_lot.lot_id,
            "farmer_id": new_lot.farmer_id,
            "crop": new_lot.crop,
            "variety": new_lot.variety,
            "quantity_kg": new_lot.quantity_kg,
            "quality_grade": new_lot.quality_grade,
            "harvest_date": new_lot.harvest_date,
            "harvest_time": new_lot.harvest_time,
            "expected_date_time": new_lot.expected_date_time,
            "sell_immediately": new_lot.sell_immediately,
            "status": new_lot.status
        },
        "netback_comparison": netback,
        "pooling_opportunity": pooling_opportunity
    }

class WeatherRescheduleRequest(BaseModel):
    new_date: Optional[str] = None

@router.post("/lot/{lot_id}/weather-reschedule")
def reschedule_harvest_weather(
    lot_id: str,
    req: WeatherRescheduleRequest = WeatherRescheduleRequest(),
    db: Session = Depends(get_db)
):
    """
    One-tap harvest rescheduling when an adverse weather event / heavy rainfall is predicted.
    """
    try:
        from backend.app.services.weather_protection_service import WeatherProtectionService
        return WeatherProtectionService.reschedule_harvest_for_weather(
            lot_id=lot_id,
            new_date=req.new_date,
            db=db
        )
    except ValueError as e:
        raise HTTPException(status_code=404, detail=str(e))


@router.get("/lot/{lot_id}")
def get_harvest_lot(lot_id: str, db: Session = Depends(get_db)):
    lot = db.query(HarvestLot).filter(HarvestLot.lot_id == lot_id).first()
    if not lot:
        raise HTTPException(status_code=404, detail="Harvest lot not found.")
    return {
        "success": True,
        "harvest_id": lot.lot_id,
        "lot_id": lot.lot_id,
        "farmer_id": lot.farmer_id,
        "crop": lot.crop,
        "variety": lot.variety,
        "quantity_kg": lot.quantity_kg,
        "quality_grade": lot.quality_grade,
        "harvest_date": lot.harvest_date,
        "harvest_time": lot.harvest_time,
        "expected_date_time": lot.expected_date_time,
        "sell_immediately": lot.sell_immediately,
        "status": lot.status
    }

@router.get("/lots/{farmer_id}")
def get_farmer_lots(farmer_id: str, db: Session = Depends(get_db)):
    lots = db.query(HarvestLot).filter(HarvestLot.farmer_id == farmer_id).all()
    return {
        "success": True,
        "farmer_id": farmer_id,
        "lots": [
            {
                "lot_id": l.lot_id,
                "crop": l.crop,
                "variety": l.variety,
                "quantity_kg": l.quantity_kg,
                "quality_grade": l.quality_grade,
                "harvest_date": l.harvest_date,
                "status": l.status
            }
            for l in lots
        ]
    }

@router.get("/harvests")
def list_farmer_harvests(
    phone: Optional[str] = None,
    farmer_id: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Returns scheduled and stored harvests for a farmer identified by mobile phone or farmer_id.
    """
    query = db.query(HarvestLot)
    if phone:
        farmer = db.query(Farmer).filter(
            (Farmer.phone == phone) | (Farmer.phone.endswith(phone))
        ).first()
        if farmer:
            query = query.filter(HarvestLot.farmer_id.in_([farmer.farmer_id, phone]))
        else:
            query = query.filter(HarvestLot.farmer_id == phone)
    elif farmer_id:
        clean_fid = str(farmer_id).strip()
        farmer = db.query(Farmer).filter(
            (Farmer.farmer_id == clean_fid) |
            (Farmer.phone == clean_fid) |
            (Farmer.phone.endswith(clean_fid))
        ).first()
        if farmer:
            query = query.filter(HarvestLot.farmer_id.in_([farmer.farmer_id, clean_fid]))
        else:
            query = query.filter(HarvestLot.farmer_id == clean_fid)

    lots = query.order_by(HarvestLot.created_at.desc()).all()
    return {
        "success": True,
        "count": len(lots),
        "harvests": [
            {
                "harvest_id": l.lot_id,
                "lot_id": l.lot_id,
                "farmer_id": l.farmer_id,
                "crop": l.crop,
                "variety": l.variety,
                "quantity_kg": l.quantity_kg,
                "quality_grade": l.quality_grade,
                "harvest_date": l.harvest_date,
                "harvest_time": l.harvest_time,
                "expected_date_time": l.expected_date_time,
                "sell_immediately": l.sell_immediately,
                "status": l.status
            }
            for l in lots
        ]
    }

@router.get("/harvests/{harvest_id}/mandi-analysis")
def get_harvest_mandi_analysis(
    harvest_id: str,
    target_mandi: Optional[str] = None,
    origin_district: Optional[str] = None,
    origin_state: Optional[str] = None,
    db: Session = Depends(get_db)
):
    """
    Evaluates all mandi options across India for a specific harvest,
    transparently analyzing all costs, transport, APMC commissions, and net cash in hand.
    """
    from backend.app.services.pan_india_mandi_service import PanIndiaMandiService
    from backend.app.services.distance_service import DistanceService

    lot = db.query(HarvestLot).filter(HarvestLot.lot_id == harvest_id).first()
    farmer = db.query(Farmer).filter(Farmer.farmer_id == lot.farmer_id).first() if lot else None

    crop = lot.crop if lot and lot.crop else "Produce"
    quantity_kg = lot.quantity_kg if lot else 200.0
    farmer_village = farmer.village if farmer and farmer.village else None
    farmer_district = (farmer.district if farmer and farmer.district else "") or origin_district or ""
    farmer_state = (farmer.state if farmer and farmer.state else "") or origin_state or ""
    farmer_coords = (farmer.latitude, farmer.longitude) if farmer and farmer.latitude and farmer.longitude else None

    if not farmer_coords and (farmer_district or farmer_state):
        farmer_coords = DistanceService.resolve_coordinates(farmer_district or farmer_state, farmer_district, farmer_state)

    mandi_options = PanIndiaMandiService.evaluate_mandi_options(
        crop=crop,
        quantity_kg=quantity_kg,
        farmer_district=farmer_district,
        farmer_state=farmer_state,
        target_mandi=target_mandi,
        farmer_village=farmer_village,
        farmer_coords=farmer_coords
    )

    loc_label = f"{farmer_village}, {farmer_district}, {farmer_state}" if farmer_village else f"{farmer_district}, {farmer_state}"

    return {
        "success": True,
        "harvest_id": harvest_id,
        "crop": crop,
        "quantity_kg": quantity_kg,
        "farmer_location": loc_label,
        "mandis_count": len(mandi_options),
        "mandis": mandi_options
    }

class NetbackCalcRequest(BaseModel):
    quantity_kg: float = Field(..., gt=0)
    crop: str = "Produce"
    kolar_rate: Optional[float] = 24.0
    bangarpet_rate: Optional[float] = 28.0
    direct_rate: Optional[float] = 28.50
    shared_freight: Optional[float] = None
    local_rate: Optional[float] = None
    distant_rate: Optional[float] = None
    local_mandi_name: Optional[str] = None
    distant_mandi_name: Optional[str] = None
    origin_location: Optional[str] = None
    origin_district: Optional[str] = None
    origin_state: Optional[str] = None
    local_transport: Optional[float] = None
    distant_transport: Optional[float] = None

@router.post("/netback")
def calculate_netback(req: NetbackCalcRequest):
    return NetbackService.calculate_comparison(
        quantity_kg=req.quantity_kg,
        crop=req.crop,
        kolar_rate=req.kolar_rate or 24.0,
        bangarpet_rate=req.bangarpet_rate or 28.0,
        local_rate=req.local_rate,
        distant_rate=req.distant_rate,
        direct_rate=req.direct_rate or 28.50,
        shared_freight_cost=req.shared_freight,
        origin_location=req.origin_location,
        origin_district=req.origin_district,
        origin_state=req.origin_state,
        local_mandi_name=req.local_mandi_name,
        distant_mandi_name=req.distant_mandi_name,
        local_transport=req.local_transport,
        distant_transport=req.distant_transport
    )

class AcceptPoolRequest(BaseModel):
    farmer_id: str
    pool_id: str
    lot_id: str
    accept: bool = True

@router.post("/accept-pool")
def accept_pool_offer(req: AcceptPoolRequest, db: Session = Depends(get_db)):
    share = db.query(FarmerPoolShare).filter(
        FarmerPoolShare.pool_id == req.pool_id,
        FarmerPoolShare.farmer_id == req.farmer_id,
        FarmerPoolShare.lot_id == req.lot_id
    ).first()

    if not share:
        raise HTTPException(status_code=404, detail="Farmer pool share not found.")

    share.farmer_accepted = req.accept
    db.commit()

    return {
        "success": True,
        "pool_id": req.pool_id,
        "lot_id": req.lot_id,
        "farmer_accepted": share.farmer_accepted,
        "allocated_kg": share.allocated_kg,
        "net_payout": share.net_payout
    }

@router.get("/earnings/{farmer_id}")
def get_earnings_summary(farmer_id: str, db: Session = Depends(get_db)):
    shares = db.query(FarmerPoolShare).filter(FarmerPoolShare.farmer_id == farmer_id).all()
    total_net = sum(s.net_payout for s in shares)
    total_kg = sum(s.allocated_kg for s in shares)

    return {
        "success": True,
        "farmer_id": farmer_id,
        "total_allocated_kg": total_kg,
        "total_net_payout": round(total_net, 2),
        "shares": [
            {
                "pool_id": s.pool_id,
                "lot_id": s.lot_id,
                "allocated_kg": s.allocated_kg,
                "gross_amount": s.gross_amount,
                "allocated_freight": s.allocated_freight,
                "net_payout": s.net_payout,
                "farmer_accepted": s.farmer_accepted,
                "buyer_accepted": s.buyer_accepted
            }
            for s in shares
        ]
    }
