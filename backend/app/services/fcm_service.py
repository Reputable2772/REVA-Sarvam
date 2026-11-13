import os
import json
import logging
from typing import List, Dict, Any, Optional
from sqlalchemy.orm import Session

logger = logging.getLogger(__name__)

# Guarded imports for Firebase Admin SDK
try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    FIREBASE_AVAILABLE = True
except ImportError:
    FIREBASE_AVAILABLE = False
    firebase_admin = None
    credentials = None
    messaging = None

from backend.app.config import settings

class FCMService:
    _app: Optional[Any] = None
    _init_attempted: bool = False

    @classmethod
    def get_google_services_metadata(cls) -> Dict[str, Any]:
        """
        Parses google-services.json to provide client app and project configuration.
        """
        paths = ["./google-services.json", "./android/farmroute-saathi/app/google-services.json"]
        for p in paths:
            if os.path.exists(p):
                try:
                    with open(p, "r", encoding="utf-8") as f:
                        data = json.load(f)
                    p_info = data.get("project_info", {})
                    client_list = data.get("client", [])
                    client_pkg = ""
                    if client_list:
                        client_pkg = client_list[0].get("client_info", {}).get("android_client_info", {}).get("package_name", "")
                    return {
                        "present": True,
                        "file_path": p,
                        "project_id": p_info.get("project_id", ""),
                        "project_number": p_info.get("project_number", ""),
                        "storage_bucket": p_info.get("storage_bucket", ""),
                        "package_name": client_pkg
                    }
                except Exception as e:
                    logger.warning(f"Error reading {p}: {e}")
        return {"present": False}

    @classmethod
    def get_firebase_app(cls) -> Optional[Any]:
        if cls._app is not None:
            return cls._app

        if cls._init_attempted:
            return cls._app

        cls._init_attempted = True

        if not FIREBASE_AVAILABLE:
            logger.info("firebase-admin package not available; running in simulated mode.")
            return None

        # Check already initialized default app
        try:
            cls._app = firebase_admin.get_app()
            return cls._app
        except ValueError:
            pass

        cred = None
        # 1. Environment JSON string
        json_env = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
        if json_env:
            try:
                cert_dict = json.loads(json_env)
                cred = credentials.Certificate(cert_dict)
            except Exception as e:
                logger.warning(f"Failed to parse FIREBASE_SERVICE_ACCOUNT_JSON: {e}")

        # 2. File path credential
        if not cred:
            cred_path = getattr(settings, "FIREBASE_CREDENTIALS_PATH", "./serviceAccountKey.json")
            if cred_path and os.path.exists(cred_path):
                try:
                    cred = credentials.Certificate(cred_path)
                except Exception as e:
                    logger.warning(f"Failed to load Firebase credentials from {cred_path}: {e}")

        project_id = getattr(settings, "FIREBASE_PROJECT_ID", "")
        if not project_id:
            meta = cls.get_google_services_metadata()
            if meta.get("project_id"):
                project_id = meta["project_id"]

        if cred:
            try:
                options = {}
                if project_id:
                    options["projectId"] = project_id
                cls._app = firebase_admin.initialize_app(cred, options=options or None)
                logger.info("Firebase Admin SDK initialized successfully.")
                return cls._app
            except Exception as e:
                logger.error(f"Error initializing Firebase app: {e}")
                return None

        logger.info("No Firebase service account credentials found. FCM will operate in simulated mode.")
        return None

    @classmethod
    def is_live_fcm_available(cls) -> bool:
        return cls.get_firebase_app() is not None

    @classmethod
    def send_push_notification(
        cls,
        tokens: List[str],
        title: str,
        body: str,
        notification_type: str = "general",
        metadata: Optional[Dict[str, Any]] = None,
        channel_id: str = "farmroute_alerts"
    ) -> Dict[str, Any]:
        """
        Dispatches push notification via FCM to a list of device tokens.
        If Firebase is unconfigured, operates in deterministic simulated mode.
        """
        valid_tokens = [t.strip() for t in tokens if t and isinstance(t, str) and t.strip()]
        if not valid_tokens:
            return {
                "success": True,
                "mode": "No Registered Devices",
                "dispatched_count": 0,
                "success_count": 0,
                "failure_count": 0,
                "failed_tokens": []
            }

        app = cls.get_firebase_app()
        if not app or not FIREBASE_AVAILABLE:
            return {
                "success": True,
                "mode": "FCM Simulated Broadcast",
                "dispatched_count": len(valid_tokens),
                "success_count": len(valid_tokens),
                "failure_count": 0,
                "failed_tokens": [],
                "note": "Alert stored in database and simulated to active client tokens."
            }

        fcm_data = {"type": str(notification_type), "channel_id": str(channel_id)}
        if metadata:
            for k, v in metadata.items():
                if isinstance(v, (dict, list)):
                    fcm_data[k] = json.dumps(v)
                else:
                    fcm_data[k] = str(v)

        notification = messaging.Notification(
            title=title,
            body=body
        )

        android_config = messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(
                channel_id=channel_id,
                sound="default"
            )
        )

        message = messaging.MulticastMessage(
            tokens=valid_tokens,
            notification=notification,
            data=fcm_data,
            android=android_config
        )

        try:
            response = messaging.send_each_for_multicast(message, app=app)
            failed_tokens = []
            for idx, resp in enumerate(response.responses):
                if not resp.success:
                    failed_tokens.append(valid_tokens[idx])
                    logger.warning(f"FCM delivery failed for token {valid_tokens[idx][:10]}...: {resp.exception}")

            return {
                "success": response.success_count > 0,
                "mode": "FCM Live Dispatch",
                "dispatched_count": len(valid_tokens),
                "success_count": response.success_count,
                "failure_count": response.failure_count,
                "failed_tokens": failed_tokens
            }
        except Exception as e:
            logger.error(f"Failed to dispatch FCM multicast: {e}")
            return {
                "success": False,
                "mode": "FCM Live Dispatch Error",
                "error": str(e),
                "dispatched_count": len(valid_tokens),
                "success_count": 0,
                "failure_count": len(valid_tokens),
                "failed_tokens": valid_tokens
            }

    @classmethod
    def dispatch_to_user(
        cls,
        recipient_id: str,
        title: str,
        body: str,
        notification_type: str = "general",
        metadata: Optional[Dict[str, Any]] = None,
        channel_id: str = "farmroute_alerts",
        db: Optional[Session] = None
    ) -> Dict[str, Any]:
        """
        Dispatches push notification directly to all registered devices for recipient_id.
        Optionally prunes stale/invalid tokens from the database.
        """
        if not db:
            return cls.send_push_notification(
                tokens=[],
                title=title,
                body=body,
                notification_type=notification_type,
                metadata=metadata,
                channel_id=channel_id
            )

        from backend.app.models.models import DevicePushToken
        devices = db.query(DevicePushToken).filter(DevicePushToken.user_id == recipient_id).all()
        tokens = [d.fcm_token for d in devices if d.fcm_token]

        res = cls.send_push_notification(
            tokens=tokens,
            title=title,
            body=body,
            notification_type=notification_type,
            metadata=metadata,
            channel_id=channel_id
        )

        if res.get("failed_tokens") and res.get("mode") == "FCM Live Dispatch":
            try:
                for ft in res["failed_tokens"]:
                    db.query(DevicePushToken).filter(DevicePushToken.fcm_token == ft).delete()
                db.commit()
            except Exception as prune_err:
                logger.warning(f"Failed to prune invalid FCM tokens: {prune_err}")

        return res
