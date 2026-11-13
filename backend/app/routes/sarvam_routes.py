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
    speaker: Optional[str] = "chaitra_kn_conversation"

class VoiceCommandRequest(BaseModel):
    text: Optional[str] = None
    audio_base64: Optional[str] = None
    language_code: Optional[str] = "kn-IN"

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
        speaker=req.speaker
    )

@router.post("/process-command")
def process_voice_command(req: VoiceCommandRequest):
    """
    Universal voice command and harvest parameter extraction endpoint.
    Accepts text or audio; transcribes and extracts entities across the entire app.
    """
    import base64
    transcript = req.text or ""
    if not transcript and req.audio_base64:
        try:
            audio_bytes = base64.b64decode(req.audio_base64)
            stt = SarvamService.transcribe_audio(
                audio_bytes=audio_bytes,
                language_code=req.language_code or "kn-IN"
            )
            transcript = stt.get("transcript", "")
        except Exception:
            pass

    if not transcript:
        is_kn = "kn" in (req.language_code or "kn-IN")
        msg = "ಧ್ವನಿ ಕೇಳಿಸಲಿಲ್ಲ, ದಯವಿಟ್ಟು ಮತ್ತೊಮ್ಮೆ ಮಾತನಾಡಿ." if is_kn else "Could not detect clear speech. Please speak again."
        tts = SarvamService.synthesize_speech(
            text=msg,
            language_code=req.language_code or "kn-IN"
        )
        return {
            "success": False,
            "error": "NO_SPEECH_DETECTED",
            "action": "PROMPT_RETRY",
            "entities": {},
            "fields_updated": [],
            "message_en": "No clear speech detected. Please speak again.",
            "message_kn": "ಧ್ವನಿ ಕೇಳಿಸಲಿಲ್ಲ, ದಯವಿಟ್ಟು ಮತ್ತೊಮ್ಮೆ ಮಾತನಾಡಿ.",
            "spoken_feedback": msg,
            "audio_base64": tts.get("audio_base64", "")
        }

    result = SarvamService.parse_universal_command(
        text=transcript,
        language_code=req.language_code or "kn-IN"
    )

    # Generate synthesized spoken audio response
    tts = SarvamService.synthesize_speech(
        text=result["spoken_feedback"],
        language_code=req.language_code or "kn-IN"
    )
    result["audio_base64"] = tts.get("audio_base64", "")
    return result

@router.post("/process-audio")
async def process_voice_audio(
    file: UploadFile = File(...),
    language_code: str = Form("kn-IN")
):
    """
    Direct audio upload endpoint for hardware recordings.
    Transcribes audio via Sarvam Saaras v4 and extracts entities.
    """
    audio_bytes = await file.read()
    stt = SarvamService.transcribe_audio(
        audio_bytes=audio_bytes,
        filename=file.filename or "recording.wav",
        language_code=language_code
    )
    transcript = (stt.get("transcript") or "").strip()
    if not transcript:
        is_kn = "kn" in language_code
        msg = "ಧ್ವನಿ ಕೇಳಿಸಲಿಲ್ಲ, ದಯವಿಟ್ಟು ಮತ್ತೊಮ್ಮೆ ಮಾತನಾಡಿ." if is_kn else "Could not detect clear speech. Please speak again."
        tts = SarvamService.synthesize_speech(
            text=msg,
            language_code=language_code
        )
        return {
            "success": False,
            "error": "NO_SPEECH_DETECTED",
            "action": "PROMPT_RETRY",
            "entities": {},
            "fields_updated": [],
            "message_en": "No clear speech detected. Please speak again.",
            "message_kn": "ಧ್ವನಿ ಕೇಳಿಸಲಿಲ್ಲ, ದಯವಿಟ್ಟು ಮತ್ತೊಮ್ಮೆ ಮಾತನಾಡಿ.",
            "spoken_feedback": msg,
            "audio_base64": tts.get("audio_base64", ""),
            "raw_transcript": "",
            "detected_language": language_code,
            "language_code": language_code
        }

    detected_lang = "kn-IN" if any("\u0C80" <= c <= "\u0CFF" for c in transcript) else ("en-IN" if any(c.isalpha() for c in transcript) else language_code)

    result = SarvamService.parse_universal_command(
        text=transcript,
        language_code=detected_lang
    )
    result["stt_model"] = stt.get("model", "saaras:v4")
    result["is_live_api"] = stt.get("is_live_api", False)
    result["raw_transcript"] = transcript
    result["detected_language"] = detected_lang
    result["language_code"] = detected_lang

    # Generate synthesized spoken audio response in the exact spoken language
    tts = SarvamService.synthesize_speech(
        text=result["spoken_feedback"],
        language_code=detected_lang
    )
    result["audio_base64"] = tts.get("audio_base64", "")
    return result

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
    from backend.app.models.models import Farmer
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

    farmer_obj = db.query(Farmer).filter(Farmer.farmer_id == req.farmer_id).first()
    default_village = farmer_obj.village if (farmer_obj and farmer_obj.village) else "Local Farm"

    crop = entities.get("crop") or "Tomato"
    variety = entities.get("variety") or "Standard Variety"
    qty_kg = float(entities.get("quantity_kg") or 100.0)
    grade = entities.get("quality_grade") or "Grade A"
    village = entities.get("village") or default_village

    # 3. Register lot in DB
    reg_req = HarvestRegistrationRequest(
        farmer_id=req.farmer_id,
        crop=crop,
        variety=variety,
        quantity_kg=qty_kg,
        quality_grade=grade,
        village=village,
        metadata={
            "source": "sarvam_voice_ai",
            "transcript": transcript,
            "stt_model": stt_info.get("model")
        }
    )
    lot_response = register_harvest(req=reg_req, db=db)

    # 4. Spoken confirmation
    lot_id = lot_response.get("lot_id", "")
    crop_kn = "ಟೊಮೆಟೊ" if "tomat" in crop.lower() else ("ಈರುಳ್ಳಿ" if "onion" in crop.lower() else ("ಆಲೂಗಡ್ಡೆ" if "potato" in crop.lower() else crop))

    if "kn" in (req.language_code or "kn-IN"):
        confirm_text = f"ನಿಮ್ಮ {qty_kg} ಕೆಜಿ {grade} {crop_kn} ಯಶಸ್ವಿಯಾಗಿ ನೋಂದಾಯಿಸಲಾಗಿದೆ. ಲಾಟ್ ಐಡಿ {lot_id}."
    else:
        confirm_text = f"Your {qty_kg} kg {grade} {crop} has been successfully registered. Lot ID is {lot_id}."

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

class MandiVoiceExplanationRequest(BaseModel):
    harvest_id: Optional[str] = None
    crop: Optional[str] = None
    quantity_kg: Optional[float] = None
    language_code: Optional[str] = "kn-IN"

@router.post("/mandi-explanation")
def get_mandi_voice_explanation(
    req: MandiVoiceExplanationRequest,
    db: Session = Depends(get_db)
):
    """
    Synthesizes spoken audio explanation comparing Mandi options across India,
    highlighting transparent costs, net profit, and headline price traps for uneducated farmers.
    """
    from backend.app.services.pan_india_mandi_service import PanIndiaMandiService
    from backend.app.models.models import HarvestLot

    crop = req.crop or "Produce"
    qty = req.quantity_kg or 200.0

    if req.harvest_id:
        lot = db.query(HarvestLot).filter(HarvestLot.lot_id == req.harvest_id).first()
        if lot:
            crop = lot.crop or "Produce"
            qty = lot.quantity_kg or 200.0

    mandis = PanIndiaMandiService.evaluate_mandi_options(
        crop=crop,
        quantity_kg=qty
    )

    spoken_res = SarvamService.generate_mandi_spoken_summary(
        crop=crop,
        quantity_kg=qty,
        mandi_results=mandis,
        language_code=req.language_code or "kn-IN"
    )

    return {
        "success": True,
        "harvest_id": req.harvest_id,
        "crop": crop,
        "quantity_kg": qty,
        "summary_text": spoken_res.get("summary_text"),
        "audio_base64": spoken_res.get("audio_base64"),
        "language_code": req.language_code or "kn-IN",
        "recommended_mandi": spoken_res.get("recommended_mandi"),
        "trap_mandi": spoken_res.get("trap_mandi")
    }

class SchemeGuidanceRequest(BaseModel):
    query: str = Field(..., min_length=1)
    farmer_id: Optional[str] = "FARMER-01"
    language_code: Optional[str] = "kn-IN"

@router.post("/scheme-guidance")
def get_scheme_guidance(req: SchemeGuidanceRequest, db: Session = Depends(get_db)):
    """
    Factual AI Saathi voice & text guidance for government schemes, subsidies,
    FPO formation, and e-NAM trading.
    """
    return SarvamService.answer_scheme_query(
        query_text=req.query,
        farmer_id=req.farmer_id,
        language_code=req.language_code or "kn-IN",
        db=db
    )
