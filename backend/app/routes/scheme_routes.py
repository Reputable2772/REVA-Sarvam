import json
from fastapi import APIRouter, Depends, HTTPException, Query, Body
from sqlalchemy.orm import Session
from typing import Optional, List, Dict, Any
from pydantic import BaseModel, Field

from backend.app.database import get_db
from backend.app.models.models import Farmer
from backend.app.models.scheme_models import SchemeSource, Scheme, FarmerSavedOpportunity, FarmerBenefitNotification
from backend.app.services.scheme_matching_service import SchemeMatchingService
from backend.app.services.scheme_seed_service import seed_verified_schemes

router = APIRouter(prefix="/schemes", tags=["AI Farmer Benefits & Schemes"])

class ProfileUpdateRequest(BaseModel):
    farmer_id: Optional[str] = None
    phone: Optional[str] = None
    landholding_hectares: Optional[float] = None
    fruits_id: Optional[str] = None
    is_fpo_member: Optional[bool] = None

class BookmarkRequest(BaseModel):
    farmer_id: str
    scheme_id: str
    saved: bool = True

@router.get("/sources")
def list_official_scheme_sources(db: Session = Depends(get_db)):
    """Returns official central and state government publication registries."""
    seed_verified_schemes(db)
    sources = db.query(SchemeSource).all()
    return {
        "success": True,
        "count": len(sources),
        "sources": [
            {
                "source_id": s.source_id,
                "name": s.name,
                "portal_name": s.portal_name,
                "authority_type": s.authority_type,
                "official_url": s.official_url,
                "helpdesk_contact": s.helpdesk_contact,
                "last_checked_date": s.last_checked_date
            }
            for s in sources
        ]
    }

@router.get("/all")
def list_all_verified_schemes(
    category: Optional[str] = Query(None, description="Filter by category"),
    state: Optional[str] = Query(None, description="Filter by applicable state"),
    db: Session = Depends(get_db)
):
    """Lists all verified schemes with official traceable publication links."""
    seed_verified_schemes(db)
    query = db.query(Scheme).filter(Scheme.verification_status == "OFFICIALLY_VERIFIED")
    if category:
        query = query.filter(Scheme.category == category)

    schemes = query.all()
    results = []
    for s in schemes:
        states = json.loads(s.applicable_states_json or "[]")
        if state and states and state not in states:
            continue
        results.append({
            "scheme_id": s.scheme_id,
            "title_en": s.title_en,
            "title_kn": s.title_kn,
            "category": s.category,
            "target_level": s.target_level,
            "issuing_department": s.issuing_department,
            "benefit_amount_spec": s.benefit_amount_spec,
            "benefit_description_en": s.benefit_description_en,
            "benefit_description_kn": s.benefit_description_kn,
            "eligibility_criteria_en": s.eligibility_criteria_en,
            "eligibility_criteria_kn": s.eligibility_criteria_kn,
            "exclusion_criteria_en": s.exclusion_criteria_en,
            "exclusion_criteria_kn": s.exclusion_criteria_kn,
            "required_documents": json.loads(s.required_documents_json or "[]"),
            "application_steps": json.loads(s.application_steps_json or "[]"),
            "application_method": s.application_method,
            "official_portal_url": s.official_portal_url,
            "official_contact": s.official_contact,
            "verification_status": s.verification_status,
            "last_verified_date": s.last_verified_date,
            "freshness_label": s.freshness_label,
            "requires_fruits_id": s.requires_fruits_id,
            "requires_fpo_membership": s.requires_fpo_membership
        })

    return {"success": True, "count": len(results), "schemes": results}

@router.get("/detail/{scheme_id}")
def get_scheme_detail(scheme_id: str, db: Session = Depends(get_db)):
    """Retrieves full verified scheme dossier with required documents and application steps."""
    seed_verified_schemes(db)
    s = db.query(Scheme).filter(Scheme.scheme_id == scheme_id).first()
    if not s:
        raise HTTPException(status_code=404, detail="Scheme not found")

    return {
        "success": True,
        "scheme": {
            "scheme_id": s.scheme_id,
            "title_en": s.title_en,
            "title_kn": s.title_kn,
            "category": s.category,
            "target_level": s.target_level,
            "issuing_department": s.issuing_department,
            "benefit_amount_spec": s.benefit_amount_spec,
            "benefit_description_en": s.benefit_description_en,
            "benefit_description_kn": s.benefit_description_kn,
            "eligibility_criteria_en": s.eligibility_criteria_en,
            "eligibility_criteria_kn": s.eligibility_criteria_kn,
            "exclusion_criteria_en": s.exclusion_criteria_en,
            "exclusion_criteria_kn": s.exclusion_criteria_kn,
            "required_documents": json.loads(s.required_documents_json or "[]"),
            "application_steps": json.loads(s.application_steps_json or "[]"),
            "application_method": s.application_method,
            "official_portal_url": s.official_portal_url,
            "official_contact": s.official_contact,
            "verification_status": s.verification_status,
            "last_verified_date": s.last_verified_date,
            "freshness_label": s.freshness_label,
            "requires_fruits_id": s.requires_fruits_id,
            "requires_fpo_membership": s.requires_fpo_membership
        }
    }

@router.get("/recommendations/{identifier}")
def get_personalized_recommendations(identifier: str, db: Session = Depends(get_db)):
    """
    Computes deterministic opportunity matches for a farmer (by farmer_id or phone).
    Evaluates published rules without AI hallucination.
    """
    seed_verified_schemes(db)
    farmer = db.query(Farmer).filter(
        (Farmer.farmer_id == identifier) | (Farmer.phone == identifier)
    ).first()

    if not farmer:
        # Default to FARMER-01 if not found for seamless demo
        farmer = db.query(Farmer).first()

    if not farmer:
        raise HTTPException(status_code=404, detail="No registered farmers found.")

    matches = SchemeMatchingService.evaluate_farmer_opportunities(farmer.farmer_id, db)
    return {
        "success": True,
        "farmer_id": farmer.farmer_id,
        "farmer_name": farmer.name,
        "location": f"{farmer.district}, {farmer.state}",
        "total_opportunities": len(matches),
        "likely_eligible_count": len([m for m in matches if m["match_status"] == "LIKELY_ELIGIBLE"]),
        "more_info_needed_count": len([m for m in matches if m["match_status"] == "MORE_INFO_REQUIRED"]),
        "opportunities": matches
    }

@router.post("/update-profile")
def update_voluntary_profile(req: ProfileUpdateRequest, db: Session = Depends(get_db)):
    """
    Voluntarily updates missing profile fields (FRUITS ID, Landholding, FPO membership)
    and immediately recalculates eligibility.
    """
    farmer = None
    if req.farmer_id:
        farmer = db.query(Farmer).filter(Farmer.farmer_id == req.farmer_id).first()
    elif req.phone:
        farmer = db.query(Farmer).filter(Farmer.phone == req.phone).first()

    if not farmer:
        farmer = db.query(Farmer).first()

    meta = {}
    try:
        if farmer.metadata_json:
            meta = json.loads(farmer.metadata_json)
    except Exception:
        meta = {}

    if req.landholding_hectares is not None:
        meta["landholding_hectares"] = req.landholding_hectares
    if req.fruits_id is not None:
        meta["fruits_id"] = req.fruits_id
    if req.is_fpo_member is not None:
        meta["is_fpo_member"] = req.is_fpo_member

    farmer.metadata_json = json.dumps(meta)
    db.commit()

    # Re-evaluate
    matches = SchemeMatchingService.evaluate_farmer_opportunities(farmer.farmer_id, db)
    return {
        "success": True,
        "farmer_id": farmer.farmer_id,
        "updated_attributes": meta,
        "recalculated_opportunities": matches
    }

@router.post("/bookmark")
def toggle_bookmark(req: BookmarkRequest, db: Session = Depends(get_db)):
    """Bookmarks or unbookmarks a scheme for the farmer."""
    existing = db.query(FarmerSavedOpportunity).filter(
        FarmerSavedOpportunity.farmer_id == req.farmer_id,
        FarmerSavedOpportunity.scheme_id == req.scheme_id
    ).first()

    if req.saved and not existing:
        saved = FarmerSavedOpportunity(farmer_id=req.farmer_id, scheme_id=req.scheme_id)
        db.add(saved)
        db.commit()
    elif not req.saved and existing:
        db.delete(existing)
        db.commit()

    return {"success": True, "farmer_id": req.farmer_id, "scheme_id": req.scheme_id, "is_saved": req.saved}

@router.get("/bookmarks/{farmer_id}")
def get_farmer_bookmarks(farmer_id: str, db: Session = Depends(get_db)):
    """Returns saved opportunity IDs for a farmer."""
    bookmarks = db.query(FarmerSavedOpportunity).filter(FarmerSavedOpportunity.farmer_id == farmer_id).all()
    return {"success": True, "saved_scheme_ids": [b.scheme_id for b in bookmarks]}

@router.get("/notifications/{identifier}")
def get_farmer_notifications(identifier: str, db: Session = Depends(get_db)):
    """Returns in-app smart alerts explaining why the farmer received each notice."""
    seed_verified_schemes(db)
    farmer = db.query(Farmer).filter(
        (Farmer.farmer_id == identifier) | (Farmer.phone == identifier)
    ).first()

    if not farmer:
        farmer = db.query(Farmer).first()

    if not farmer:
        return {"success": True, "notifications": []}

    notifs = SchemeMatchingService.generate_smart_notifications_for_farmer(farmer.farmer_id, db)
    return {"success": True, "farmer_id": farmer.farmer_id, "count": len(notifs), "notifications": notifs}

@router.post("/notifications/{notification_id}/read")
def mark_notification_read(notification_id: int, db: Session = Depends(get_db)):
    """Marks a notification as read."""
    notif = db.query(FarmerBenefitNotification).filter(FarmerBenefitNotification.id == notification_id).first()
    if notif:
        notif.is_read = True
        db.commit()
    return {"success": True, "notification_id": notification_id}
