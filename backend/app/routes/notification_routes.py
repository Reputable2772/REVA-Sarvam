from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from pydantic import BaseModel, Field
from typing import List, Optional
from datetime import datetime, timezone
from backend.app.database import get_db
from backend.app.models.models import DevicePushToken, NotificationRecord, AuditLog

router = APIRouter(prefix="/notifications", tags=["Phase 17 — Push Notifications (FCM)"])

class RegisterTokenRequest(BaseModel):
    device_id: str
    user_id: str
    fcm_token: str
    platform: Optional[str] = "android"

class SendAlertRequest(BaseModel):
    recipient_id: str
    title: str
    body: str
    notification_type: Optional[str] = "general" # offer_match, weather_alert, simulated_payment, logistics

@router.post("/register-token")
def register_push_token(
    req: RegisterTokenRequest,
    db: Session = Depends(get_db)
):
    """
    Registers or updates FCM token for a mobile device.
    """
    existing = db.query(DevicePushToken).filter(DevicePushToken.device_id == req.device_id).first()
    now = datetime.now(timezone.utc)

    if existing:
        existing.fcm_token = req.fcm_token
        existing.user_id = req.user_id
        existing.platform = req.platform or "android"
        existing.updated_at = now
    else:
        new_token = DevicePushToken(
            device_id=req.device_id,
            user_id=req.user_id,
            fcm_token=req.fcm_token,
            platform=req.platform or "android",
            updated_at=now
        )
        db.add(new_token)

    db.commit()
    return {
        "success": True,
        "message": f"FCM token registered for device {req.device_id}",
        "user_id": req.user_id
    }

@router.post("/send-alert")
def send_push_alert(
    req: SendAlertRequest,
    db: Session = Depends(get_db)
):
    """
    Dispatches out-of-app push notification alert (FCM / in-app notification center).
    """
    notification = NotificationRecord(
        recipient_id=req.recipient_id,
        title=req.title,
        body=req.body,
        type=req.notification_type or "general",
        read=False,
        created_at=datetime.now(timezone.utc)
    )
    db.add(notification)

    # Check registered devices
    devices = db.query(DevicePushToken).filter(DevicePushToken.user_id == req.recipient_id).all()
    device_count = len(devices)

    db.commit()

    return {
        "success": True,
        "notification_id": notification.id,
        "recipient_id": req.recipient_id,
        "dispatched_to_devices": device_count,
        "mode": "FCM Simulated Broadcast" if device_count > 0 else "Stored to In-App Inbox",
        "title": req.title,
        "body": req.body
    }

@router.get("/inbox/{user_id}")
def get_user_notifications(
    user_id: str,
    db: Session = Depends(get_db)
):
    """
    Fetches notifications for farmer/buyer inbox.
    """
    notifications = db.query(NotificationRecord).filter(
        NotificationRecord.recipient_id == user_id
    ).order_by(NotificationRecord.id.desc()).all()

    return {
        "success": True,
        "user_id": user_id,
        "count": len(notifications),
        "notifications": [
            {
                "id": n.id,
                "title": n.title,
                "body": n.body,
                "type": n.type,
                "read": n.read,
                "created_at": n.created_at.isoformat() if n.created_at else None
            }
            for n in notifications
        ]
    }
