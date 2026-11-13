from fastapi import APIRouter, UploadFile, File, Form, HTTPException, Depends
from pydantic import BaseModel, Field
from typing import Optional, Dict, Any
from sqlalchemy.orm import Session
from backend.app.database import get_db
from backend.app.services.sarvam_service import SarvamService
from backend.app.routes.farmer_routes import register_harvest, HarvestRegistrationRequest

router = APIRouter(prefix="/voice", tags=["Sarvam Voice AI"])

class ExtractRequest(BaseModel):
    text: str = Field(..., min_length=1)

class SpeakRequest(BaseModel):
    text: str = Field(..., min_length=1)
    language_code: Optional[str] = "kn-IN"
    speaker: Optional[str] = "pavithra"

class VoiceRegistrationRequest(BaseModel):
    farmer_id: str
    audio_base64: Optional[str] = None
    language_code: Optional[str] = "kn-IN"
    manual_text: Optional[str] = None

@router.post("/transcribe")
async def transcribe_speech(
    file: Optional[UploadFile] = File(None),
    language_code: str = Form("kn-IN")
):
    """
    Transcribes farmer speech using Sarvam Saaras v4.
    """
    audio_bytes = b""
    filename = "voice.wav"
    if file:
        audio_bytes = await file.read()
        filename = file.filename or "voice.wav"

    result = SarvamService.transcribe_audio(
        audio_bytes=audio_bytes,
        filename=filename,
        language_code=language_code
    )
    return result

@router.post("/extract")
def extract_entities(req: ExtractRequest):
    """
    Extracts structured agricultural entities (crop, quantity, variety, grade)
    from farmer speech transcript using Sarvam AI.
    """
    return SarvamService.extract_harvest_entities(req.text)

@router.post("/speak")
def synthesize_speech(req: SpeakRequest):
    """
    Synthesizes speech in Kannada or English using Sarvam Bulbul v4-flash.
    Returns base64 encoded audio.
    """
    return SarvamService.synthesize_speech(
        text=req.text,
        language_code=req.language_code or "kn-IN",
        speaker=req.speaker or "pavithra"
    )

@router.post("/register-by-voice")
def register_by_voice(
    req: VoiceRegistrationRequest,
    db: Session = Depends(get_db)
):
    """
    Full automated Voice-to-Lot Pipeline:
    1. Transcribe audio or use fallback transcript.
    2. Extract crop, variety, quantity_kg, quality_grade.
    3. Register harvest lot into SQLite database.
    4. Synthesize spoken confirmation in farmer's preferred language.
    """
    import base64
    audio_bytes = b""
    if req.audio_base64:
        try:
            audio_bytes = base64.b64decode(req.audio_base64)
        except Exception:
            pass

    # 1. Transcribe
    if req.manual_text:
        transcript = req.manual_text
        stt_info = {"success": True, "transcript": transcript, "model": "manual_fallback"}
    else:
        stt_info = SarvamService.transcribe_audio(
            audio_bytes=audio_bytes,
            language_code=req.language_code or "kn-IN"
        )
        transcript = stt_info.get("transcript", "")

    # 2. Extract
    extracted_res = SarvamService.extract_harvest_entities(transcript)
    entities = extracted_res.get("extracted", {})

    # 3. Register lot in DB
    reg_req = HarvestRegistrationRequest(
        farmer_id=req.farmer_id,
        crop=entities.get("crop", "Tomato"),
        variety=entities.get("variety", "Hybrid Red"),
        quantity_kg=float(entities.get("quantity_kg", 500.0)),
        quality_grade=entities.get("quality_grade", "Grade A"),
        village=entities.get("village", "Mulbagal"),
        metadata={
            "source": "sarvam_voice_ai",
            "transcript": transcript,
            "stt_model": stt_info.get("model")
        }
    )
    lot_response = register_harvest(req=reg_req, db=db)

    # 4. Spoken confirmation
    kg = entities.get("quantity_kg", 500.0)
    grade = entities.get("quality_grade", "Grade A")
    lot_id = lot_response.get("lot_id", "")
    
    if "kn" in (req.language_code or "kn-IN"):
        confirm_text = f"ನಿಮ್ಮ {kg} ಕೆಜಿ {grade} ಟೊಮೆಟೊ ಯಶಸ್ವಿಯಾಗಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ. ಲಾಟ್ ಐಡಿ {lot_id}."
    else:
        confirm_text = f"Your {kg} kg {grade} tomato has been successfully registered. Lot ID is {lot_id}."

    tts_info = SarvamService.synthesize_speech(
        text=confirm_text,
        language_code=req.language_code or "kn-IN"
    )

    return {
        "success": True,
        "transcript": transcript,
        "extracted_entities": entities,
        "lot_registration": lot_response,
        "spoken_confirmation": {
            "text": confirm_text,
            "audio_base64": tts_info.get("audio_base64"),
            "model": tts_info.get("model")
        }
    }
