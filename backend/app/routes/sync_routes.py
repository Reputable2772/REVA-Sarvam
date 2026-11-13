from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from datetime import datetime, timezone
from backend.app.database import get_db
from backend.app.models.models import HarvestLot, Farmer, AuditLog
from backend.app.services.netback_service import NetbackService

router = APIRouter(prefix="/sync", tags=["Phase 15 — Offline Background Sync"])

class OfflineLotItem(BaseModel):
    client_uuid: str
    farmer_id: str
    crop: str = "Produce"
    variety: str = "Standard Variety"
    quantity_kg: float = Field(..., gt=0)
    quality_grade: str = "Grade A"
    village: Optional[str] = None
    recorded_at: Optional[str] = None

class BatchSyncRequest(BaseModel):
    device_id: str
    lots: List[OfflineLotItem]

@router.post("/harvest-batch")
def sync_harvest_batch(
    req: BatchSyncRequest,
    db: Session = Depends(get_db)
):
    """
    Idempotent offline batch synchronization endpoint for Android WorkManager.
    Deduplicates based on client_uuid stored in metadata.
    """
    synced_lots = []
    skipped_duplicates = 0

    for item in req.lots:
        # Check idempotency via metadata
        existing = db.query(HarvestLot).filter(
            HarvestLot.farmer_id == item.farmer_id,
            HarvestLot.metadata_json.like(f"%{item.client_uuid}%")
        ).first()

        if existing:
            skipped_duplicates += 1
            synced_lots.append({
                "client_uuid": item.client_uuid,
                "lot_id": existing.lot_id,
                "status": "already_synced"
            })
            continue

        # Ensure farmer exists
        farmer = db.query(Farmer).filter(Farmer.farmer_id == item.farmer_id).first()
        if not farmer:
            # Generate deterministic unique phone based on farmer_id to respect unique constraint
            phone_num = f"+91 {abs(hash(item.farmer_id)) % 9000000000 + 1000000000}"
            farmer = Farmer(
                farmer_id=item.farmer_id,
                name=f"Farmer {item.farmer_id}",
                village=item.village or "",
                district=item.village or "",
                phone=phone_num
            )
            db.add(farmer)
            db.commit()

        # Generate unique Lot ID with dynamic district prefix
        dist_map = {"kolar": "KLR", "bangalore": "BLR", "bengaluru": "BLR", "mysore": "MYS", "mysuru": "MYS", "belgaum": "BEL", "belagavi": "BEL", "tumkur": "TUM", "mandya": "MDY", "hassan": "HAS"}
        d_clean = (farmer.district or "").lower().strip()
        dist_code = dist_map.get(d_clean, (farmer.district[:3] if farmer and farmer.district else "KLR").upper())
        date_str = datetime.now(timezone.utc).strftime("%Y%m%d")
        lot_count = db.query(HarvestLot).count() + 1
        lot_id = f"FR-{dist_code}-{date_str}-{lot_count:04d}"

        new_lot = HarvestLot(
            lot_id=lot_id,
            farmer_id=item.farmer_id,
            crop=item.crop,
            variety=item.variety,
            quantity_kg=item.quantity_kg,
            quality_grade=item.quality_grade,
            harvest_date=item.recorded_at or datetime.now(timezone.utc).strftime("%Y-%m-%d"),
            status="ready_for_sale",
            metadata_json=f'{{"client_uuid": "{item.client_uuid}", "synced_from_device": "{req.device_id}"}}'
        )
        db.add(new_lot)

        log = AuditLog(
            action="OFFLINE_HARVEST_SYNCED",
            actor_role="FARMER",
            actor_id=item.farmer_id,
            details=f"Synced offline lot {lot_id} (UUID: {item.client_uuid}) from device {req.device_id}"
        )
        db.add(log)
        db.commit()

        synced_lots.append({
            "client_uuid": item.client_uuid,
            "lot_id": lot_id,
            "status": "newly_synced",
            "quantity_kg": item.quantity_kg
        })

    return {
        "success": True,
        "device_id": req.device_id,
        "total_received": len(req.lots),
        "newly_synced": len(req.lots) - skipped_duplicates,
        "skipped_duplicates": skipped_duplicates,
        "results": synced_lots
    }
