import os
import json
import base64
import re
import urllib.request
import urllib.error
from typing import Dict, Any, Optional
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
        raw_keys = os.getenv("SARVAM_API_KEYS", getattr(settings, "SARVAM_API_KEYS", "") or "")
        if raw_keys:
            keys.extend([k.strip() for k in raw_keys.split(",") if k.strip()])
        single_key = os.getenv("SARVAM_API_KEY", getattr(settings, "SARVAM_API_KEY", "") or "").strip()
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
        speaker: str = "pavithra"
    ) -> Dict[str, Any]:
        """
        Synthesizes text to speech using Sarvam Bulbul v4-flash model with multi-key cycling.
        Returns base64 encoded audio.
        """
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
        crop = "Tomato"
        variety = "Hybrid Red"
        quantity_kg = 200.0
        quality_grade = "Grade A"
        village = "Mulbagal"
        harvest_date = "2026-11-13"
        harvest_time = "07:00 AM"
        expected_date_time = "2026-11-14 09:00 AM"
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
            harvest_date = "2026-11-13"
            fields_detected.append("harvest_date")
        elif re.search(r"yesterday|ನಿನ್ನೆ", text, re.IGNORECASE):
            harvest_date = "2026-11-12"
            fields_detected.append("harvest_date")
        elif re.search(r"tomorrow|ನಾಳೆ", text, re.IGNORECASE):
            harvest_date = "2026-11-14"
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
            expected_date_time = "2026-11-14 09:00 AM"
            fields_detected.append("expected_date_time")
        elif re.search(r"tomorrow evening|ನಾಳೆ ಸಂಜೆ", text, re.IGNORECASE):
            expected_date_time = "2026-11-14 05:00 PM"
            fields_detected.append("expected_date_time")
        elif re.search(r"today evening|ಇಂದು ಸಂಜೆ", text, re.IGNORECASE):
            expected_date_time = "2026-11-13 06:00 PM"
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
        elif re.search(r"malur|ಮಾಲೂರು", text, re.IGNORECASE):
            village = "Malur"
            fields_detected.append("village")
        elif re.search(r"kolar|ಕೋಲಾರ", text, re.IGNORECASE):
            village = "Kolar"
            fields_detected.append("village")
        else:
            village = "Mulbagal"

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
            date = entities.get('harvest_date', '2026-11-13')
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
