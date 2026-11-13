from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any, List
from datetime import datetime, timezone
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
    village: Optional[str] = "Mulbagal"
    metadata: Optional[Dict[str, Any]] = None

@router.post("/register")
def register_harvest(
    req: HarvestRegistrationRequest,
    db: Session = Depends(get_db)
):
    # Ensure farmer exists
    farmer = db.query(Farmer).filter(Farmer.farmer_id == req.farmer_id).first()
    if not farmer:
        farmer = Farmer(
            farmer_id=req.farmer_id,
            name=f"Farmer {req.farmer_id}",
            village=req.village or "Mulbagal",
            district="Kolar",
            state="Karnataka",
            pincode="563101",
            phone=f"98450{req.farmer_id[-5:]}" if len(req.farmer_id) >= 5 else "9845000000"
        )
        db.add(farmer)
        db.commit()

    # Generate unique Lot ID: FR-KLR-YYYYMMDD-XXXX
    date_str = datetime.now(timezone.utc).strftime("%Y%m%d")
    lot_count = db.query(HarvestLot).count() + 1
    lot_id = f"FR-KLR-{date_str}-{lot_count:04d}"

    h_date = req.harvest_date or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    h_time = req.harvest_time or datetime.now(timezone.utc).strftime("%I:%M %p")
    exp_dt = req.expected_date_time or "2026-11-14 09:00 AM"
    sell_imm = req.sell_immediately if req.sell_immediately is not None else True

    meta = req.metadata or {}
    meta["harvest_time"] = h_time
    meta["expected_date_time"] = exp_dt
    meta["sell_immediately"] = sell_imm

    new_lot = HarvestLot(
        lot_id=lot_id,
        farmer_id=req.farmer_id,
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
        actor_id=req.farmer_id,
        details=f"Registered {req.quantity_kg}kg {req.variety} {req.crop} with Lot ID {lot_id} (Date: {h_date} {h_time}, Immediate: {sell_imm})"
    )
    db.add(log)
    db.commit()
    db.refresh(new_lot)

    # Calculate instant comparative netback
    netback = NetbackService.calculate_comparison(quantity_kg=req.quantity_kg, crop=req.crop)

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
        "netback_comparison": netback
    }

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

class NetbackCalcRequest(BaseModel):
    quantity_kg: float = Field(..., gt=0)
    crop: str = "Tomato"
    kolar_rate: Optional[float] = 24.0
    bangarpet_rate: Optional[float] = 28.0
    direct_rate: Optional[float] = 28.50
    shared_freight: Optional[float] = 549.0

@router.post("/netback")
def calculate_netback(req: NetbackCalcRequest):
    return NetbackService.calculate_comparison(
        quantity_kg=req.quantity_kg,
        crop=req.crop,
        kolar_rate=req.kolar_rate or 24.0,
        bangarpet_rate=req.bangarpet_rate or 28.0,
        direct_rate=req.direct_rate or 28.50,
        shared_freight_cost=req.shared_freight or 549.0
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
