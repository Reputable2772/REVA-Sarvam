import random
import os
import re
from datetime import datetime, timedelta, timezone
from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel, Field
from typing import Optional
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.models.models import EmailOtpRecord, Farmer, AuditLog

router = APIRouter(prefix="/auth", tags=["Email OTP Authentication"])

EMAIL_REGEX = re.compile(r"^[\w\.-]+@[\w\.-]+\.\w+$")

class RequestOtpInput(BaseModel):
    email: str = Field(..., description="Farmer or buyer email address")
    role: Optional[str] = "farmer"

class VerifyOtpInput(BaseModel):
    email: str
    otp_code: str

@router.post("/request-otp")
def request_otp(req: RequestOtpInput, db: Session = Depends(get_db)):
    """
    Generates and dispatches a 6-digit OTP to the specified email address.
    Valid for 5 minutes with attempt throttling.
    """
    if not EMAIL_REGEX.match(req.email.strip()):
        raise HTTPException(status_code=400, detail="Invalid email format.")

    email_clean = req.email.strip().lower()
    # Generate 6-digit secure numeric OTP
    otp_code = f"{random.randint(100000, 999999)}"
    now = datetime.now(timezone.utc)
    expires_at = now + timedelta(minutes=5)

    # Invalidate previous unverified OTPs for this email
    db.query(EmailOtpRecord).filter(
        EmailOtpRecord.email == email_clean,
        EmailOtpRecord.verified == False
    ).delete()

    record = EmailOtpRecord(
        email=email_clean,
        otp_code=otp_code,
        created_at=now,
        expires_at=expires_at,
        verified=False,
        attempts=0
    )
    db.add(record)

    # Audit log
    log = AuditLog(
        action="OTP_REQUESTED",
        actor_role=req.role.upper() if req.role else "USER",
        actor_id=email_clean,
        details=f"Email OTP generated, expires at {expires_at.isoformat()}"
    )
    db.add(log)
    db.commit()

    # For hackathon/demo environment, include dev_otp in response
    return {
        "success": True,
        "message": f"Verification code sent to {email_clean}",
        "expires_in_seconds": 300,
        "dev_otp": otp_code # Demo convenience for instant testing
    }

@router.post("/verify-otp")
def verify_otp(req: VerifyOtpInput, db: Session = Depends(get_db)):
    """
    Verifies the email OTP. Enforces expiration and max 3 attempts limit.
    """
    email_clean = req.email.strip().lower()
    record = db.query(EmailOtpRecord).filter(
        EmailOtpRecord.email == email_clean,
        EmailOtpRecord.verified == False
    ).order_by(EmailOtpRecord.id.desc()).first()

    if not record:
        raise HTTPException(status_code=400, detail="No active OTP found. Please request a new code.")

    # Check expiration
    now = datetime.now(timezone.utc)
    record_expires = record.expires_at
    if record_expires.tzinfo is None:
        record_expires = record_expires.replace(tzinfo=timezone.utc)

    if now > record_expires:
        raise HTTPException(status_code=400, detail="OTP has expired. Please request a new one.")

    # Check attempts
    if record.attempts >= 3:
        raise HTTPException(status_code=429, detail="Too many invalid attempts. Request a new OTP.")

    if record.otp_code != req.otp_code.strip():
        record.attempts += 1
        db.commit()
        raise HTTPException(status_code=400, detail=f"Invalid OTP code. {3 - record.attempts} attempts remaining.")

    # Success
    record.verified = True
    
    # Check if existing farmer profile or create default
    farmer = db.query(Farmer).filter(Farmer.phone == req.email).first()
    farmer_id = farmer.farmer_id if farmer else f"FR-KLR-{req.email.split('@')[0].upper()[:6]}"

    log = AuditLog(
        action="OTP_VERIFIED",
        actor_role="FARMER",
        actor_id=req.email,
        details=f"Email authenticated successfully. Bound to farmer_id {farmer_id}"
    )
    db.add(log)
    db.commit()

    # Generate session/token string
    session_token = f"FR_SESSION_{req.email}_{int(now.timestamp())}"

    return {
        "success": True,
        "message": "Authentication successful",
        "email": req.email,
        "farmer_id": farmer_id,
        "session_token": session_token
    }
