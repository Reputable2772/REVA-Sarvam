import os
import json
import base64
import re
from datetime import datetime, timedelta
import urllib.request
import urllib.error
from typing import Dict, Any, Optional, List
from backend.app.config import settings

class SarvamService:
    """
    Unified Sarvam AI Voice Service
    Handles:
    - Multi-Key API Key Rotation pool across SARVAM_API_KEYS & SARVAM_API_KEY
    - Automatic cycling on 401/403/429/5xx errors
    - OpenAI-Compatible Fallback for LLM entity extraction & Whisper STT
    - Speech-to-Text (STT) via Saaras v4
    - Text-to-Speech (TTS) via Bulbul v4-flash
    - Structured Agricultural Entity Extraction via Sarvam-105b or robust NLP heuristics
    """

    STT_URL = "https://api.sarvam.ai/speech-to-text"
    TTS_URL = "https://api.sarvam.ai/text-to-speech"
    COMPLETIONS_URL = "https://api.sarvam.ai/v1/chat/completions"

    _key_index: int = 0

    @classmethod
    def get_api_keys(cls) -> list:
        keys = []
        if "SARVAM_API_KEYS" in os.environ:
            raw_keys = os.environ.get("SARVAM_API_KEYS", "")
        else:
            raw_keys = getattr(settings, "SARVAM_API_KEYS", "") or ""
        if raw_keys:
            keys.extend([k.strip() for k in raw_keys.split(",") if k.strip()])

        if "SARVAM_API_KEY" in os.environ:
            single_key = os.environ.get("SARVAM_API_KEY", "")
        elif "SARVAM_API_KEYS" in os.environ:
            single_key = ""
        else:
            single_key = getattr(settings, "SARVAM_API_KEY", "") or ""
        single_key = single_key.strip()
        if single_key and single_key not in keys:
            keys.append(single_key)
        return keys

    @classmethod
    def get_api_key(cls) -> str:
        keys = cls.get_api_keys()
        if not keys:
            return ""
        cls._key_index = cls._key_index % len(keys)
        return keys[cls._key_index]

    @classmethod
    def rotate_api_key(cls) -> str:
        keys = cls.get_api_keys()
        if not keys:
            return ""
        cls._key_index = (cls._key_index + 1) % len(keys)
        return keys[cls._key_index]

    @classmethod
    def get_openai_fallback_config(cls) -> Dict[str, str]:
        return {
            "api_key": os.getenv("OPENAI_COMPATIBLE_API_KEY", getattr(settings, "OPENAI_COMPATIBLE_API_KEY", "") or ""),
            "base_url": os.getenv("OPENAI_COMPATIBLE_BASE_URL", getattr(settings, "OPENAI_COMPATIBLE_BASE_URL", "https://api.openai.com/v1") or "https://api.openai.com/v1").rstrip("/"),
            "model": os.getenv("OPENAI_COMPATIBLE_MODEL", getattr(settings, "OPENAI_COMPATIBLE_MODEL", "gpt-3.5-turbo") or "gpt-3.5-turbo")
        }

    @classmethod
    def transcribe_audio(
        cls,
        audio_bytes: bytes,
        filename: str = "audio.wav",
        language_code: str = "kn-IN"
    ) -> Dict[str, Any]:
        """
        Transcribes speech using Sarvam Saaras v4 model with multi-key cycling.
        Falls back to OpenAI-compatible Whisper if configured, or realistic simulated speech if both fail.
        """
        keys = cls.get_api_keys()
        if keys:
            boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW"
            body = bytearray()
            body.extend(f"--{boundary}\r\n".encode())
            body.extend(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode())
            body.extend(b"Content-Type: audio/wav\r\n\r\n")
            body.extend(audio_bytes)
            body.extend(b"\r\n")
            body.extend(f"--{boundary}\r\n".encode())
            body.extend(b'Content-Disposition: form-data; name="model"\r\n\r\n')
            body.extend(b"saaras:v4\r\n")
            body.extend(f"--{boundary}\r\n".encode())
            body.extend(b'Content-Disposition: form-data; name="language_code"\r\n\r\n')
            body.extend(language_code.encode() + b"\r\n")
            body.extend(f"--{boundary}--\r\n".encode())
            data_bytes = bytes(body)

            for _ in range(len(keys)):
                current_key = cls.get_api_key()
                try:
                    req = urllib.request.Request(
                        cls.STT_URL,
                        data=data_bytes,
                        headers={
                            "api-subscription-key": current_key,
                            "Content-Type": f"multipart/form-data; boundary={boundary}"
                        },
                        method="POST"
                    )
                    with urllib.request.urlopen(req, timeout=10) as response:
                        res_data = json.loads(response.read().decode())
                        return {
                            "success": True,
                            "transcript": res_data.get("transcript", ""),
                            "language_code": res_data.get("language_code", language_code),
                            "model": "saaras:v4",
                            "is_live_api": True
                        }
                except (urllib.error.HTTPError, urllib.error.URLError, Exception):
                    cls.rotate_api_key()

        # Fallback to OpenAI-compatible Whisper if available
        openai_cfg = cls.get_openai_fallback_config()
        if openai_cfg.get("api_key"):
            try:
                whisper_url = f"{openai_cfg['base_url']}/audio/transcriptions"
                w_boundary = "----WebKitFormBoundaryWhisper7MA4YW"
                w_body = bytearray()
                w_body.extend(f"--{w_boundary}\r\n".encode())
                w_body.extend(f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode())
                w_body.extend(b"Content-Type: audio/wav\r\n\r\n")
                w_body.extend(audio_bytes)
                w_body.extend(b"\r\n")
                w_body.extend(f"--{w_boundary}\r\n".encode())
                w_body.extend(b'Content-Disposition: form-data; name="model"\r\n\r\n')
                w_body.extend(b"whisper-1\r\n")
                w_body.extend(f"--{w_boundary}--\r\n".encode())

                w_req = urllib.request.Request(
                    whisper_url,
                    data=bytes(w_body),
                    headers={
                        "Authorization": f"Bearer {openai_cfg['api_key']}",
                        "Content-Type": f"multipart/form-data; boundary={w_boundary}"
                    },
                    method="POST"
                )
                with urllib.request.urlopen(w_req, timeout=12) as response:
                    w_res = json.loads(response.read().decode())
                    return {
                        "success": True,
                        "transcript": w_res.get("text", ""),
                        "language_code": language_code,
                        "model": "whisper-1 (openai-fallback)",
                        "is_live_api": True
                    }
            except Exception:
                pass

        # Intelligent Fallback Simulation (Kannada / English)
        sample_transcript = (
            "ನನ್ನ ಬಳಿ 800 ಕೆಜಿ ಗ್ರೇಡ್ ಎ ಟೊಮೆಟೊ ಇದೆ, ಮುಳುಬಾಗಿಲು ಗ್ರಾಮದಿಂದ ಮಾರಾಟ ಮಾಡಬೇಕು."
            if "kn" in language_code
            else "I have 800 kg Grade A tomato ready for sale from Mulbagal."
        )
        return {
            "success": True,
            "transcript": sample_transcript,
            "language_code": language_code,
            "model": "saaras:v4 (simulated)",
            "is_live_api": False
        }

    @classmethod
    def synthesize_speech(
        cls,
        text: str,
        language_code: str = "kn-IN",
        speaker: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Synthesizes text to speech using Sarvam Bulbul v4-flash model with multi-key cycling.
        Returns base64 encoded audio.
        """
        if not speaker or speaker in ("pavithra", "arvind"):
            is_kn = "kn" in (language_code or "").lower()
            speaker = "chaitra_kn_conversation" if is_kn else "aparna_en_companion"

        keys = cls.get_api_keys()
        if keys:
            payload = {
                "inputs": [text],
                "target_language_code": language_code,
                "speaker": speaker,
                "model": "bulbul:v4-flash"
            }
            data_payload = json.dumps(payload).encode()
            for _ in range(len(keys)):
                current_key = cls.get_api_key()
                try:
                    req = urllib.request.Request(
                        cls.TTS_URL,
                        data=data_payload,
                        headers={
                            "api-subscription-key": current_key,
                            "Content-Type": "application/json"
                        },
                        method="POST"
                    )
                    with urllib.request.urlopen(req, timeout=10) as response:
                        res_data = json.loads(response.read().decode())
                        audios = res_data.get("audios", [])
                        audio_b64 = audios[0] if audios else ""
                        return {
                            "success": True,
                            "audio_base64": audio_b64,
                            "language_code": language_code,
                            "speaker": speaker,
                            "model": "bulbul:v4-flash",
                            "is_live_api": True
                        }
                except (urllib.error.HTTPError, urllib.error.URLError, Exception):
                    cls.rotate_api_key()

        # Generate minimal valid WAV header + silence as fallback base64
        wav_header = bytes([
            0x52, 0x49, 0x46, 0x46,  # "RIFF"
            0x24, 0x00, 0x00, 0x00,  # size
            0x57, 0x41, 0x56, 0x45,  # "WAVE"
            0x66, 0x6D, 0x74, 0x20,  # "fmt "
            0x10, 0x00, 0x00, 0x00,  # subchunk1 size (16)
            0x01, 0x00,              # PCM
            0x01, 0x00,              # Mono (1 channel)
            0x44, 0xAC, 0x00, 0x00,  # 44100 sample rate
            0x88, 0x58, 0x01, 0x00,  # byte rate
            0x02, 0x00,              # block align
            0x10, 0x00,              # bits per sample (16)
            0x64, 0x61, 0x74, 0x61,  # "data"
            0x00, 0x00, 0x00, 0x00   # 0 bytes data
        ])
        fallback_b64 = base64.b64encode(wav_header).decode()

        return {
            "success": True,
            "audio_base64": fallback_b64,
            "language_code": language_code,
            "speaker": speaker,
            "model": "bulbul:v4-flash (simulated)",
            "is_live_api": False
        }

    @classmethod
    def _extract_heuristic(cls, text: str) -> Dict[str, Any]:
        """
        Robust agricultural NLP heuristic parser.
        Default entities and regular expression matchers for Kannada & English.
        """
        now = datetime.now()
        today_str = now.strftime("%Y-%m-%d")
        yesterday_str = (now - timedelta(days=1)).strftime("%Y-%m-%d")
        tomorrow_str = (now + timedelta(days=1)).strftime("%Y-%m-%d")

        crop = "Tomato"
        variety = "Standard"
        quantity_kg = 200.0
        quality_grade = "Grade A"
        village = None
        harvest_date = today_str
        harvest_time = "07:00 AM"
        expected_date_time = f"{tomorrow_str} 09:00 AM"
        sell_immediately = True
        fields_detected = []

        # Regex for quantity: prioritize explicit weight unit
        qty_unit_match = re.search(r"(\d+(?:\.\d+)?)\s*(kg|kgs|ಕೆಜಿ|quintal|ton|ಕ್ವಿಂಟಾಲ್|ಟನ್)", text, re.IGNORECASE)
        if qty_unit_match:
            val = float(qty_unit_match.group(1))
            unit = qty_unit_match.group(2).lower()
            if "quintal" in unit or "ಕ್ವಿಂಟಾಲ್" in unit:
                quantity_kg = val * 100.0
            elif "ton" in unit or "ಟನ್" in unit:
                quantity_kg = val * 1000.0
            else:
                quantity_kg = val
            fields_detected.append("quantity_kg")
        else:
            # Standalone number not followed by time units
            qty_fallback = re.search(r"\b(\d{2,5}(?:\.\d+)?)\b(?!\s*(?:am|pm|ಗಂಟೆ|days|hours))", text, re.IGNORECASE)
            if qty_fallback:
                quantity_kg = float(qty_fallback.group(1))
                fields_detected.append("quantity_kg")

        # Grade detection
        if re.search(r"grade\s*b|ಗ್ರೇಡ್\s*ಬಿ", text, re.IGNORECASE):
            quality_grade = "Grade B"
            fields_detected.append("quality_grade")
        elif re.search(r"grade\s*c|ಗ್ರೇಡ್\s*ಸಿ", text, re.IGNORECASE):
            quality_grade = "Grade C"
            fields_detected.append("quality_grade")
        elif re.search(r"grade\s*a|ಗ್ರೇಡ್\s*ಎ", text, re.IGNORECASE):
            quality_grade = "Grade A"
            fields_detected.append("quality_grade")

        # Crop detection
        if re.search(r"onion|ಈರುಳ್ಳಿ", text, re.IGNORECASE):
            crop = "Onion"
            variety = "Nasik Red"
            fields_detected.append("crop")
        elif re.search(r"potato|ಆಲೂಗಡ್ಡೆ", text, re.IGNORECASE):
            crop = "Potato"
            variety = "Jyoti"
            fields_detected.append("crop")
        elif re.search(r"cabbage|ಎಲೆಕೋಸು", text, re.IGNORECASE):
            crop = "Cabbage"
            variety = "Green Express"
            fields_detected.append("crop")
        elif re.search(r"carrot|ಕ್ಯಾರೆಟ್", text, re.IGNORECASE):
            crop = "Carrot"
            variety = "Pusa Rudhira"
            fields_detected.append("crop")
        elif re.search(r"chilli|ಮೆಣಸಿನಕಾಯಿ", text, re.IGNORECASE):
            crop = "Chilli"
            variety = "Guntur Hot"
            fields_detected.append("crop")
        elif re.search(r"tomato|ಟೊಮೆಟೊ", text, re.IGNORECASE):
            crop = "Tomato"
            variety = "Hybrid Red"
            fields_detected.append("crop")

        # Harvest Date detection
        if re.search(r"today|ಇಂದು|ಈ ದಿನ", text, re.IGNORECASE):
            harvest_date = today_str
            fields_detected.append("harvest_date")
        elif re.search(r"yesterday|ನಿನ್ನೆ", text, re.IGNORECASE):
            harvest_date = yesterday_str
            fields_detected.append("harvest_date")
        elif re.search(r"tomorrow|ನಾಳೆ", text, re.IGNORECASE):
            harvest_date = tomorrow_str
            fields_detected.append("harvest_date")

        date_match = re.search(r"\b(202\d-\d{2}-\d{2})\b", text)
        if date_match:
            harvest_date = date_match.group(1)
            fields_detected.append("harvest_date")

        # Harvest Time detection
        time_match = re.search(r"(\d{1,2})(?::(\d{2}))?\s*(am|pm|ಗಂಟೆ)", text, re.IGNORECASE)
        if time_match:
            hour = int(time_match.group(1))
            minute = time_match.group(2) or "00"
            ampm = (time_match.group(3) or "").upper()
            if "ಗಂಟೆ" in ampm:
                ampm = "AM" if ("ಬೆಳಿಗ್ಗೆ" in text or hour < 12) else "PM"
            harvest_time = f"{hour:02d}:{minute} {ampm}"
            fields_detected.append("harvest_time")
        elif re.search(r"morning|ಬೆಳಿಗ್ಗೆ", text, re.IGNORECASE):
            harvest_time = "07:00 AM"
            fields_detected.append("harvest_time")
        elif re.search(r"afternoon|ಮಧ್ಯಾಹ್ನ", text, re.IGNORECASE):
            harvest_time = "01:30 PM"
            fields_detected.append("harvest_time")
        elif re.search(r"evening|ಸಂಜೆ", text, re.IGNORECASE):
            harvest_time = "05:30 PM"
            fields_detected.append("harvest_time")

        # Expected Date/Time detection
        if re.search(r"tomorrow morning|ನಾಳೆ ಬೆಳಿಗ್ಗೆ", text, re.IGNORECASE):
            expected_date_time = f"{tomorrow_str} 09:00 AM"
            fields_detected.append("expected_date_time")
        elif re.search(r"tomorrow evening|ನಾಳೆ ಸಂಜೆ", text, re.IGNORECASE):
            expected_date_time = f"{tomorrow_str} 05:00 PM"
            fields_detected.append("expected_date_time")
        elif re.search(r"today evening|ಇಂದು ಸಂಜೆ", text, re.IGNORECASE):
            expected_date_time = f"{today_str} 06:00 PM"
            fields_detected.append("expected_date_time")

        # Sell immediately vs Cold storage
        if re.search(r"cold storage|store|ಶೀತಲ|ಇಲ್ಲ|hold|later", text, re.IGNORECASE):
            sell_immediately = False
            fields_detected.append("sell_immediately")
        elif re.search(r"immediately|now|direct|ಮಾರಾಟ|ಈಗಲೇ|ಹೌದು|yes|today", text, re.IGNORECASE):
            sell_immediately = True
            fields_detected.append("sell_immediately")

        # Village detection
        if re.search(r"bangarpet|ಬಂಗಾರಪೇಟೆ", text, re.IGNORECASE):
            village = "Bangarpet"
            fields_detected.append("village")
        elif re.search(r"mulbagal|ಮುಳಬಾಗಿಲು", text, re.IGNORECASE):
            village = "Mulbagal"
            fields_detected.append("village")
        elif re.search(r"malur|ಮಾಲೂರು", text, re.IGNORECASE):
            village = "Malur"
            fields_detected.append("village")
        elif re.search(r"kolar|ಕೋಲಾರ", text, re.IGNORECASE):
            village = "Kolar"
            fields_detected.append("village")
        elif re.search(r"srinivaspur|ಶ್ರೀನಿವಾಸಪುರ", text, re.IGNORECASE):
            village = "Srinivaspur"
            fields_detected.append("village")
        elif re.search(r"chintamani|ಚಿಂತಾಮಣಿ", text, re.IGNORECASE):
            village = "Chintamani"
            fields_detected.append("village")
        elif re.search(r"chikkaballapur|ಚಿಕ್ಕಬಳ್ಳಾಪುರ", text, re.IGNORECASE):
            village = "Chikkaballapur"
            fields_detected.append("village")
        else:
            loc_match = re.search(r"\b(?:in|at|from|village)\s+([A-Za-z]+)", text, re.IGNORECASE)
            if loc_match:
                cand = loc_match.group(1).title()
                if cand.lower() not in ["kg", "kgs", "farm", "grade", "hybrid", "today", "tomorrow"]:
                    village = cand
                    fields_detected.append("village")
            else:
                kn_loc_match = re.search(r"([A-Za-z\u0C80-\u0CFF]{3,20})(?:ನಲ್ಲಿ|ದಲ್ಲಿ|ಯಿಂದ|ದಿಂದ|ಹತ್ತಿರ)", text)
                if kn_loc_match:
                    village = kn_loc_match.group(1)
                    fields_detected.append("village")

        return {
            "crop": crop,
            "variety": variety,
            "quantity_kg": quantity_kg,
            "quality_grade": quality_grade,
            "harvest_date": harvest_date,
            "harvest_time": harvest_time,
            "expected_date_time": expected_date_time,
            "sell_immediately": sell_immediately,
            "village": village,
            "fields_detected": fields_detected
        }

    @classmethod
    def extract_harvest_entities(cls, text: str) -> Dict[str, Any]:
        """
        Extracts structured crop registration fields from unstructured voice transcript.
        Fields: crop, variety, quantity_kg, quality_grade, village, harvest_date, harvest_time, expected_date_time, sell_immediately.
        Rotates across Sarvam API keys, falls back to OpenAI-compatible LLM, and finally to robust heuristics.
        """
        keys = cls.get_api_keys()
        parsed = None
        system_prompt = (
            "You are an agricultural AI parser. Extract harvest registration details from the farmer statement. "
            "Return strict JSON with keys: crop, variety, quantity_kg, quality_grade, village, "
            "harvest_date (YYYY-MM-DD), harvest_time (e.g. 07:00 AM), expected_date_time, sell_immediately (boolean). "
            "Always use standard English names for crops (e.g. Tomato, Onion, Potato, Cabbage, Carrot, Chilli)."
        )

        if keys:
            payload = {
                "model": "sarvam-105b",
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": text}
                ],
                "temperature": 0.1
            }
            data_payload = json.dumps(payload).encode()
            for _ in range(len(keys)):
                current_key = cls.get_api_key()
                try:
                    req = urllib.request.Request(
                        cls.COMPLETIONS_URL,
                        data=data_payload,
                        headers={
                            "api-subscription-key": current_key,
                            "Content-Type": "application/json"
                        },
                        method="POST"
                    )
                    with urllib.request.urlopen(req, timeout=10) as response:
                        res_data = json.loads(response.read().decode())
                        content = res_data["choices"][0]["message"]["content"]
                        parsed = json.loads(re.search(r"\{.*\}", content, re.DOTALL).group(0))
                        break
                except (urllib.error.HTTPError, urllib.error.URLError, Exception):
                    cls.rotate_api_key()

        # Fallback to OpenAI-compatible LLM
        if not parsed:
            openai_cfg = cls.get_openai_fallback_config()
            if openai_cfg.get("api_key"):
                try:
                    llm_url = f"{openai_cfg['base_url']}/chat/completions"
                    payload = {
                        "model": openai_cfg.get("model", "gpt-3.5-turbo"),
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": text}
                        ],
                        "temperature": 0.1
                    }
                    req = urllib.request.Request(
                        llm_url,
                        data=json.dumps(payload).encode(),
                        headers={
                            "Authorization": f"Bearer {openai_cfg['api_key']}",
                            "Content-Type": "application/json"
                        },
                        method="POST"
                    )
                    with urllib.request.urlopen(req, timeout=12) as response:
                        res_data = json.loads(response.read().decode())
                        content = res_data["choices"][0]["message"]["content"]
                        parsed = json.loads(re.search(r"\{.*\}", content, re.DOTALL).group(0))
                except Exception:
                    pass

        heuristic = cls._extract_heuristic(text)

        if parsed:
            # Map Kannada keys if LLM translated them
            key_mappings = {
                "ಬೆಳೆ": "crop",
                "ಪ್ರಕಾರ": "variety",
                "ತಳಿ": "variety",
                "ಪ್ರಮಾಣ": "quantity_kg",
                "ತೂಕ": "quantity_kg",
                "ಗುಣಮಟ್ಟ": "quality_grade",
                "ಗ್ರೇಡ್": "quality_grade",
                "ಗ್ರಾಮ": "village",
                "ಊರು": "village",
                "ಸ್ಥಳ": "village",
                "ಕೊಯ್ಲು_ದಿನಾಂಕ": "harvest_date",
                "ಕೊಯ್ಲು_ಸಮಯ": "harvest_time",
                "ಮಾರಾಟ_ಸಮಯ": "expected_date_time",
                "ತಕ್ಷಣ_ಮಾರಾಟ": "sell_immediately"
            }
            normalized = {}
            for k, v in parsed.items():
                target_k = key_mappings.get(k, k)
                normalized[target_k] = v

            # Normalize crop name
            crop_val = normalized.get("crop")
            if crop_val:
                crop_lower = str(crop_val).strip().lower()
                if "tomat" in crop_lower or "ಟೊಮೆಟೊ" in crop_lower:
                    normalized["crop"] = "Tomato"
                elif "onion" in crop_lower or "ಈರುಳ್ಳಿ" in crop_lower:
                    normalized["crop"] = "Onion"
                elif "potato" in crop_lower or "ಆಲೂ" in crop_lower:
                    normalized["crop"] = "Potato"
                elif "cabbage" in crop_lower or "ಕೋಸು" in crop_lower:
                    normalized["crop"] = "Cabbage"
                elif "carrot" in crop_lower or "ಕ್ಯಾರೆಟ್" in crop_lower:
                    normalized["crop"] = "Carrot"
                elif "chilli" in crop_lower or "ಮೆಣಸಿನ" in crop_lower:
                    normalized["crop"] = "Chilli"
                else:
                    normalized["crop"] = str(crop_val).title()

            # Normalize quantity
            if "quantity_kg" in normalized and normalized["quantity_kg"] is not None:
                try:
                    normalized["quantity_kg"] = float(normalized["quantity_kg"])
                except (ValueError, TypeError):
                    pass

            # Normalize quality grade to standard Grade A / Grade B / Grade C
            if "quality_grade" in normalized and normalized["quality_grade"]:
                qg = str(normalized["quality_grade"]).strip().upper()
                if qg in ["A", "GRADE A", "GRADE-A", "ಗ್ರೇಡ್ ಎ", "ಗ್ರೇಡ್ A"]:
                    normalized["quality_grade"] = "Grade A"
                elif qg in ["B", "GRADE B", "GRADE-B", "ಗ್ರೇಡ್ ಬಿ", "ಗ್ರೇಡ್ B"]:
                    normalized["quality_grade"] = "Grade B"
                elif qg in ["C", "GRADE C", "GRADE-C", "ಗ್ರೇಡ್ ಸಿ", "ಗ್ರೇಡ್ C"]:
                    normalized["quality_grade"] = "Grade C"

            # Fill in any missing or null fields from heuristic
            for key, h_val in heuristic.items():
                if key not in normalized or normalized[key] is None or normalized[key] == "":
                    normalized[key] = h_val

            return {
                "success": True,
                "extracted": normalized,
                "model": "sarvam-105b",
                "is_live_api": True
            }

        return {
            "success": True,
            "extracted": heuristic,
            "model": "sarvam_heuristic_parser",
            "is_live_api": False
        }

    @classmethod
    def parse_universal_command(cls, text: str, language_code: str = "kn-IN") -> Dict[str, Any]:
        """
        Universal app-wide voice command interpreter.
        Enables speech understanding throughout the entire application.
        """
        is_kn = "kn" in language_code.lower() or any("\u0C80" <= c <= "\u0CFF" for c in text)
        entities_result = cls.extract_harvest_entities(text)
        entities = entities_result.get("extracted", {})

        action = "UPDATE_HARVEST"
        if re.search(r"rate|price|mandi|ದರ|ಬೆಲೆ|ಮಾರುಕಟ್ಟೆ", text, re.IGNORECASE):
            action = "NAVIGATE_MARKETPLACE"
            msg_en = "Navigating to Live APMC & Marketplace Rates."
            msg_kn = "ಲೈವ್ ಮಾರುಕಟ್ಟೆ ಮತ್ತು ಎಪಿಎಂಸಿ ದರಗಳಿಗೆ ನ್ಯಾವಿಗೇಟ್ ಮಾಡಲಾಗುತ್ತಿದೆ."
        elif re.search(r"weather|rain|forecast|ಮಳೆ|ಹವಾಮಾನ", text, re.IGNORECASE):
            action = "NAVIGATE_WEATHER"
            msg_en = "Opening 3-Day Micro-Climate Weather Advisory."
            msg_kn = "3-ದಿನಗಳ ಹವಾಮಾನ ಮುನ್ಸೂಚನೆಯನ್ನು ತೆರೆಯಲಾಗುತ್ತಿದೆ."
        elif re.search(r"cold storage|warehouse|ಶೀತಲ", text, re.IGNORECASE):
            action = "NAVIGATE_COLD_STORAGE"
            msg_en = "Opening Cold Storage Facility Discovery."
            msg_kn = "ಶೀತಲ ಶೇಖರಣಾ ಸೌಲಭ್ಯಗಳ ವಿಭಾಗವನ್ನು ತೆರೆಯಲಾಗುತ್ತಿದೆ."
        elif re.search(r"scheme|benefit|subsidy|grant|insurance|ಯೋಜನೆ|ಸಬ್ಸಿಡಿ|ಅನುದಾನ|ಸೌಲಭ್ಯ|ವಿಮೆ|fruits|pm kisan|raitha siri|enam", text, re.IGNORECASE):
            action = "NAVIGATE_SCHEMES"
            msg_en = "Opening AI Government Schemes & Opportunities Intelligence."
            msg_kn = "ಸರ್ಕಾರಿ ಯೋಜನೆಗಳು ಮತ್ತು ಸೌಲಭ್ಯಗಳ ವಿಭಾಗವನ್ನು ತೆರೆಯಲಾಗುತ್ತಿದೆ."
        elif re.search(r"reset|ಮರುಹೊಂದಿಸಿ", text, re.IGNORECASE):
            action = "DEMO_RESET"
            msg_en = "Resetting demo scenario to baseline."
            msg_kn = "ಡೆಮೊ ಸನ್ನಿವೇಶವನ್ನು ಮರುಹೊಂದಿಸಲಾಗುತ್ತಿದೆ."
        elif re.search(r"logout|ನಿರ್ಗಮಿಸಿ", text, re.IGNORECASE):
            action = "LOGOUT"
            msg_en = "Logging out of Kisan account."
            msg_kn = "ಖಾತೆಯಿಂದ ನಿರ್ಗಮಿಸಲಾಗುತ್ತಿದೆ."
        elif re.search(r"confirm|submit|declare|ದೃಢೀಕರಿಸಿ|ಘೋಷಿಸಿ", text, re.IGNORECASE):
            action = "SUBMIT_HARVEST"
            msg_en = f"Confirming harvest declaration for {entities.get('quantity_kg', 200)}kg {entities.get('crop', 'Tomato')}."
            msg_kn = f"{entities.get('quantity_kg', 200)} ಕೆಜಿ {entities.get('crop', 'ಟೊಮೆಟೊ')} ಕೊಯ್ಲು ದೃಢೀಕರಿಸಲಾಗುತ್ತಿದೆ."
        else:
            action = "UPDATE_HARVEST"
            crop = entities.get('crop', 'Tomato')
            qty = entities.get('quantity_kg', 200)
            date = entities.get('harvest_date') or datetime.now().strftime("%Y-%m-%d")
            time = entities.get('harvest_time', '07:00 AM')
            msg_en = f"Updated: {qty}kg {crop}, Harvest: {date} at {time}."
            msg_kn = f"ನವೀಕರಿಸಲಾಗಿದೆ: {qty} ಕೆಜಿ {crop}, ಕೊಯ್ಲು: {date} {time}."

        return {
            "success": True,
            "transcript": text,
            "action": action,
            "entities": entities,
            "fields_updated": entities.get("fields_detected", []),
            "message_en": msg_en,
            "message_kn": msg_kn,
            "spoken_feedback": msg_kn if is_kn else msg_en
        }

    @classmethod
    def generate_mandi_spoken_summary(
        cls,
        crop: str,
        quantity_kg: float,
        mandi_results: List[Dict[str, Any]],
        language_code: str = "kn-IN"
    ) -> Dict[str, Any]:
        """
        Synthesizes spoken audio explanation comparing Mandi options and highlighting headline traps.
        """
        is_kn = "kn" in language_code.lower()
        if not mandi_results:
            msg = "ಯಾವುದೇ ಮಾರುಕಟ್ಟೆ ಮಾಹಿತಿ ಲಭ್ಯವಿಲ್ಲ." if is_kn else "No mandi data available for comparison."
            tts_res = cls.synthesize_speech(msg, language_code)
            return {"summary_text": msg, "audio_base64": tts_res.get("audio_base64", "")}

        recommended = next((m for m in mandi_results if m.get("is_recommended")), mandi_results[0])
        trap = next((m for m in mandi_results if m.get("is_trap")), None)

        rec_name = recommended.get("mandi_name") or recommended.get("market") or "Primary Mandi"
        rec_net = int(recommended.get("net_take_home", 0))
        rec_dist = recommended.get("distance_km", 0)
        rec_deductions = recommended.get("itemized_deductions") or recommended.get("deductions") or {}
        rec_trans = int(rec_deductions.get("transport_cost") or rec_deductions.get("transport", 0))

        if is_kn:
            dist_phrase = f"{rec_dist} ಕಿಮೀ ದೂರದಲ್ಲಿದ್ದು, " if rec_dist > 0 else ""
            trans_phrase = f"ಸಾರಿಗೆ ವೆಚ್ಚ ಕೇವಲ ₹{rec_trans}. " if rec_trans > 0 else ""
            text = f"ನಿಮ್ಮ {int(quantity_kg)} ಕೆಜಿ {crop} ಬೆಳೆಗಾಗಿ, {rec_name} {dist_phrase}₹{rec_net} ಗರಿಷ್ಠ ನಿವ್ವಳ ಹಣ ನೀಡುತ್ತದೆ. {trans_phrase}"
            if trap:
                trap_name = trap.get("mandi_name") or trap.get("market") or "Distant Mandi"
                trap_modal = trap.get("modal_price_per_kg") or trap.get("modal_price_kg", 0)
                trap_dist = trap.get("distance_km", 0)
                trap_ded = trap.get("itemized_deductions") or trap.get("deductions") or {}
                trap_trans = int(trap_ded.get("transport_cost") or trap_ded.get("transport", 0))
                diff = int(rec_net - trap.get("net_take_home", 0))
                dist_warn = f"{trap_dist} ಕಿಮೀ ದೂರದ ಪ್ರಯಾಣ ಮತ್ತು ₹{trap_trans} ವಾಹನ ಬಾಡಿಗೆಯಿಂದಾಗಿ " if trap_dist > 0 else "ಹೆಚ್ಚಿನ ಸಾರಿಗೆ ವೆಚ್ಚದಿಂದ "
                text += f"ಎಚ್ಚರ: {trap_name} ನಲ್ಲಿ ಕೆಜಿಗೆ ₹{trap_modal} ಕಾಣಿಸಿದರೂ, {dist_warn}₹{diff} ಕಡಿಮೆ ಲಾಭ ಸಿಗುತ್ತದೆ."
            else:
                text += "ಕಡಿಮೆ ಸಾರಿಗೆ ಮತ್ತು ಕಮಿಷನ್ ವೆಚ್ಚ ಇರುವ ಸಮೀಪದ ಮಾರುಕಟ್ಟೆಯನ್ನು ಆಯ್ಕೆ ಮಾಡಿ."
        else:
            dist_phrase = f"at {rec_dist} km " if rec_dist > 0 else ""
            trans_phrase = f"Transport freight is ₹{rec_trans}. " if rec_trans > 0 else ""
            text = f"For your {int(quantity_kg)} kg of {crop}, {rec_name} {dist_phrase}gives the highest net return of ₹{rec_net}. {trans_phrase}"
            if trap:
                trap_name = trap.get("mandi_name") or trap.get("market") or "Distant Mandi"
                trap_modal = trap.get("modal_price_per_kg") or trap.get("modal_price_kg", 0)
                trap_dist = trap.get("distance_km", 0)
                trap_ded = trap.get("itemized_deductions") or trap.get("deductions") or {}
                trap_trans = int(trap_ded.get("transport_cost") or trap_ded.get("transport", 0))
                loss = int(rec_net - trap.get("net_take_home", 0))
                dist_warn = f"due to {trap_dist} km distance (₹{trap_trans} freight) " if trap_dist > 0 else "due to transport costs "
                text += f"Beware of {trap_name}: headline rate is ₹{trap_modal}/kg, but {dist_warn}you take home ₹{loss} less cash in hand."
            else:
                text += "Choosing direct pooling saves middleman commission and transport loss."

        tts_res = cls.synthesize_speech(text, language_code)
        return {
            "summary_text": text,
            "audio_base64": tts_res.get("audio_base64", ""),
            "language_code": language_code,
            "recommended_mandi": rec_name,
            "trap_mandi": (trap.get("mandi_name") or trap.get("market")) if trap else None
        }

    @classmethod
    def answer_scheme_query(
        cls,
        query_text: str,
        farmer_id: Optional[str] = None,
        language_code: str = "kn-IN",
        db: Optional[Any] = None
    ) -> Dict[str, Any]:
        """
        Factual, grounded AI Assistant answering farmer questions about Government Schemes,
        Subsidies, FPO opportunities, and e-NAM.
        Never guarantees approval; references official government portals.
        """
        is_kn = "kn" in language_code.lower() or any("\u0C80" <= c <= "\u0CFF" for c in query_text)
        query_lower = query_text.lower()

        # Load verified schemes
        from backend.app.models.scheme_models import Scheme
        from backend.app.services.scheme_matching_service import SchemeMatchingService

        schemes = []
        matches = []
        if db:
            schemes = db.query(Scheme).all()
            if farmer_id:
                matches = SchemeMatchingService.evaluate_farmer_opportunities(farmer_id, db)

        # Factual grounded response matching
        if re.search(r"fpo|producer company|ಸಂಸ್ಥೆ|ಎಫ್‌ಪಿಒ|ಗುಂಪು", query_lower):
            if is_kn:
                ans = (
                    "FPO (ರೈತ ಉತ್ಪಾದಕ ಸಂಸ್ಥೆ) ರೈತರ ಒಕ್ಕೂಟವಾಗಿದೆ. SFAC ಮತ್ತು ನಬಾರ್ಡ್ ಯೋಜನೆ ಅಡಿಯಲ್ಲಿ, "
                    "ಕನಿಷ್ಠ 300 ರೈತರು ಸೇರಿ FPO ರಚಿಸಿದರೆ ₹15 ಲಕ್ಷದವರೆಗೆ ಈಕ್ವಿಟಿ ಅನುದಾನ ಮತ್ತು ₹2 ಕೋಟಿ ಸಾಲ ಖಾತರಿ ಸಿಗುತ್ತದೆ. "
                    "ನೆನಪಿಡಿ: ಈ ಅನುದಾನವು FPO ಸಂಸ್ಥೆಗೆ ಮಾತ್ರ ಸಿಗುತ್ತದೆ, ವೈಯಕ್ತಿಕ ರೈತರ ಖಾತೆಗೆ ನೇರವಾಗಿ ಜಮೆಯಾಗುವುದಿಲ್ಲ. "
                    "ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್: sfacindia.com, ಸಹಾಯವಾಣಿ: 1800 270 0224."
                )
            else:
                ans = (
                    "Under the Central Sector Scheme for 10,000 FPOs (SFAC / NABARD), an FPO with at least 300 farmer "
                    "members can receive up to ₹15 Lakhs matching Equity Grant and ₹2 Crore Credit Guarantee. "
                    "IMPORTANT NOTE: This grant is paid to the registered FPO company for collective infrastructure, "
                    "NOT directly to individual farmers. Official portal: sfacindia.com (Helpline: 1800 270 0224)."
                )
            scheme_id = "SCHEME-SFAC-10K-FPO"

        elif re.search(r"raitha siri|millet|ಸಿರಿ|ರೈತ ಸಿರಿ|ತೋಟಗಾರಿಕೆ|fruits", query_lower):
            if is_kn:
                ans = (
                    "ಕರ್ನಾಟಕ ರೈತ ಸಿರಿ ಯೋಜನೆಯಡಿ, ಸಿರಿಧಾನ್ಯ ಮತ್ತು ತೋಟಗಾರಿಕೆ ಬೆಳೆಗಾರರಿಗೆ ಹೆಕ್ಟೇರ್‌ಗೆ ₹10,000 (ಗರಿಷ್ಠ 2 ಹೆಕ್ಟೇರ್‌ಗೆ ₹20,000) "
                    "ಪ್ರೋತ್ಸಾಹಧನ ಸಿಗುತ್ತದೆ. ಇದಕ್ಕೆ ನಿಮ್ಮ ಭೂಮಿಯನ್ನು ಫ್ರೂಟ್ಸ್ (FRUITS) ಪೋರ್ಟಲ್‌ನಲ್ಲಿ ನೋಂದಾಯಿಸಿ FID ಸಂಖ್ಯೆ ಹೊಂದಿರಬೇಕು. "
                    "ಅಗತ್ಯ ದಾಖಲೆಗಳು: ಪಹಣಿ/RTC, ಆಧಾರ್, ಬ್ಯಾಂಕ್ ಪಾಸ್‌ಬುಕ್. ನಿಮ್ಮ ಸಮೀಪದ ರೈತ ಸಂಪರ್ಕ ಕೇಂದ್ರಕ್ಕೆ (RSK) ಭೇಟಿ ನೀಡಿ. "
                    "ಅಧಿಕೃತ ಪೋರ್ಟಲ್: fruits.karnataka.gov.in."
                )
            else:
                ans = (
                    "Under Karnataka Raitha Siri Scheme, horticulture & millet growers receive ₹10,000 per hectare "
                    "(up to 2 hectares = ₹20,000) directly via DBT. You must be registered on the Karnataka FRUITS portal "
                    "with a valid FID. Required documents: RTC/Pahani, Aadhaar, and bank passbook. "
                    "Apply or update at your nearest Raitha Samparka Kendra (RSK) or visit fruits.karnataka.gov.in."
                )
            scheme_id = "SCHEME-KA-RAITHA-SIRI"

        elif re.search(r"pm kisan|kisan|ಸಮ್ಮಾನ್|ಆದಾಯ|6000", query_lower):
            if is_kn:
                ans = (
                    "ಪ್ರಧಾನ ಮಂತ್ರಿ ಕಿಸಾನ್ ಸಮ್ಮಾನ್ ನಿಧಿ (PM-KISAN) ಯೋಜನೆಯಡಿ, ಸಾಗುವಳಿ ಭೂಮಿ ಹೊಂದಿರುವ ಅರ್ಹ ರೈತ ಕುಟುಂಬಗಳಿಗೆ "
                    "ವರ್ಷಕ್ಕೆ ₹6,000 ಆದಾಯ ಬೆಂಬಲ ಸಿಗುತ್ತದೆ (ತಲಾ ₹2,000 ರ 3 ಕಂತುಗಳಲ್ಲಿ). "
                    "ನಿಮ್ಮ ಆಧಾರ್ ಸಂಖ್ಯೆಗೆ e-KYC ಪೂರ್ಣಗೊಂಡಿರಬೇಕು ಮತ್ತು ಬ್ಯಾಂಕ್ ಖಾತೆ ಆಧಾರ್‌ಗೆ ಲಿಂಕ್ ಆಗಿರಬೇಕು. "
                    "ಅಧಿಕೃತ ಪೋರ್ಟಲ್: pmkisan.gov.in, ಸಹಾಯವಾಣಿ: 155261."
                )
            else:
                ans = (
                    "PM-KISAN provides ₹6,000 per year in three installments of ₹2,000 each directly to cultivable "
                    "landholding farmer families. Requires completed biometric/facial e-KYC and Aadhaar-seeded bank account. "
                    "Official portal: pmkisan.gov.in (Helpline: 155261)."
                )
            scheme_id = "SCHEME-PM-KISAN"

        elif re.search(r"insurance|fasal bima|samrakshane|ವಿಮೆ|ಫಸಲ್ ಬಿಮಾ|ಸಂರಕ್ಷಣೆ|ನಷ್ಟ", query_lower):
            if is_kn:
                ans = (
                    "ಪ್ರಧಾನ ಮಂತ್ರಿ ಫಸಲ್ ಬಿಮಾ (ಸಂರಕ್ಷಣೆ ಕರ್ನಾಟಕ) ಯೋಜನೆಯಡಿ ಬೆಳೆ ಹಾನಿಗೆ ಸಮಗ್ರ ವಿಮಾ ರಕ್ಷಣೆ ಸಿಗುತ್ತದೆ. "
                    "ಟೊಮೆಟೊ, ಈರುಳ್ಳಿ ಮುಂತಾದ ತೋಟಗಾರಿಕೆ ಬೆಳೆಗಳಿಗೆ ರೈತರು ಕೇವಲ 5% ಪ್ರೀಮಿಯಂ ಪಾವತಿಸಬೇಕು. "
                    "ಅಗತ್ಯ ದಾಖಲೆಗಳು: ಜಮೀನಿನ ಪಹಣಿ (RTC), ಬಿತ್ತನೆ ದೃಢೀಕರಣ ಪತ್ರ, ಆಧಾರ್. "
                    "ನಿಮ್ಮ ತಾಲೂಕಿನ ಬೆಳೆ ಕಟ್-ಆಫ್ ದಿನಾಂಕವನ್ನು samrakshane.karnataka.gov.in ನಲ್ಲಿ ಪರಿಶೀಲಿಸಿ."
                )
            else:
                ans = (
                    "PM Fasal Bima Yojana / Samrakshane Karnataka covers natural crop losses from pre-sowing to post-harvest. "
                    "For commercial and horticulture crops like Tomato and Onion, farmers pay only 5% nominal premium. "
                    "Check cut-off dates and submit RTC and sowing certificate at your nearest CSC or bank branch. "
                    "Portal: samrakshane.karnataka.gov.in (Helpline: 080-22180800)."
                )
            scheme_id = "SCHEME-PMFBY-SAMRAKSHANE"

        elif re.search(r"enam|e-nam|ಮಾರುಕಟ್ಟೆ|ಆನ್‌ಲೈನ್ ಮಾರಾಟ|trading|bidding", query_lower):
            if is_kn:
                ans = (
                    "e-NAM (ರಾಷ್ಟ್ರೀಯ ಕೃಷಿ ಮಾರುಕಟ್ಟೆ) ಮೂಲಕ ನೀವು ಕೋಲಾರ ಅಥವಾ ಬಂಗಾರಪೇಟೆ ಎಪಿಎಂಸಿಗೆ ತರುವ ಬೆಳೆಯನ್ನು "
                    "ದೇಶಾದ್ಯಂತದ ವ್ಯಾಪಾರಿಗಳು ಆನ್‌ಲೈನ್‌ನಲ್ಲಿ ಸ್ಪರ್ಧಾತ್ಮಕ ದರಕ್ಕೆ ಬಿಡ್ ಮಾಡುತ್ತಾರೆ. "
                    "ಯಾವುದೇ ಅನಧಿಕೃತ ಕಮಿಷನ್ ಕಡಿತವಿಲ್ಲ, ಸರಿಯಾದ ಎಲೆಕ್ಟ್ರಾನಿಕ್ ತೂಕ, ಮತ್ತು 24 ಗಂಟೆಗಳಲ್ಲಿ ನಿಮ್ಮ ಬ್ಯಾಂಕ್ ಖಾತೆಗೆ ನೇರ ಹಣ ಬರುತ್ತದೆ. "
                    "ನೋಂದಣಿಗೆ ಆಧಾರ್ ಮತ್ತು ಬ್ಯಾಂಕ್ ಪಾಸ್‌ಬುಕ್ ಸಾಕು. ಅಧಿಕೃತ ಪೋರ್ಟಲ್: enam.gov.in."
                )
            else:
                ans = (
                    "e-NAM provides pan-India electronic price discovery at integrated APMCs (like Kolar and Bangarpet). "
                    "Produce is assayed for quality, and verified buyers bid electronically with zero unauthorized commission "
                    "and direct online payment to your bank within 24 hours. "
                    "Register free with Aadhaar and bank details at the APMC gate or on enam.gov.in."
                )
            scheme_id = "SCHEME-ENAM-DIRECT"

        else:
            if is_kn:
                ans = (
                    "ಸರ್ಕಾರಿ ಯೋಜನೆಗಳ ವಿವರ: 1. ಪಿಎಂ-ಕಿಸಾನ್ (₹6,000/ವರ್ಷ), 2. ಕರ್ನಾಟಕ ರೈತ ಸಿರಿ (₹10,000/ಹೆಕ್ಟೇರ್ ತೋಟಗಾರಿಕೆ), "
                    "3. ಸಂರಕ್ಷಣೆ ಬೆಳೆ ವಿಮೆ (5% ಪ್ರೀಮಿಯಂ), 4. ನಬಾರ್ಡ್ FPO ಅನುದಾನ (₹15 ಲಕ್ಷ ಸಂಸ್ಥೆಗೆ), "
                    "5. e-NAM ನೇರ ಮಾರುಕಟ್ಟೆ. ನಿರ್ದಿಷ್ಟ ಯೋಜನೆಯ ಬಗ್ಗೆ ಕೇಳಲು ಹೆಸರನ್ನು ಹೇಳಿ."
                )
            else:
                ans = (
                    "Available verified agricultural opportunities: 1. PM-KISAN (₹6,000/yr), "
                    "2. Karnataka Raitha Siri (₹10,000/ha for horticulture), 3. Samrakshane Crop Insurance, "
                    "4. SFAC/NABARD 10,000 FPO Scheme (₹15 Lakhs institutional equity grant), and 5. e-NAM Direct Trading. "
                    "Ask about any specific scheme for step-by-step guidance."
                )
            scheme_id = None

        tts_res = cls.synthesize_speech(ans, language_code)
        return {
            "query": query_text,
            "answer": ans,
            "language_code": language_code,
            "scheme_id": scheme_id,
            "audio_base64": tts_res.get("audio_base64", ""),
            "disclaimer": "Factual guidance based strictly on verified government publications. Not a government approval or financial guarantee."
        }
