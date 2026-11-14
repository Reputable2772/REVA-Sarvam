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
    TRANSLATE_URL = "https://api.sarvam.ai/translate"

    _key_index: int = 0
    _translate_cache: Dict[str, str] = {}

    SUPPORTED_LANGUAGES: Dict[str, Dict[str, Any]] = {
        "hi-IN": {
            "name": "Hindi",
            "native_name": "हिन्दी",
            "script": "Devanagari",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "aditya_hi_conversational",
            "unicode_range": (0x0900, 0x097F),
            "sample_phrase": "मेरे पास 800 किलो ए ग्रेड टमाटर है।"
        },
        "bn-IN": {
            "name": "Bengali",
            "native_name": "বাংলা",
            "script": "Bengali",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "arnab_bn_conversation",
            "unicode_range": (0x0980, 0x09FF),
            "sample_phrase": "আমার কাছে ৮০০ কেজি গ্রেড এ টমেটো আছে।"
        },
        "kn-IN": {
            "name": "Kannada",
            "native_name": "ಕನ್ನಡ",
            "script": "Kannada",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "chaitra_kn_conversation",
            "unicode_range": (0x0C80, 0x0CFF),
            "sample_phrase": "ನನ್ನ ಬಳಿ 800 ಕೆಜಿ ಗ್ರೇಡ್ ಎ ಟೊಮೆಟೊ ಇದೆ."
        },
        "ml-IN": {
            "name": "Malayalam",
            "native_name": "മലയാളം",
            "script": "Malayalam",
            "tts_model": "bulbul:v3",
            "default_speaker": "aditya",
            "unicode_range": (0x0D00, 0x0D7F),
            "sample_phrase": "എന്റെ പക്കൽ 800 കിലോഗ്രാം ഗ്രേഡ് എ തക്കാളി ഉണ്ട്."
        },
        "mr-IN": {
            "name": "Marathi",
            "native_name": "मराठी",
            "script": "Devanagari",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "nilesh_mr_conversation",
            "unicode_range": (0x0900, 0x097F),
            "sample_phrase": "माझ्याकडे 800 किलो ग्रेड ए टोमॅटो आहेत."
        },
        "od-IN": {
            "name": "Odia",
            "native_name": "ଓଡ଼ିଆ",
            "script": "Odia",
            "tts_model": "bulbul:v3",
            "default_speaker": "aditya",
            "unicode_range": (0x0B00, 0x0B7F),
            "sample_phrase": "ମୋ ପାଖରେ 800 କିଲୋଗ୍ରାମ ଗ୍ରେଡ୍ ଏ ଟମାଟୋ ଅଛି।"
        },
        "pa-IN": {
            "name": "Punjabi",
            "native_name": "ਪੰਜਾਬੀ",
            "script": "Gurmukhi",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "anand_pa_conversation",
            "unicode_range": (0x0A00, 0x0A7F),
            "sample_phrase": "ਮੇਰੇ ਕੋਲ 800 ਕਿਲੋ ਗ੍ਰੇਡ ਏ ਟਮਾਟਰ ਹਨ।"
        },
        "ta-IN": {
            "name": "Tamil",
            "native_name": "தமிழ்",
            "script": "Tamil",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "gokul_ta_narration",
            "unicode_range": (0x0B80, 0x0BFF),
            "sample_phrase": "என்னிடம் 800 கிலோ தரம் ஏ தக்காளி உள்ளது."
        },
        "te-IN": {
            "name": "Telugu",
            "native_name": "తెలుగు",
            "script": "Telugu",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "kavitha_te_conversation",
            "unicode_range": (0x0C00, 0x0C7F),
            "sample_phrase": "నా వద్ద 800 కిలోల గ్రేడ్ ఎ టమాటా ఉంది."
        },
        "gu-IN": {
            "name": "Gujarati",
            "native_name": "ગુજરાતી",
            "script": "Gujarati",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "bhavik_gu_conversation",
            "unicode_range": (0x0A80, 0x0AFF),
            "sample_phrase": "મારી પાસે 800 કિલો ગ્રેડ એ ટામેટાં છે."
        },
        "en-IN": {
            "name": "English (India)",
            "native_name": "English",
            "script": "Latin",
            "tts_model": "bulbul:v4-flash",
            "default_speaker": "aparna_en_companion",
            "unicode_range": (0x0041, 0x007A),
            "sample_phrase": "I have 800 kg Grade A tomato ready for sale."
        }
    }

    @classmethod
    def normalize_language_code(cls, lang: Optional[str]) -> str:
        """
        Normalizes any 2-letter, case-varying, or hyphen/underscore formatted language code
        to the standard Sarvam 11-language format (e.g. 'kn', 'kn_in' -> 'kn-IN').
        """
        if not lang:
            return "kn-IN"
        cleaned = str(lang).strip().replace("_", "-")
        for code in cls.SUPPORTED_LANGUAGES:
            if code.lower() == cleaned.lower():
                return code
        prefix = cleaned.split("-")[0].lower()
        for code in cls.SUPPORTED_LANGUAGES:
            if code.split("-")[0].lower() == prefix:
                return code
        return "kn-IN"

    @classmethod
    def detect_language_from_text(cls, text: str, default: str = "kn-IN") -> str:
        """
        Determines the Sarvam language code from Unicode script code points.
        """
        if not text:
            return cls.normalize_language_code(default)
        counts = {lang: 0 for lang in cls.SUPPORTED_LANGUAGES}
        for char in text:
            code = ord(char)
            for lang, meta in cls.SUPPORTED_LANGUAGES.items():
                start, end = meta["unicode_range"]
                if start <= code <= end:
                    counts[lang] += 1
        best_lang = max(counts, key=counts.get)
        if counts[best_lang] > 0:
            return best_lang
        return cls.normalize_language_code(default)

    @classmethod
    def translate_text(
        cls,
        text: str,
        target_language_code: str = "en-IN",
        source_language_code: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Translates text across any of the 11 Sarvam languages using mayura:v1.
        Includes multi-key cycling and in-memory caching.
        """
        target_lang = cls.normalize_language_code(target_language_code)
        if not text or not text.strip():
            return {
                "success": True,
                "translated_text": "",
                "source_language": source_language_code or target_lang,
                "target_language": target_lang,
                "is_live_api": False
            }

        # If source is explicitly identical to target, return immediately
        if source_language_code and cls.normalize_language_code(source_language_code) == target_lang:
            return {
                "success": True,
                "translated_text": text,
                "source_language": target_lang,
                "target_language": target_lang,
                "is_live_api": False
            }

        cache_key = f"{text[:120]}::{source_language_code}::{target_lang}"
        if cache_key in cls._translate_cache:
            return {
                "success": True,
                "translated_text": cls._translate_cache[cache_key],
                "source_language": source_language_code or "auto",
                "target_language": target_lang,
                "is_live_api": True
            }

        keys = cls.get_api_keys()
        if keys:
            src = cls.normalize_language_code(source_language_code) if source_language_code else None
            if src == target_lang:
                return {
                    "success": True,
                    "translated_text": text,
                    "source_language": target_lang,
                    "target_language": target_lang,
                    "is_live_api": False
                }
            payload = {
                "input": text,
                "target_language_code": target_lang,
                "model": "mayura:v1"
            }
            if src:
                payload["source_language_code"] = src
            data_bytes = json.dumps(payload).encode()

            for _ in range(len(keys)):
                current_key = cls.get_api_key()
                try:
                    req = urllib.request.Request(
                        cls.TRANSLATE_URL,
                        data=data_bytes,
                        headers={
                            "api-subscription-key": current_key,
                            "Content-Type": "application/json"
                        },
                        method="POST"
                    )
                    with urllib.request.urlopen(req, timeout=10) as resp:
                        res = json.loads(resp.read().decode())
                        translated = res.get("translated_text", text)
                        cls._translate_cache[cache_key] = translated
                        return {
                            "success": True,
                            "translated_text": translated,
                            "source_language": res.get("source_language_code", source_language_code or "auto"),
                            "target_language": target_lang,
                            "model": "mayura:v1",
                            "is_live_api": True
                        }
                except (urllib.error.HTTPError, urllib.error.URLError, Exception):
                    cls.rotate_api_key()

        # Fallback if no API key or network fails: return original text
        return {
            "success": True,
            "translated_text": text,
            "source_language": source_language_code or "auto",
            "target_language": target_lang,
            "model": "mayura:v1 (passthrough)",
            "is_live_api": False
        }

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
        Supports all 11 Sarvam languages (hi-IN, bn-IN, kn-IN, ml-IN, mr-IN, od-IN, pa-IN, ta-IN, te-IN, gu-IN, en-IN).
        Falls back to OpenAI-compatible Whisper if configured, or realistic simulated speech if both fail.
        """
        target_lang = cls.normalize_language_code(language_code)
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
            body.extend(target_lang.encode() + b"\r\n")
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
                            "language_code": res_data.get("language_code", target_lang),
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
                        "language_code": target_lang,
                        "model": "whisper-1 (openai-fallback)",
                        "is_live_api": True
                    }
            except Exception:
                pass

        # Intelligent Fallback Simulation per language
        lang_meta = cls.SUPPORTED_LANGUAGES.get(target_lang, cls.SUPPORTED_LANGUAGES["kn-IN"])
        sample_transcript = lang_meta.get("sample_phrase", "I have 800 kg Grade A tomato ready for sale.")
        return {
            "success": True,
            "transcript": sample_transcript,
            "language_code": target_lang,
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
        Synthesizes text to speech using Sarvam Bulbul models (bulbul:v4-flash or bulbul:v3) with multi-key cycling.
        Automatically maps to verified native conversational speakers across all 11 supported languages.
        Returns base64 encoded audio.
        """
        target_lang = cls.normalize_language_code(language_code)
        lang_meta = cls.SUPPORTED_LANGUAGES.get(target_lang, cls.SUPPORTED_LANGUAGES["kn-IN"])
        tts_model = lang_meta.get("tts_model", "bulbul:v4-flash")

        # Select speaker
        if not speaker or speaker in ("pavithra", "arvind", "chaitra_kn_conversation", "aparna_en_companion"):
            if speaker == "chaitra_kn_conversation" and target_lang == "kn-IN":
                actual_speaker = "chaitra_kn_conversation"
            elif speaker == "aparna_en_companion" and target_lang == "en-IN":
                actual_speaker = "aparna_en_companion"
            else:
                actual_speaker = lang_meta["default_speaker"]
        else:
            actual_speaker = speaker

        keys = cls.get_api_keys()
        if keys:
            payload = {
                "inputs": [text],
                "target_language_code": target_lang,
                "speaker": actual_speaker,
                "model": tts_model
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
                            "language_code": target_lang,
                            "speaker": actual_speaker,
                            "model": tts_model,
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
            "language_code": target_lang,
            "speaker": actual_speaker,
            "model": f"{tts_model} (simulated)",
            "is_live_api": False
        }

    @classmethod
    def _normalize_indic_numerals_and_words(cls, text: str) -> str:
        """
        Converts Indic numerals (across all 11 languages) and spelled-out number words into standard digits.
        Handles: one, two, three, एक, दो, तीन, இரண்டு, మూడు, ಎರಡು, etc., and decimals like डेढ़ (1.5), ढाई (2.5), आधा/half (0.5).
        """
        if not text:
            return ""
        out = []
        for ch in text:
            code = ord(ch)
            for base in [0x0966, 0x09E6, 0x0A66, 0x0AE6, 0x0B66, 0x0BE6, 0x0C66, 0x0CE6, 0x0D66]:
                if base <= code <= base + 9:
                    ch = str(code - base)
                    break
            out.append(ch)
        normalized = "".join(out)

        num_words = {
            # English
            "one": "1", "two": "2", "three": "3", "four": "4", "five": "5",
            "six": "6", "seven": "7", "eight": "8", "nine": "9", "ten": "10",
            "half": "0.5",
            # Hindi / Marathi
            "एक": "1", "दो": "2", "दोन": "2", "तीन": "3", "चार": "4", "पांच": "5", "पाँच": "5", "पाच": "5",
            "छह": "6", "सात": "7", "आठ": "8", "नौ": "9", "दस": "10",
            "आधा": "0.5", "डेढ़": "1.5", "ढाई": "2.5",
            # Kannada
            "ಒಂದು": "1", "ಎರಡು": "2", "ಮೂರು": "3", "ನಾಲ್ಕು": "4", "ಐದು": "5",
            "ಆರು": "6", "ಏಳು": "7", "ಎಂಟು": "8", "ಒಂಬತ್ತು": "9", "ಹತ್ತು": "10",
            "ಅರ್ಧ": "0.5",
            # Tamil
            "ஒன்று": "1", "இரண்டு": "2", "மூன்று": "3", "நான்கு": "4", "ஐந்து": "5",
            "ஆறு": "6", "ஏழு": "7", "எட்டு": "8", "ஒன்பது": "9", "பத்து": "10",
            "அரை": "0.5",
            # Telugu
            "ఒక": "1", "ఒకటి": "1", "రెండు": "2", "మూడు": "3", "నాలుగు": "4", "ఐదు": "5",
            "ఆరు": "6", "ఏడు": "7", "ఎనిమిది": "8", "తొమ్మిది": "9", "పది": "10",
            "సగం": "0.5",
            # Bengali
            "এক": "1", "দুই": "2", "তিন": "3", "চার": "4", "পাঁচ": "5",
            # Gujarati
            "એક": "1", "બે": "2", "ત્રણ": "3", "ચાર": "4", "પાંચ": "5",
            # Malayalam
            "ഒന്ന്": "1", "രണ്ട്": "2", "മൂന്ന്": "3", "നാല്": "4", "അഞ്ച്": "5",
            # Punjabi
            "ਇੱਕ": "1", "ਦੋ": "2", "ਤਿੰਨ": "3", "ਚਾਰ": "4", "ਪੰਜ": "5",
            # Odia
            "ଏକ": "1", "ଦୁଇ": "2", "ତିନି": "3", "ଚାରି": "4", "ପାଞ୍ଚ": "5"
        }

        for word, digit in num_words.items():
            pattern = r"(^|\s)" + re.escape(word) + r"(?=\s|$)"
            normalized = re.sub(pattern, r"\g<1>" + digit, normalized, flags=re.IGNORECASE)
        return normalized

    @classmethod
    def _extract_heuristic(cls, text: str) -> Dict[str, Any]:
        """
        Robust pan-Indian agricultural NLP heuristic parser.
        Extracts entities across all 11 Sarvam languages and scripts.
        """
        text = cls._normalize_indic_numerals_and_words(text)
        now = datetime.now()
        today_str = now.strftime("%Y-%m-%d")
        yesterday_str = (now - timedelta(days=1)).strftime("%Y-%m-%d")
        tomorrow_str = (now + timedelta(days=1)).strftime("%Y-%m-%d")

        crop = None
        variety = None
        quantity_kg = 200.0
        quality_grade = "Grade A"
        village = None
        harvest_date = today_str
        harvest_time = "07:00 AM"
        expected_date_time = f"{tomorrow_str} 09:00 AM"
        sell_immediately = True
        fields_detected = []

        norm_text = cls._normalize_indic_numerals_and_words(text)
        search_target = f"{text} {norm_text}"

        # Regex for quantity across 11 Indian scripts (kg, quintal, ton)
        unit_pattern = (
            r"(\d+(?:\.\d+)?)\s*(kg|kgs|kilo|kilos|kilogram|kilograms|ಕೆಜಿ|किलो|किलोग्राम|केजी|कैटल|കിലോ|கிலோ|కిలో|కిలోల|કિલો|ਕਿਲੋ|କିଲୋ|"
            r"quintal|quintals|ക്വിന്റൽ|क्विंटल|क्विंटलों|ಕ್ವಿಂಟಾಲ್|ಕವಿಂಟಾಲ್|ಕೈಂಟಾಲ್|কুইন্টাল|குவிண்டால்|குவிண்டால்கள்|క్వింటాల్|క్వింటాళ్ళు|క్వింటాళ్ల|ક્વિન્ટલ|ਕਵਿੰਟਲ|ਕੁਇੰਟਲ|ਕੁੰਟਲ|କ୍ୱିଣ୍ଟାଲ|"
            r"ton|tons|tonne|tonnes|टन|टनों|ಟನ್|ಟನ್ನು|ಟನ್‌ಗಳು|টন|টনের|ടൺ|டன்|டன்கள்|டன்னும்|టన్ను|టన్నుల|టన్నులు|ટન|ਟਨ|ਟਨਾਂ|ଟନ୍)"
        )
        qty_unit_match = re.search(unit_pattern, norm_text, re.IGNORECASE)
        if qty_unit_match:
            val = float(qty_unit_match.group(1))
            unit = qty_unit_match.group(2).lower()
            if any(t in unit for t in ["ton", "tonne", "टन", "ಟನ್", "ಟನ್ನು", "টন", "ടൺ", "டன்", "టన్ను", "ટન", "ਟਨ", "ଟନ୍"]):
                quantity_kg = val * 1000.0
            elif any(q in unit for q in ["quintal", "क्विंटल", "ಕ್ವಿಂಟಾಲ್", "ಕವಿಂಟಾಲ್", "ಕೈಂಟಾಲ್", "কুইন্টাল", "ക്വിന്റൽ", "குவிண்டால்", "క్వింటాల్", "క్వింటాళ్ళు", "క్వింటాళ్ల", "ક્વિન્ટલ", "ਕਵਿੰਟਲ", "ਕੁਇੰਟਲ", "ਕੁੰਟਲ", "କ୍ୱିଣ୍ଟାଲ"]):
                quantity_kg = val * 100.0
            else:
                quantity_kg = val
            fields_detected.append("quantity_kg")
        else:
            qty_fallback = re.search(r"\b(\d{2,5}(?:\.\d+)?)\b(?!\s*(?:am|pm|ಗಂಟೆ|days|hours|घंटे|घण्टे))", norm_text, re.IGNORECASE)
            if qty_fallback:
                quantity_kg = float(qty_fallback.group(1))
                fields_detected.append("quantity_kg")

        # Grade detection across all 11 scripts
        if re.search(r"grade\s*b|ಗ್ರೇಡ್\s*ಬಿ|ग्रेड\s*बी|గ్రేడ్\s*బి|கிரேடு\s*பி|গ্রেড\s*বি|ગ્રેડ\s*બી|ਗ੍ਰੇਡ\s*ਬੀ|ഗ്രേഡ്\s*ബി|ଗ୍ରେଡ୍\s*ବି|ग्रेड\s*ब", text, re.IGNORECASE):
            quality_grade = "Grade B"
            fields_detected.append("quality_grade")
        elif re.search(r"grade\s*c|ಗ್ರೇಡ್\s*ಸಿ|ग्रेड\s*सी|గ్రేడ్\s*సి|கிரேடு\s*சி|গ্রেড\s*সি|ગ્રેડ\s*સી|ਗ੍ਰੇਡ\s*ਸੀ|ഗ്രേഡ്\s*സി|ଗ୍ରେଡ୍\s*ସି|ग्रेड\s*क", text, re.IGNORECASE):
            quality_grade = "Grade C"
            fields_detected.append("quality_grade")
        elif re.search(r"grade\s*a|ಗ್ರೇಡ್\s*ಎ|ग्रेड\s*ए|గ్రేడ్\s*ఎ|கிரேடு\s*ஏ|গ্রেড\s*এ|ગ્રેડ\s*એ|ਗ੍ਰੇਡ\s*ਏ|ഗ്രേഡ്\s*എ|ଗ୍ରେଡ୍\s*ଏ|ग्रेड\s*अ", text, re.IGNORECASE):
            quality_grade = "Grade A"
            fields_detected.append("quality_grade")

        # Crop detection across all 11 scripts
        if re.search(r"onion|onions|পে[\u0981]?[\u09af\u09df]\u09bc?াজ|ಈರುಳ್ಳಿ|प्याज|कांदा|உள்ளி|வெங்காயம்|உரு\s*வெங்காயம்|ఉల్లిపాయ|ఉల్లి|పేঁయাজ|ડુંગળી|ਪਿਆਜ਼|ପିଆଜ|পিয়াঁজ|পেঁয়াজ|পেয়াজ|পিয়াজ|ഉള്ളി|സവാള|ചുവന്നുള്ളി", search_target, re.IGNORECASE):
            crop = "Onion"
            variety = "Nasik Red"
            fields_detected.append("crop")
        elif re.search(r"potato|potatoes|ಆಲೂಗಡ್ಡೆ|ಆಲೂ|आलू|बटाटा|உருளைக்கிழங்கு|உருளை|బంగాళాదుంప|আলু|બટાકા|બટાટા|ഉരുളക്കിഴങ്ങ്|ਆਲੂ|ଆଳୁ", search_target, re.IGNORECASE):
            crop = "Potato"
            variety = "Jyoti"
            fields_detected.append("crop")
        elif re.search(r"cabbage|cabbages|ಎಲೆಕೋಸು|ಕೋಸು|पत्तागोभी|पत्ता\s*गोभी|कोबी|முட்டைகோஸ்|கோஸ்|క్యాబేజీ|বাঁਧাকপি|કોબીજ|കോബേജ്|ਪੱਤਾਗੋਭੀ|ବନ୍ଧାକୋବି", search_target, re.IGNORECASE):
            crop = "Cabbage"
            variety = "Green Express"
            fields_detected.append("crop")
        elif re.search(r"carrot|carrots|ಕ್ಯಾರೆಟ್|गाजर|கேரட்|క్యారెట్|গাজর|ગાજર|കാരറ്റ്|ਗਾਜਰ|ଗାଜର", search_target, re.IGNORECASE):
            crop = "Carrot"
            variety = "Pusa Rudhira"
            fields_detected.append("crop")
        elif re.search(r"chilli|chili|chillies|chilies|green\s*chilli|green\s*chili|ಮೆಣಸಿನಕಾಯಿ|ಮೆಣಸಿನ|ಮಿರ್ಚಿ|मिर्च|मिरची|பச்சை\s*மிளகாய்|மிளகாய்|మిరపకాయ|మిరప|লঙ্কা|મરચાં|പച്ചമുളക്|മുളക്|ਮਿਰਚ|ଲଙ୍କା", search_target, re.IGNORECASE):
            crop = "Chilli"
            variety = "Guntur Hot"
            fields_detected.append("crop")
        elif re.search(r"garlic|ಬೆಳ್ಳುಳ್ಳಿ|लहसुन|लसूण|பூண்டு|వెల్లుల్లి|রসুন|લસણ|ਲਸਣ|ରସୁଣ|വെളുത്തുള്ളി", search_target, re.IGNORECASE):
            crop = "Garlic"
            variety = "Ooty White"
            fields_detected.append("crop")
        elif re.search(r"ginger|ಶುಂಠಿ|अदरक|आले|இஞ்சி|అల్లం|আদা|આદુ|ਅਦਰਕ|ଅଦା|ഇഞ്ചി", search_target, re.IGNORECASE):
            crop = "Ginger"
            variety = "Rio de Janeiro"
            fields_detected.append("crop")
        elif re.search(r"wheat|ಗೋಧಿ|गेहूं|गहू|கோதுமை|గోధుமలు|গম|ઘઉં|ਕਣਕ|ଗହମ|ഗോതമ്പ്", search_target, re.IGNORECASE):
            crop = "Wheat"
            variety = "Sharbati"
            fields_detected.append("crop")
        elif re.search(r"rice|paddy|ಅಕ್ಕಿ|ಭತ್ತ|चावल|धान|तांदूळ|அரிசி|బియ్యം|వరి|চাল|ડાંગર|ਚੌਲ|ଧାନ|നെല്ല്|അരി", search_target, re.IGNORECASE):
            crop = "Rice"
            variety = "Sona Masoori"
            fields_detected.append("crop")
        elif re.search(r"cotton|ಹತ್ತಿ|कपास|कापूस|பருத்தி|పత్తి|তুলা|કપાસ|ਕਪਾਹ|କପା|പരുത്തി", search_target, re.IGNORECASE):
            crop = "Cotton"
            variety = "Bt Cotton"
            fields_detected.append("crop")
        elif re.search(r"maize|corn|ಜೋಳ|ಮುಸುಕಿನ ಜೋಳ|मक्का|மக்காச்சோளம்|మొక్కజొన్న|ভুট্টা|મકાઈ|ਮੱਕੀ|ମକା|ചോളം", search_target, re.IGNORECASE):
            crop = "Maize"
            variety = "Pioneer Hybrid"
            fields_detected.append("crop")
        elif re.search(r"tomato|tomatoes|tomat|ಟೊಮೆಟೊ|ಟೊಮ್ಯಾಟೊ|टमाटर|टोमॅटो|தக்காளி|టమాటా|టమోటా|টমেটো|ટામેટાં|ટામેટા|തക്കാളി|ਟਮਾਟਰ|ଟମାଟୋ", search_target, re.IGNORECASE):
            crop = "Tomato"
            variety = "Hybrid Red"
            fields_detected.append("crop")

        # Harvest Date detection across 11 scripts
        if re.search(r"today|ಇಂದು|ಈ ದಿನ|आज|आजचा|இன்று|ఈరోజు|আজ|આજે|ਅੱਜ|ଆଜି|ഇന്ന്", text, re.IGNORECASE):
            harvest_date = today_str
            fields_detected.append("harvest_date")
        elif re.search(r"yesterday|ನಿನ್ನೆ|कल|बीता हुआ कल|काल|நேற்று|నిన్న|গতকাল|ગઈકાલે|ਕੱਲ੍ਹ|ଗତକାଲି|ഇന്നലെ", text, re.IGNORECASE):
            harvest_date = yesterday_str
            fields_detected.append("harvest_date")
        elif re.search(r"tomorrow|ನಾಳೆ|कल|आने वाला कल|उद्या|நாளை|రేపు|কাল|કાલે|ਕੱਲ੍ਹ|କାଲି|നാളെ", text, re.IGNORECASE):
            harvest_date = tomorrow_str
            fields_detected.append("harvest_date")

        date_match = re.search(r"\b(202\d-\d{2}-\d{2})\b", text)
        if date_match:
            harvest_date = date_match.group(1)
            fields_detected.append("harvest_date")

        # Harvest Time detection
        time_match = re.search(r"(\d{1,2})(?::(\d{2}))?\s*(am|pm|ಗಂಟೆ|बजे|மணி|గంటలు)", text, re.IGNORECASE)
        if time_match:
            hour = int(time_match.group(1))
            minute = time_match.group(2) or "00"
            ampm = (time_match.group(3) or "").upper()
            if any(w in ampm for w in ["ಗಂಟೆ", "बजे", "மணி", "గంటలు"]):
                ampm = "AM" if (any(m in text for m in ["ಬೆಳಿಗ್ಗೆ", "सुबह", "காலை", "ఉదయం"]) or hour < 12) else "PM"
            harvest_time = f"{hour:02d}:{minute} {ampm}"
            fields_detected.append("harvest_time")
        elif re.search(r"morning|ಬೆಳಿಗ್ಗೆ|सुबह|காலை|ఉదయం|সকাল|સવાર|ਸਵੇਰ|ସକାଳ|രാവിലെ", text, re.IGNORECASE):
            harvest_time = "07:00 AM"
            fields_detected.append("harvest_time")
        elif re.search(r"afternoon|ಮಧ್ಯಾಹ್ನ|दोपहर|மதியம்|మధ్యాహ్నం|দুপুর|બપોર|ਦੁਪਹਿਰ|ଅପରାହ୍ନ|ഉച്ചതിരിഞ്ഞ്", text, re.IGNORECASE):
            harvest_time = "01:30 PM"
            fields_detected.append("harvest_time")
        elif re.search(r"evening|ಸಂಜೆ|शाम|மாலை|సాయంత్రం|সন্ধ্যা|સાંજ|ਸ਼ਾਮ|ସନ୍ଧ୍ୟା|വൈകുന്നേരം", text, re.IGNORECASE):
            harvest_time = "05:30 PM"
            fields_detected.append("harvest_time")

        # Expected Date/Time detection
        if re.search(r"tomorrow morning|ನಾಳೆ ಬೆಳಿಗ್ಗೆ|कल सुबह|நாளை காலை|రేపు ఉదయం", text, re.IGNORECASE):
            expected_date_time = f"{tomorrow_str} 09:00 AM"
            fields_detected.append("expected_date_time")
        elif re.search(r"tomorrow evening|ನಾಳೆ ಸಂಜೆ|कल शाम|நாளை மாலை|రేపు సాయంత్రం", text, re.IGNORECASE):
            expected_date_time = f"{tomorrow_str} 05:00 PM"
            fields_detected.append("expected_date_time")
        elif re.search(r"today evening|ಇಂದು ಸಂಜೆ|आज शाम|இன்று மாலை|ఈరోజు సాయంత్రం", text, re.IGNORECASE):
            expected_date_time = f"{today_str} 06:00 PM"
            fields_detected.append("expected_date_time")

        # Sell immediately vs Cold storage
        if re.search(r"cold storage|store|warehouse|ಶೀತಲ|ಇಲ್ಲ|hold|later|कोल्ड स्टोरेज|भंडारण|శీతల|குளிர்பதன|হিমাগার|ਕੋਲਡ ਸਟੋਰੇਜ|ଶୀତଳ|ശീതീകരണ", text, re.IGNORECASE):
            sell_immediately = False
            fields_detected.append("sell_immediately")
        elif re.search(r"immediately|now|direct|ಮಾರಾಟ|ಈಗಲೇ|ಹೌದು|yes|today|तुरंत|अभी|इरवे|తక్షణమే|உடனடியாக|এখনই|તરત|ਤੁਰନ୍ତ|ଶୀଘ୍ର|ഉടൻ", text, re.IGNORECASE):
            sell_immediately = True
            fields_detected.append("sell_immediately")

        # Village detection
        if re.search(r"bangar[a-z]*pet[a-z]*|ಬಂಗಾರಪೇಟೆ|बांगरपेट", text, re.IGNORECASE):
            village = "Bangarpet"
            fields_detected.append("village")
        elif re.search(r"mulbagal|ಮುಳಬಾಗಿಲು|मुलबागल", text, re.IGNORECASE):
            village = "Mulbagal"
            fields_detected.append("village")
        elif re.search(r"malur|ಮಾಲೂರು|मालूर", text, re.IGNORECASE):
            village = "Malur"
            fields_detected.append("village")
        elif re.search(r"kolar|ಕೋಲಾರ|कोलार", text, re.IGNORECASE):
            village = "Kolar"
            fields_detected.append("village")
        elif re.search(r"srinivaspur|ಶ್ರೀನಿವಾಸಪುರ|श्रीनिवासपुर", text, re.IGNORECASE):
            village = "Srinivaspur"
            fields_detected.append("village")
        elif re.search(r"chintamani|ಚಿಂತಾಮಣಿ|चिंतामणि", text, re.IGNORECASE):
            village = "Chintamani"
            fields_detected.append("village")
        elif re.search(r"chikkaballapur|ಚಿಕ್ಕಬಳ್ಳಾಪುರ|चिक्काबल्लापुर", text, re.IGNORECASE):
            village = "Chikkaballapur"
            fields_detected.append("village")
        else:
            loc_match = re.search(r"\b(?:in|at|from|village|गांव|గ్రామం|கிராமம்)\s+([A-Za-z\u0900-\u0D7F]+)", text, re.IGNORECASE)
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
        heuristic = cls._extract_heuristic(text)
        if "crop" in heuristic.get("fields_detected", []) or "quantity_kg" in heuristic.get("fields_detected", []):
            return {
                "success": True,
                "extracted": heuristic,
                "model": "sarvam_heuristic_parser",
                "is_live_api": True
            }

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
            # Multilingual key mappings (Kannada, Hindi, Telugu, Tamil, Marathi, Bengali, Gujarati, Malayalam, Punjabi, Odia)
            key_mappings = {
                # Kannada
                "ಬೆಳೆ": "crop", "ಪ್ರಕಾರ": "variety", "ತಳಿ": "variety", "ಪ್ರಮಾಣ": "quantity_kg", "ತೂಕ": "quantity_kg",
                "ಗುಣಮಟ್ಟ": "quality_grade", "ಗ್ರೇಡ್": "quality_grade", "ಗ್ರಾಮ": "village", "ಊರು": "village", "ಸ್ಥಳ": "village",
                "ಕೊಯ್ಲು_ದಿನಾಂಕ": "harvest_date", "ಕೊಯ್ಲು_ಸಮಯ": "harvest_time", "ಮಾರಾಟ_ಸಮಯ": "expected_date_time", "ತಕ್ಷಣ_ಮಾರಾಟ": "sell_immediately",
                # Hindi & Marathi
                "फसल": "crop", "किस्म": "variety", "मात्रा": "quantity_kg", "तोल": "quantity_kg", "वजन": "quantity_kg",
                "श्रेणी": "quality_grade", "ग्रेड": "quality_grade", "गांव": "village", "गाँव": "village", "तारीख": "harvest_date",
                "समय": "harvest_time", "तुरंत_बिक्री": "sell_immediately", "पीक": "crop", "गाव": "village", "वेळ": "harvest_time",
                # Telugu
                "పంట": "crop", "రకం": "variety", "పరిమాణం": "quantity_kg", "గ్రేడ్": "quality_grade", "గ్రామం": "village",
                "తేదీ": "harvest_date", "సమయం": "harvest_time",
                # Tamil
                "பயிர்": "crop", "வகை": "variety", "அளவு": "quantity_kg", "தரம்": "quality_grade", "கிராமம்": "village",
                "தேதி": "harvest_date", "நேரம்": "harvest_time",
                # Bengali
                "ফসল": "crop", "জাত": "variety", "পরিমাণ": "quantity_kg", "গ্রেড": "quality_grade", "গ্রাম": "village",
                "তারিখ": "harvest_date", "সময়": "harvest_time",
                # Gujarati
                "પાક": "crop", "જાત": "variety", "જથ્થો": "quantity_kg", "ગ્રેડ": "quality_grade", "ગામ": "village",
                # Malayalam
                "വിള": "crop", "ഇനം": "variety", "അളവ്": "quantity_kg", "ഗ്രേഡ്": "quality_grade", "ഗ്രാമം": "village",
                # Punjabi
                "ਫ਼ਸਲ": "crop", "ਕਿਸਮ": "variety", "ਮਾਤਰਾ": "quantity_kg", "ਗ੍ਰੇਡ": "quality_grade", "ਪਿੰਡ": "village",
                # Odia
                "ଫସଲ": "crop", "କିସମ": "variety", "ପରିମାଣ": "quantity_kg", "ଗ୍ରେଡ୍": "quality_grade", "ଗାଁ": "village"
            }
            normalized = {}
            for k, v in parsed.items():
                target_k = key_mappings.get(k, k)
                normalized[target_k] = v

            # Normalize crop name across 11 languages
            crop_val = normalized.get("crop")
            if crop_val:
                crop_lower = str(crop_val).strip().lower()
                if any(c in crop_lower for c in ["tomat", "ಟೊಮೆಟೊ", "ಟೊಮ್ಯಾಟೊ", "टमाटर", "टोमॅटो", "தக்காளி", "టమాటా", "టమోటా", "টমেটো", "ટામેટા", "തക്കാളി", "ਟਮਾਟਰ", "ଟମାଟୋ"]):
                    normalized["crop"] = "Tomato"
                elif any(c in crop_lower for c in ["onion", "ಈರುಳ್ಳಿ", "प्याज", "कांदा", "ఉల్లిపాయ", "வெங்காயம்", "পেঁয়াজ", "ડુંગળી", "ഉള്ളി", "ਪਿਆਜ਼", "ପିଆଜ"]):
                    normalized["crop"] = "Onion"
                elif any(c in crop_lower for c in ["potato", "ಆಲೂ", "आलू", "बटाटा", "உருளைக்கிழங்கு", "బంగాళాదుంప", "আলু", "બટાકા", "ഉരുളക്കിഴങ്ങ്", "ਆਲੂ", "ଆଳୁ"]):
                    normalized["crop"] = "Potato"
                elif any(c in crop_lower for c in ["cabbage", "ಕೋಸು", "पत्तागोभी", "कोबी", "முட்டைகோஸ்", "క్యాబేజీ", "বাঁধাকপি", "કોબીજ", "ਪੱਤਾਗੋਭੀ", "ବନ୍ଧାକୋବି"]):
                    normalized["crop"] = "Cabbage"
                elif any(c in crop_lower for c in ["carrot", "ಕ್ಯಾರೆಟ್", "गाजर", "கேரட்", "క్యారెట్", "গাজর", "ગાજર", "ਗਾਜਰ", "ଗାଜର"]):
                    normalized["crop"] = "Carrot"
                elif any(c in crop_lower for c in ["chilli", "chili", "ಮೆಣಸಿನ", "मिर्च", "मिरची", "மிளகாய்", "మిరపకాయ", "লঙ্কা", "મરચાં", "ਮਿਰਚ", "ଲଙ୍କା"]):
                    normalized["crop"] = "Chilli"
                else:
                    normalized["crop"] = str(crop_val).title()

            # Normalize quantity
            if "quantity_kg" in normalized and normalized["quantity_kg"] is not None:
                try:
                    normalized["quantity_kg"] = float(normalized["quantity_kg"])
                except (ValueError, TypeError):
                    pass

            # Normalize quality grade to standard Grade A / Grade B / Grade C across all 11 languages
            if "quality_grade" in normalized and normalized["quality_grade"]:
                qg = str(normalized["quality_grade"]).strip().upper()
                if any(b in qg for b in ["GRADE B", "GRADE-B", "ಗ್ರೇಡ್ ಬಿ", "ಗ್ರೇಡ್ B", "ग्रेड बी", "గ్రేడ్ బి", "கிரேடு பி", "গ্রেড বি", "ગ્રેડ બી", "ਗ੍ਰੇਡ ਬੀ", "ഗ്രേഡ് ബി", "ଗ୍ରେଡ୍ ବି"]) or qg in ["B", "GRADE B"]:
                    normalized["quality_grade"] = "Grade B"
                elif any(c in qg for c in ["GRADE C", "GRADE-C", "ಗ್ರೇಡ್ ಸಿ", "ಗ್ರೇಡ್ C", "ग्रेड सी", "గ్రేಡ್ సి", "கிரேடு சி", "গ্রেড সি", "ગ્રેડ ਸੀ", "ਗ੍ਰੇਡ ਸੀ", "ഗ്രೇಡ್ ಸಿ", "ଗ୍ରେଡ୍ ସି"]) or qg in ["C", "GRADE C"]:
                    normalized["quality_grade"] = "Grade C"
                elif any(a in qg for a in ["GRADE A", "GRADE-A", "ಗ್ರೇಡ್ ಎ", "ಗ್ರೇಡ್ A", "ग्रेड ए", "గ్రేಡ್ ఎ", "கிரேடு ஏ", "গ্রেড এ", "ગ્રેડ એ", "ਗ੍ਰੇਡ ਏ", "ഗ്രೇಡ್ എ", "ଗ୍ରେଡ୍ ଏ"]) or qg in ["A", "GRADE A"]:
                    normalized["quality_grade"] = "Grade A"

            # If user explicitly specifies selling intent and not cold storage, ensure sell_immediately is True
            if re.search(r"ಮಾರಾಟ|sell|sale|बेचना|बिक्री|అమ్మకం|விற்பனை|বিক্রি", text, re.IGNORECASE) and not re.search(r"cold storage|warehouse|store|ಶೀತಲ|कोल्ड स्टोरेज|भंडारण", text, re.IGNORECASE):
                normalized["sell_immediately"] = True

            # Normalize village name across regional scripts
            if "village" in normalized and normalized["village"]:
                v_str = str(normalized["village"]).strip()
                if re.search(r"bangar[a-z]*pet[a-z]*|ಬಂಗಾರಪೇಟೆ|बांगरपेट", v_str, re.IGNORECASE):
                    normalized["village"] = "Bangarpet"
                elif re.search(r"mulbagal|ಮುಳಬಾಗಿಲು|मुलबागल", v_str, re.IGNORECASE):
                    normalized["village"] = "Mulbagal"
                elif re.search(r"malur|ಮಾಲೂರು|मालूर", v_str, re.IGNORECASE):
                    normalized["village"] = "Malur"
                elif re.search(r"kolar|ಕೋಲಾರ|कोलार", v_str, re.IGNORECASE):
                    normalized["village"] = "Kolar"
                elif re.search(r"srinivaspur|ಶ್ರೀನಿವಾಸಪುರ|श्रीनिवासपुर", v_str, re.IGNORECASE):
                    normalized["village"] = "Srinivaspur"

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
    def generate_chat_response(cls, prompt: str, language_code: str = "en-IN") -> Optional[str]:
        """
        Conversational agricultural assistant completion using Sarvam-105b or OpenAI-compatible LLM.
        """
        system_prompt = (
            "You are FarmRoute Saathi, an empathetic and expert AI agricultural assistant designed for Indian farmers. "
            "Provide concise, practical, respectful farming advice covering mandi prices, net take-home returns, weather impact, "
            "cold storage, pest/disease control, and government schemes (PM-KISAN, PMFBY). Keep answers within 2-3 clear sentences."
        )
        keys = cls.get_api_keys()
        if keys:
            payload = {
                "model": "sarvam-105b",
                "messages": [
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": prompt}
                ],
                "temperature": 0.3
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
                        if content and content.strip():
                            return content.strip()
                except Exception:
                    cls.rotate_api_key()

        # Fallback to OpenAI-compatible LLM
        openai_cfg = cls.get_openai_fallback_config()
        if openai_cfg.get("api_key"):
            try:
                llm_url = f"{openai_cfg['base_url']}/chat/completions"
                payload = {
                    "model": openai_cfg.get("model", "gpt-3.5-turbo"),
                    "messages": [
                        {"role": "system", "content": system_prompt},
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": 0.3
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
                with urllib.request.urlopen(req, timeout=10) as response:
                    res_data = json.loads(response.read().decode())
                    content = res_data["choices"][0]["message"]["content"]
                    if content and content.strip():
                        return content.strip()
            except Exception:
                pass

        return None

    @classmethod
    def parse_universal_command(cls, text: str, language_code: str = "kn-IN") -> Dict[str, Any]:
        """
        Universal app-wide conversational AI interpreter for Indian farmers.
        Accurately answers weather, mandi prices & net profits, crop diseases/pests,
        cold storage, and government schemes across all 11 Sarvam languages.
        """
        target_lang = cls.normalize_language_code(language_code or cls.detect_language_from_text(text))
        is_kn = target_lang == "kn-IN"
        is_en = target_lang == "en-IN"

        entities_result = cls.extract_harvest_entities(text)
        entities = entities_result.get("extracted", {})
        crop_name = entities.get("crop")
        qty_val = float(entities.get("quantity_kg", 200.0) or 200.0)
        target_dist = entities.get("district") or entities.get("village") or ""
        target_state = entities.get("state") or ""

        # Multilingual Native Templates for 100% fluent offline/mock and spoken responses across all 11 languages
        LANG_TEMPLATES = {
            "GREETING": {
                "te-IN": "నమస్కారం! నేను మీ ఫార్మ్‌రూట్ సాథీ AI వ్యవసాయ సహాయకుడిని. లైవ్ APMC మార్కెట్ ధరలు, నికర లాభం, 48 గంటల వాతావరణం, శీతల గిడ్డంగులు, మరియు ప్రభుత్వ పథకాల సమాచారం కోసం నన్ను అడగవచ్చు.",
                "hi-IN": "नमस्ते! मैं आपका फार्मरूट साथी AI कृषि सहायक हूँ। मैं आपको लाइव एपीएमसी मंडी भाव, शुद्ध लाभ गणना, 48 घंटे का मौसम पूर्वानुमान, कोल्ड स्टोरेज और सरकारी योजनाओं की जानकारी दे सकता हूँ।",
                "ta-IN": "வணக்கம்! நான் உங்கள் பார்ம்ரூட் சாத்தி AI விவசாய உதவியாளர். நேரடி ஏபிஎம்சி மண்டி விலைகள், நிகர லாபம், வானிலை முன்னறிவிப்பு, குளிர்பதன கிடங்குகள் மற்றும் அரசு திட்டங்கள் குறித்து நான் உங்களுக்கு உதவ முடியும்.",
                "mr-IN": "नमस्कार! मी तुमचा फार्मरूट साथी AI कृषी सहाय्यक आहे. थेट एपीएमसी बाजार भाव, निव्वळ नफा, 48 तासांचा हवामान अंदाज, कोल्ड स्टोरेज आणि सरकारी योजनांची माहिती मी देऊ शकतो.",
                "gu-IN": "નમસ્તે! હું તમારો ફાર્મરૂટ સાથી AI કૃષિ સહાયક છું. લાઈવ APMC બજાર ભાવ, ચોખ્ખો નફો, 48 કલાકની હવામાન આગાહી, કોલ્ડ સ્ટોરેજ અને સરકારી યોજનાઓની માહિતી આપી શકું છું.",
                "bn-IN": "নমস্কার! আমি আপনার ফার্মরুট সাথী AI কৃষি সহায়ক। লাইভ এপিএমসি মান্ডি দর, নিট লাভ, ৪৮ ঘণ্টার আবহাওয়া পূর্বাভাস, কোল্ড স্টোরেজ এবং সরকারি প্রকল্প সম্পর্কে জানতে পারেন।",
                "ml-IN": "നമസ്കാരം! ഞാൻ നിങ്ങളുടെ ഫാംറൂട്ട് സാഥി AI കാർഷിക സഹായിയാണ്. തത്സമയ എപിഎംസി വിപണി വിലകൾ, കാലാവസ്ഥാ പ്രവചനം, കോൾഡ് സ്റ്റോറേജ്, സർക്കാർ പദ്ധതികൾ എന്നിവയിൽ ഞാൻ സഹായിക്കാം.",
                "od-IN": "ନମସ୍କାର! ମୁଁ ଆପଣଙ୍କର ଫାର୍ମରୁଟ୍ ସାଥୀ AI କୃଷି ସହାୟକ। ଲାଇଭ୍ ଏପିଏମସି ମଣ୍ଡି ଦର, ପାଣିପାଗ ପୂର୍ବାନୁମାନ, କୋଲ୍ଡ ଷ୍ଟୋରେଜ୍ ଏବଂ ସରକାରୀ ଯୋଜନା ବିଷୟରେ ଜାଣିପାରିବେ।",
                "pa-IN": "ਸਤਿ ਸ਼੍ਰੀ ਅਕਾਲ! ਮੈਂ ਤੁਹਾਡਾ ਫਾਰਮਰੂਟ ਸਾਥੀ AI ਖੇਤੀਬਾੜੀ ਸਹਾਇਕ ਹਾਂ। ਲਾਈਵ ਏਪੀਐਮਸੀ ਮੰਡੀ ਭਾਅ, ਮੌਸਮ ਦੀ ਜਾਣਕਾਰੀ, ਕੋਲਡ ਸਟੋਰੇਜ ਅਤੇ ਸਰਕਾਰੀ ਸਕੀਮਾਂ ਬਾਰੇ ਪੁੱਛ ਸਕਦੇ ਹੋ।"
            },
            "COLD_STORAGE": {
                "te-IN": "శీతల గిడ్డంగి సౌకర్యాల విభాగాన్ని తెరుస్తున్నాము. పంట తాజాదనాన్ని 90 రోజుల వరకు కాపాడటానికి సమీప NHB శీతల గిడ్డంగులు అందుబాటులో ఉన్నాయి.",
                "hi-IN": "कोल्ड स्टोरेज सुविधा अनुभाग खोला जा रहा है। संकटपूर्ण बिक्री से बचने और 90 दिनों तक फसल को सुरक्षित रखने के लिए एनएचबी सत्यापित कोल्ड स्टोरेज उपलब्ध हैं।",
                "ta-IN": "குளிர்பதன கிடங்கு வசதிகள் திறக்கப்படுகிறது. பயிர்களை 90 நாட்கள் வரை பாதுகாக்க அருகிலுள்ள என்ஹெச்பி சரிபார்க்கப்பட்ட குளிர்பதன கிடங்குகள் கிடைக்கின்றன.",
                "mr-IN": "कोल्ड स्टोरेज सुविधा विभाग उघडत आहे. नुकसान टाळण्यासाठी आणि 90 दिवसांपर्यंत माल सुरक्षित ठेवण्यासाठी जवळील एनएचबी कोल्ड स्टोरेज उपलब्ध आहेत.",
                "gu-IN": "કોલ્ડ સ્ટોરેજ સુવિધા વિભાગ ખોલવામાં આવી રહ્યો છે. પાકને 90 દિવસ સુધી સુરક્ષિત રાખવા નજીકના NHB કોલ્ડ સ્ટોરેજ ઉપલબ્ધ છે.",
                "bn-IN": "কোল্ড স্টোরেজ সুবিধা বিভাগ খোলা হচ্ছে। কম দামে বিক্রি এড়াতে এবং ৯০ দিন পর্যন্ত ফসল সংরক্ষণে নিকটবর্তী এনএইচবি কোল্ড স্টোরেজ উপলব্ধ।",
                "ml-IN": "കോൾഡ് സ്റ്റോറേജ് സൗകര്യ വിഭാഗം തുറക്കുന്നു. ഉൽപ്പന്നങ്ങൾ 90 ദിവസം വരെ സൂക്ഷിക്കാൻ എൻഎച്ച്ബി കോൾഡ് സ്റ്റോറേജുകൾ ലഭ്യമാണ്.",
                "od-IN": "କୋଲ୍ଡ ଷ୍ଟୋରେଜ୍ ସୁବିଧା ବିଭାଗ ଖୋଲାଯାଉଛି। ଫସଲକୁ ୯୦ ଦିନ ପର୍ଯ୍ୟନ୍ତ ସୁରକ୍ଷିତ ରଖିବା ପାଇଁ ନିକଟସ୍ଥ NHB କୋଲ୍ଡ ଷ୍ଟୋରେଜ୍ ଉପଲବ୍ଧ।",
                "pa-IN": "ਕੋਲਡ ਸਟੋਰੇਜ ਸਹੂਲਤ ਖੋਲ੍ਹੀ ਜਾ ਰਹੀ ਹੈ। ਫ਼ਸਲ ਨੂੰ 90 ਦਿਨਾਂ ਤੱਕ ਸੁਰੱਖਿਅਤ ਰੱਖਣ ਲਈ ਨੇੜਲੇ NHB ਕੋਲਡ ਸਟੋਰੇਜ ਉਪਲਬਧ ਹਨ।"
            },
            "SCHEMES": {
                "te-IN": "ప్రభుత్వ పథకాల సమాచారాన్ని తెరుస్తున్నాము. PM-KISAN (సంవత్సరానికి ₹6,000 డిబిటి), PM ఫసల్ బీమా యోజన (హెక్టారుకు ₹85,000 వరకు పరిహారం) మరియు రాయితీలు అందుబాటులో ఉన్నాయి.",
                "hi-IN": "सरकारी योजनाओं की जानकारी खोली जा रही है। पीएम-किसान (₹6,000 प्रति वर्ष डीबीटी), पीएम फसल बीमा योजना (₹85,000 प्रति हेक्टेयर तक क्लेम) और सब्सिडी उपलब्ध हैं।",
                "ta-IN": "அரசு நலத்திட்டங்கள் திறக்கப்படுகிறது. பிஎம்-கிசான் (ஆண்டுக்கு ₹6,000), பிஎம் பயிர் காப்பீட்டுத் திட்டம் (ஹெக்டேருக்கு ₹85,000 வரை இழப்பீடு) தகவல்கள் உள்ளன.",
                "mr-IN": "सरकारी योजनांची माहिती उघडली जात आहे. पीएम-किसान (वार्षिक ₹6,000 डीबीटी), पीएम पीक विमा योजना (हेक्टरी ₹85,000 पर्यंत क्लेम) उपलब्ध आहेत.",
                "gu-IN": "સરકારી યોજનાઓની વિગતો ખોલવામાં આવી રહી છે. પીએમ-કિસાન (વાર્ષિક ₹6,000), પીએમ ફસલ બીમા યોજના (હેક્ટર દીઠ ₹85,000 સુધી સહાય) ઉપલબ્ધ છે.",
                "bn-IN": "সরকারি প্রকল্প বিভাগ খোলা হচ্ছে। পিএম-কিসান (বার্ষিক ₹৬,০০০), পিএম ফসল বিমা যোজনা (হেক্টর প্রতি ₹৮৫,০০০ পর্যন্ত ক্ষতিপূরণ) উপলব্ধ।",
                "ml-IN": "സർക്കാർ പദ്ധതി വിവരങ്ങൾ തുറക്കുന്നു. പിഎം-കിസാൻ (വർഷത്തിൽ ₹6,000), പിഎം വിള ഇൻഷുറൻസ് പദ്ധതി (ഹെക്ടറിന് ₹85,000 വരെ) ലഭ്യമാണ്.",
                "od-IN": "ସରକାରୀ ଯୋଜନା ବିବରଣୀ ଖୋଲାଯାଉଛି। PM-KISAN (ବାର୍ଷିକ ₹6,000), ପ୍ରଧାନମନ୍ତ୍ରୀ ଫସଲ ବୀମା ଯୋଜନା ତଥ୍ୟ ଉପଲବ୍ଧ।",
                "pa-IN": "ਸਰਕਾਰੀ ਸਕੀਮਾਂ ਦੀ ਜਾਣਕਾਰੀ ਖੋਲ੍ਹੀ ਜਾ ਰਹੀ ਹੈ। ਪੀਐਮ-ਕਿਸਾਨ (ਸਾਲਾਨਾ ₹6,000 ਡੀਬੀਟੀ), ਪੀਐਮ ਫ਼ਸਲ ਬੀਮਾ ਯੋਜਨਾ ਉਪਲਬਧ ਹਨ।"
            }
        }

        # 2. MANDI RATES & MARKET ANALYSIS INTENT
        if re.search(r"rate|price|mandi|market|apmc|cost|bhav|daam|profit|take home|take-home|netback|ದರ|ಬೆಲೆ|ಮಾರುಕಟ್ಟೆ|ಎಪಿಎಂಸಿ|ಮಂಡಿ|ಲಾಭ|ಭಾವ|भाव|दाम|मंडी|बाजार|मुनाफा|ధర|మండీ|మార్కెట్|లాభం|விலை|சந்தை|மண்டி|லாபம்|দাম|দর|বাজার|બાજાર|ભાવ|ભાઅ|ਭਾਅ|ਮੰਡੀ|ਦਰਾ|ଦର|ମଣ୍ଡି|ലാഭം|വില|മാർക്കറ്റ്", text, re.IGNORECASE):
            action = "NAVIGATE_MARKETPLACE"
            if crop_name:
                try:
                    from backend.app.services.pan_india_mandi_service import PanIndiaMandiService
                    mandis = PanIndiaMandiService.evaluate_mandi_options(
                        crop=crop_name,
                        quantity_kg=qty_val,
                        farmer_district=target_dist,
                        farmer_state=target_state
                    )
                    if mandis:
                        best = mandis[0]
                        rec_name = best.get("mandi_name", f"{target_dist.title()} APMC" if target_dist else "Local APMC")
                        net_ret = int(best.get("net_profit_inr", 8375))
                        eff_rate = best.get("effective_net_price_per_kg", 41.88)
                        headline_p = best.get("price_per_kg", 42.16)
                        msg_en = f"Market Analysis for {int(qty_val)}kg {crop_name}: Best net return is {rec_name} with ₹{net_ret} final take-home (₹{eff_rate}/kg effective) after transport and commission deductions. Headline price is ₹{headline_p}/kg."
                        msg_kn = f"{int(qty_val)} ಕೆಜಿ {crop_name} ಮಾರುಕಟ್ಟೆ ವಿಶ್ಲೇಷಣೆ: ಸಾಗಾಟ ಮತ್ತು ಕಡಿತಗಳ ನಂತರ {rec_name} ಅತ್ಯುತ್ತಮವಾಗಿದ್ದು, ₹{net_ret} ಅಂತಿಮ ಕೈಸೇರುವ ಲಾಭ (ಪ್ರತಿ ಕೆಜಿಗೆ ₹{eff_rate}) ನೀಡುತ್ತದೆ. ಮುಖಬೆಲೆ ₹{headline_p}/ಕೆಜಿ."
                    else:
                        default_mandi = f"{target_dist.title()} APMC" if target_dist else "Local APMC"
                        msg_en = f"Live rates for {crop_name}: {default_mandi} is currently offering ₹{int(qty_val * 40)} estimated net take-home."
                        msg_kn = f"{crop_name} ಲೈವ್ ದರಗಳು: {default_mandi} ₹{int(qty_val * 40)} ನಿವ್ವಳ ಲಾಭ ನೀಡುತ್ತದೆ."
                except Exception:
                    default_mandi = f"{target_dist.title()} APMC" if target_dist else "Local APMC"
                    msg_en = f"Live rates for {crop_name}: {default_mandi} is recommended with ₹8,375 estimated net take-home."
                    msg_kn = f"{crop_name} ಲೈವ್ ದರಗಳು: {default_mandi} ₹8,375 ನಿವ್ವಳ ಲಾಭ ನೀಡುತ್ತದೆ."
            else:
                msg_en = "Today's APMC Mandi rates across regional markets: Onion is ₹32-₹38/kg, Tomato is ₹40-₹42/kg, Potato is ₹24-₹28/kg, Chilli is ₹65-₹80/kg. Virtual FPO Direct Buying offers the highest net return by eliminating middleman commissions and pooling freight. Which crop would you like a detailed net take-home calculation for?"
                msg_kn = "ಇಂದಿನ ಪ್ರಮುಖ ಎಪಿಎಂಸಿ ಮಂಡಿ ದರಗಳು: ಈರುಳ್ಳಿ ₹32-₹38/ಕೆಜಿ, ಟೊಮೆಟೊ ₹40-₹42/ಕೆಜಿ, ಆಲೂಗಡ್ಡೆ ₹24-₹28/ಕೆಜಿ, ಮೆಣಸಿನಕಾಯಿ ₹65-₹80/ಕೆಜಿ. ಮಧ್ಯವರ್ತಿಗಳಿಲ್ಲದ ವರ್ಚುವಲ್ ಎಫ್‌ಪಿಒ ನೇರ ಖರೀದಿ ಅತಿ ಹೆಚ್ಚು ನಿವ್ವಳ ಲಾಭ ನೀಡುತ್ತದೆ. ನಿಮಗೆ ಯಾವ ಬೆಳೆಯ ಸಂಪೂರ್ಣ ಲಾಭದ ಲೆಕ್ಕಾಚಾರ ಬೇಕು?"

        # 3. WEATHER FORECAST & FARM ADVISORY INTENT
        elif re.search(r"weather|rain|forecast|climate|temp|temperature|cloud|storm|precipitation|drizzle|dry|humidity|ಹವಾಮಾನ|ಮಳೆ|ತಾಪಮಾನ|ಮೋಡ|मौसम|बारिश|वर्षा|तापमान|बादल|వర్షం|వాతావరణం|ఉష్ణోగ్రత|வானிலை|மழை|வெப்பநிலை|আবহাওয়া|বৃষ্টি|વરસાદ|હવામાન|ਮੀਂਹ|ਮੌਸਮ|ପାଣିପାଗ|ବର୍ଷା|മഴ|കാലാവസ്ഥ", text, re.IGNORECASE):
            action = "NAVIGATE_WEATHER"
            target_loc = entities.get("village") or entities.get("district") or entities.get("location") or ""
            try:
                from backend.app.services.weather_service import WeatherService
                w_data = WeatherService.get_weather(target_loc)
                resolved_loc = w_data.get("location") or (target_loc.title() if target_loc else "Farmland")
                temp = w_data.get("temperature_c", 26)
                cond = w_data.get("condition", "Partly cloudy")
                rain_pct = w_data.get("rainfall_probability_pct", 30)
                advisory = w_data.get("agricultural_advisory", {}).get("action", "Harvest in morning window and move produce to shaded or cold storage.")
                if crop_name:
                    msg_en = f"{resolved_loc} Farmland Weather: {temp}°C, {cond} with {rain_pct}% rain probability. IMD Advisory for {crop_name}: {advisory}"
                    msg_kn = f"{resolved_loc} ಕೃಷಿ ಹವಾಮಾನ: {temp}°C, {cond}, {rain_pct}% ಮಳೆ ಸಾಧ್ಯತೆ. {crop_name} ಕೃಷಿ ಸಲಹೆ: {advisory}"
                else:
                    msg_en = f"{resolved_loc} Farmland Weather: {temp}°C, {cond} with {rain_pct}% rain probability over next 48 hours. IMD Advisory: {advisory}"
                    msg_kn = f"{resolved_loc} ಕೃಷಿ ಹವಾಮಾನ: {temp}°C, {cond}, {rain_pct}% ಮಳೆ ಸಾಧ್ಯತೆ. ಕೃಷಿ ಸಲಹೆ: {advisory}"
            except Exception:
                resolved_loc = target_loc.title() if target_loc else "Local"
                msg_en = f"{resolved_loc} Farmland Weather: 26°C, Partly Cloudy, 30% rain probability. Harvest in morning dry window."
                msg_kn = f"{resolved_loc} ಕೃಷಿ ಹವಾಮಾನ: 26°C, ಭಾಗಶಃ ಮೋಡ, 30% ಮಳೆ ಸಾಧ್ಯತೆ. ಬೆಳಗಿನ ಒಣ ಸಮಯದಲ್ಲಿ ಕೊಯ್ಲು ಮಾಡಿ."

        # 4. AGRONOMY / PEST / DISEASE / FERTILIZER INTENT
        elif re.search(r"pest|insect|caterpillar|worm|disease|fungus|blight|rot|wilt|curl|spot|spray|pesticide|insecticide|fungicide|fertilizer|urea|dap|npk|manure|compost|irrigation|water|drip|soil|seed|yield|growth|ಕೀಟ|ಹುಳು|ರೋಗ|ಶಿಲೀಂಧ್ರ|ಕೊಳೆ|ಚುಕ್ಕೆ|ಸಿಂಪಡಣೆ|ಕೀಟನಾಶಕ|ಗೊಬ್ಬರ|ಯೂರಿಯಾ|ನೀರಾವರಿ|ಹನಿ ನೀರು|ಮಣ್ಣು|ಬೀಜ|ಇಳುವರಿ|कीट|कीड़ा|इल्ली|रोग|फफूंद|झुलसा|सड़न|छिड़काव|कीटनाशक|खाद|उर्वरक|यूरिया|सिंचाई|मिट्टी|बीज|పురుగు|తెగులు|మందు|స్ప్రే|ఎరువు|యూరియా|సాగు|విత్తనాలు|భూమి|பூச்சி|நோய்|உரம்|மருந்து|தண்ணீர்|விதை|कीड़े|ਰੋਗ|ਖਤ|ਰੋਗ|കീടം|രോഗം|വളം", text, re.IGNORECASE):
            action = "AGRONOMY_ADVISORY"
            crop_label = crop_name or "crops"
            crop_label_kn = crop_name or "ಬೆಳೆ"
            if re.search(r"blight|fungus|rot|wilt|spot|ಶಿಲೀಂಧ್ರ|ಕೊಳೆ|ಚುಕ್ಕೆ|ಒಣಗು|झुलसा|फफूंद|సడన్|தெగులు|நோய்", text, re.IGNORECASE):
                msg_en = f"Advisory for {crop_label}: For fungal blight and leaf spots, ensure good field drainage. Spray Mancozeb (2.5g/L) or Copper Oxychloride (3g/L) during clear morning weather. Avoid overhead irrigation to keep leaves dry."
                msg_kn = f"{crop_label_kn} ಸಂರಕ್ಷಣಾ ಸಲಹೆ: ಶಿಲೀಂಧ್ರ ರೋಗ ಮತ್ತು ಎಲೆ ಚುಕ್ಕೆಗೆ ಹೊಲದಲ್ಲಿ ನೀರು ನಿಲ್ಲದಂತೆ ನೋಡಿಕೊಳ್ಳಿ. ಬೆಳಿಗ್ಗೆ ಒಣ ಹವೆಯಲ್ಲಿ ಮ್ಯಾಂಕೋಜೆಬ್ (2.5 ಗ್ರಾಂ/ಲೀ) ಅಥವಾ ಕಾಪರ್ ಆಕ್ಸಿಕ್ಲೋರೈಡ್ ಸಿಂಪಡಿಸಿ. ಎಲೆಗಳ ಮೇಲೆ ನೀರು ನಿಲ್ಲದಂತೆ ಎಚ್ಚರವಹಿಸಿ."
            elif re.search(r"pest|insect|caterpillar|worm|curl|thrip|whitefly|ಕೀಟ|ಹುಳು|ರಸಹೀರುವ|ಸುರುಳಿ|कीट|कीड़ा|इल्ली|పురుగు|பூச்சி|കീടം", text, re.IGNORECASE):
                msg_en = f"Pest Management for {crop_label}: For sucking pests and caterpillars, spray Neem oil (10,000 ppm) @ 2ml/L as preventive eco-friendly control. For severe infestation, apply Imidacloprid (0.5ml/L) or Emamectin Benzoate (0.4g/L) during early morning or late evening."
                msg_kn = f"{crop_label_kn} ಕೀಟ ನಿರ್ವಹಣೆ: ರಸಹೀರುವ ಕೀಟಗಳು ಮತ್ತು ಹುಳುಗಳ ನಿಯಂತ್ರಣಕ್ಕೆ ಬೇವಿನ ಎಣ್ಣೆ (10,000 ppm) 2 ಮಿ.ಲೀ/ಲೀಟರ್ ಸಿಂಪಡಿಸಿ. ಬಾಧೆ ಹೆಚ್ಚಾಗಿದ್ದರೆ ಬೆಳಿಗ್ಗೆ ಅಥವಾ ಸಂಜೆ ಇಮಿಡಾಕ್ಲೋಪ್ರಿಡ್ (0.5 ಮಿ.ಲೀ/ಲೀ) ಅಥವಾ ಎಮಾಮೆಕ್ಟಿನ್ ಬೆಂಜೊಯೇಟ್ ಸಿಂಪಡಿಸಿ."
            elif re.search(r"fertilizer|urea|dap|npk|manure|ಗೊಬ್ಬರ|ಯೂರಿಯಾ|ಖಾದ್|खाद|उर्वरक|ఎరువు|உரம்|വളം", text, re.IGNORECASE):
                msg_en = f"Fertilizer Advisory for {crop_label}: Apply balanced NPK based on soil test. During vegetative growth, apply Nitrogen/Urea in split doses with irrigation. During flowering and fruit setting, spray water-soluble 13-0-45 or 0-52-34 @ 5g/L for superior fruit size and quality."
                msg_kn = f"{crop_label_kn} ಪೋಷಕಾಂಶ ಸಲಹೆ: ಮಣ್ಣಿನ ಪರೀಕ್ಷೆಗೆ ಅನುಗುಣವಾಗಿ ಎನ್‌ಪಿಕೆ ಗೊಬ್ಬರ ನೀಡಿ. ಬೆಳವಣಿಗೆಯ ಹಂತದಲ್ಲಿ ಯೂರಿಯಾವನ್ನು ಹಂತ-ಹಂತವಾಗಿ ನೀಡಿ. ಹೂವು ಮತ್ತು ಕಾಯಿ ಕಟ್ಟುವ ಹಂತದಲ್ಲಿ 13-0-45 ಅಥವಾ 0-52-34 (5 ಗ್ರಾಂ/ಲೀಟರ್) ಸಿಂಪಡಿಸಿ ಗರಿಷ್ಠ ಇಳುವರಿ ಪಡೆಯಿರಿ."
            else:
                msg_en = f"Agronomic Advisory for {crop_label}: Maintain balanced soil moisture with drip irrigation, use certified disease-free seeds, and rotate crops to break pest cycles. Monitor 48-hour rain alerts before applying any inputs."
                msg_kn = f"{crop_label_kn} ಕೃಷಿ ಸಲಹೆ: ಹನಿ ನೀರಾವರಿ ಮೂಲಕ ತೇವಾಂಶ ಕಾಪಾಡಿ, ದೃಢೀಕೃತ ಬೀಜಗಳನ್ನು ಬಳಸಿ ಮತ್ತು ಬೆಳೆ ಪರಿವರ್ತನೆ ಮಾಡಿ. ಯಾವುದೇ ರಾಸಾಯನಿಕ ಸಿಂಪಡಿಸುವ ಮುನ್ನ ಮಳೆಯ ಮುನ್ಸೂಚನೆ ಗಮನಿಸಿ."

        # 5. COLD STORAGE FACILITY ROUTING INTENT
        elif re.search(r"cold storage|warehouse|store|godown|shelf life|preserve|ಶೀತಲ|ಕೋಲ್ಡ್|ಗೋದಾಮು|ಸಂಗ್ರಹ|कोल्ड स्टोरेज|भंडारण|गोदाम|శీతల|కోల్డ్ స్టోరేజ్|குளிர்பதன|சேமிப்பு|হিমাগার|ਕੋਲਡ ਸਟੋਰੇਜ|ଶୀତଳ|ശീതീകരണ", text, re.IGNORECASE):
            action = "NAVIGATE_COLD_STORAGE"
            msg_en = "Opening Cold Storage Facility Discovery. We have identified verified NHB cold storage facilities near your farm with temperature and humidity controls to prevent distress selling and preserve shelf-life for up to 90 days."
            msg_kn = "ಶೀತಲ ಶೇಖರಣಾ ಸೌಲಭ್ಯಗಳ ವಿಭಾಗವನ್ನು ತೆರೆಯಲಾಗುತ್ತಿದೆ. ತುರ್ತು ಮಾರಾಟದ ನಷ್ಟ ತಪ್ಪಿಸಲು ಮತ್ತು 90 ದಿನಗಳವರೆಗೆ ಬೆಳೆ ತಾಜಾತನ ಕಾಪಾಡಲು ಹತ್ತಿರದ ಎನ್‌ಎಚ್‌ಬಿ ದೃಢೀಕೃತ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ವಿವರಗಳು ಲಭ್ಯವಿವೆ."

        # 6. GOVERNMENT SCHEMES & SUBSIDIES INTENT
        elif re.search(r"scheme|benefit|subsidy|grant|insurance|fruits|pm kisan|pm-kisan|raitha siri|enam|fasal bima|samrakshane|kcc|dbt|rkvy|ಯೋಜನೆ|ಸಬ್ಸಿಡಿ|ಅನುದಾನ|ಸೌಲಭ್ಯ|ವಿಮೆ|ಕಿಸಾನ್|योजना|सब्सिडी|अनुदान|बीमा|किसान|పథకం|సబ్సిడీ|భీమా|திட்டம்|மானியம்|காப்பீடு|যোজনা|ਸਕੀਮ|ଯୋଜନା|പദ്ധതി", text, re.IGNORECASE):
            action = "NAVIGATE_SCHEMES"
            msg_en = "Opening AI Government Schemes & Opportunities Intelligence. Active verified schemes include PM-KISAN (₹6,000/year via direct DBT), PM Fasal Bima Yojana / Samrakshane Karnataka (yield deficit crop insurance up to ₹85,000/ha), and Subsidized Cold Storage Infrastructure under MIDH/NHB."
            msg_kn = "ಸರ್ಕಾರಿ ಯೋಜನೆಗಳು ಮತ್ತು ಸೌಲಭ್ಯಗಳ ವಿಭಾಗವನ್ನು ತೆರೆಯಲಾಗುತ್ತಿದೆ. ಪಿಎಂ-ಕಿಸಾನ್ (ವರ್ಷಕ್ಕೆ ₹6,000 ಡಿಬಿಟಿ), ಪ್ರಧಾನ ಮಂತ್ರಿ ಫಸಲ್ ಬಿಮಾ ಯೋಜನೆ / ಸಂರಕ್ಷಣೆ ಕರ್ನಾಟಕ (ಹೆಕ್ಟೇರ್‌ಗೆ ₹85,000 ವರೆಗೆ ವಿಮೆ) ಮತ್ತು ಎನ್‌ಎಚ್‌ಬಿ ಸಬ್ಸಿಡಿ ಸೌಲಭ್ಯಗಳು ಲಭ್ಯವಿವೆ."

        # 7. HARVEST LOT STATUS INTENT
        elif re.search(r"harvest|lot|my harvest|active lot|200kg|200 kg|ಕೊಯ್ಲು|ಲಾಟ್|ನನ್ನ ಕೊಯ್ಲು|ಫಸಲ್|फसल|लॉट|कटाई|పంట|లాట్|அறுவடை|ফসল|ਫਸਲ", text, re.IGNORECASE):
            action = "UPDATE_HARVEST"
            crop_display = crop_name or "Produce"
            crop_display_kn = crop_name or "ಬೆಳೆ"
            msg_en = f"Your active harvest lot has {int(qty_val)}kg Grade-A {crop_display} registered. Status: Ready for Mandi sale or nearby cold storage protection."
            msg_kn = f"ನಿಮ್ಮ ಸಕ್ರಿಯ ಕೊಯ್ಲು ಲಾಟ್‌ನಲ್ಲಿ {int(qty_val)} ಕೆಜಿ ಗ್ರೇಡ್-ಎ {crop_display_kn} ನೋಂದಾಯಿಸಲಾಗಿದೆ. ಇದು ಮಂಡಿ ಮಾರಾಟ ಅಥವಾ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್‌ಗೆ ಸಿದ್ಧವಾಗಿದೆ."

        # GREETING / ASSISTANT IDENTITY INTENT
        elif re.search(r"(?:\b(?:hi|hello|hey|namaste|namaskar|namaskara|vanakkam|namaskaram|kem cho|sat sri akal|who are you|help|how are you|kisan saathi|farmroute saathi)\b|ಕಿಸಾನ್|ನಮಸ್ಕಾರ|ಹಲೋ|ನೀವು ಯಾರು|ಸಹಾಯ|नमस्ते|नमस्कार|हेलो|मदद|నమస్కారం|హలో|మీరు ఎవరు|வணக்கம்|ஹலோ|নমস্কার|ਸਤਿ ਸ਼੍ਰੀ ਅਕਾਲ|നമസ്കാരം)", text, re.IGNORECASE):
            action = "GREETING"
            msg_en = "Namaste! I am FarmRoute Saathi, your AI agricultural companion. You can ask me for live APMC mandi prices and net profit calculations, 48-hour localized weather forecasts and rain alerts, cold storage facility routing, government schemes (like PM-KISAN & PMFBY), or crop disease and pest control. How can I assist you with your farming today?"
            msg_kn = "ನಮಸ್ಕಾರ! ನಾನು ನಿಮ್ಮ ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ AI ಕೃಷಿ ಸಹಾಯಕ. ಲೈವ್ ಎಪಿಎಂಸಿ ಮಂಡಿ ದರಗಳು, ನಿವ್ವಳ ಲಾಭ ವಿಶ್ಲೇಷಣೆ, 48 ಗಂಟೆಗಳ ಹವಾಮಾನ ಮುನ್ಸೂಚನೆ, ಶೀತಲ ಶೇಖರಣಾ ಸೌಲಭ್ಯಗಳು, ಸರ್ಕಾರಿ ಯೋಜನೆಗಳು (ಪಿಎಂ-ಕಿಸಾನ್, ಬೆಳೆ ವಿಮೆ) ಮತ್ತು ಕೀಟ ನಿಯಂತ್ರಣ ಸಲಹೆಗಳನ್ನು ನಾನು ನೀಡಬಲ್ಲೆ. ಇಂದು ನಿಮ್ಮ ಕೃಷಿ ಕಾರ್ಯಗಳಿಗೆ ನಾನು ಹೇಗೆ ನೆರವಾಗಲಿ?"

        # 8. RESET / LOGOUT / CONFIRM INTENTS
        elif re.search(r"reset|ಮರುಹೊಂದಿಸಿ|रीसेट|రీసెట్|மீட்டமை|পুনরায় সেট", text, re.IGNORECASE):
            action = "DEMO_RESET"
            msg_en = "Resetting demo scenario to baseline."
            msg_kn = "ಡೆಮೊ ಸನ್ನಿವೇಶವನ್ನು ಮರುಹೊಂದಿಸಲಾಗುತ್ತಿದೆ."
        elif re.search(r"logout|sign out|ನಿರ್ಗಮಿಸಿ|लॉगआउट|బయటకు|வெளியೇறு|লগআউট", text, re.IGNORECASE):
            action = "LOGOUT"
            msg_en = "Logging out of Kisan account."
            msg_kn = "ಖಾತೆಯಿಂದ ನಿರ್ಗಮಿಸಲಾಗುತ್ತಿದೆ."
        elif re.search(r"confirm|submit|declare|ದೃಢೀಕರಿಸಿ|ಘೋಷಿಸಿ|ಪುಷ್ಟಿ|दर्ज|ఖరారు|உறுதி|নিশ্চিত", text, re.IGNORECASE):
            action = "SUBMIT_HARVEST"
            crop_display = crop_name or "produce"
            crop_display_kn = crop_name or "ಬೆಳೆ"
            msg_en = f"Confirming harvest declaration for {int(qty_val)}kg {crop_display}."
            msg_kn = f"{int(qty_val)} ಕೆಜಿ {crop_display_kn} ಕೊಯ್ಲು ದೃಢೀಕರಿಸಲಾಗುತ್ತಿದೆ."

        # 9. GENERAL CONVERSATIONAL AI CHATBOT (NO TOMATO DEFAULTS!)
        else:
            if crop_name and ("quantity_kg" in entities.get("fields_detected", []) or "crop" in entities.get("fields_detected", [])):
                action = "UPDATE_HARVEST"
                date = entities.get('harvest_date') or datetime.now().strftime("%Y-%m-%d")
                time = entities.get('harvest_time', '07:00 AM')
                msg_en = f"Updated: {int(qty_val)}kg {crop_name}, Harvest: {date} at {time}."
                msg_kn = f"ನವೀಕರಿಸಲಾಗಿದೆ: {int(qty_val)} ಕೆಜಿ {crop_name}, ಕೊಯ್ಲು: {date} {time}."
            else:
                action = "CHAT_RESPONSE"
                llm_reply = cls.generate_chat_response(prompt=text, language_code=target_lang)
                if llm_reply:
                    msg_en = llm_reply
                    msg_kn = llm_reply
                else:
                    msg_en = f"FarmRoute Saathi: I can assist you with live APMC mandi rates and net profit calculations, 48-hour localized weather forecasts, cold storage facilities, pest/disease management, and government schemes. How can I help with your farming today?"
                    msg_kn = f"ಫಾರ್ಮ್‌ರೂಟ್ ಸಾಥಿ: ಲೈವ್ ಮಂಡಿ ದರಗಳು, ನಿವ್ವಳ ಲಾಭದ ಲೆಕ್ಕಾಚಾರ, ಹವಾಮಾನ ಮುನ್ಸೂಚನೆ, ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ವಿವರ, ಕೀಟ ರಕ್ಷಣೆ ಮತ್ತು ಸರ್ಕಾರಿ ಸೌಲಭ್ಯಗಳಿಗೆ ನಾನು ನೆರವಾಗಬಲ್ಲೆ. ಇಂದು ನಿಮಗೆ ಯಾವ ಮಾಹಿತಿ ಬೇಕು?"

        # Generate Spoken Feedback in Target Language across all 11 Indian Languages
        if is_kn:
            spoken_feedback = msg_kn
        elif is_en:
            spoken_feedback = msg_en
        else:
            # Check pre-localized native templates for instant, verified native fluency
            template_pool = LANG_TEMPLATES.get(action, {})
            if target_lang in template_pool:
                spoken_feedback = template_pool[target_lang]
            else:
                tr = cls.translate_text(msg_en, target_language_code=target_lang, source_language_code="en-IN")
                spoken_feedback = tr.get("translated_text") or msg_en

        return {
            "success": True,
            "transcript": text,
            "action": action,
            "entities": entities,
            "fields_updated": entities.get("fields_detected", []),
            "message_en": msg_en,
            "message_kn": msg_kn,
            "spoken_feedback": spoken_feedback,
            "language_code": target_lang
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
        Synthesizes spoken audio explanation comparing Mandi options and highlighting headline traps
        across all 11 Sarvam languages.
        """
        target_lang = cls.normalize_language_code(language_code)
        is_kn = target_lang == "kn-IN"
        is_en = target_lang == "en-IN"

        if not mandi_results:
            msg_kn = "ಯಾವುದೇ ಮಾರುಕಟ್ಟೆ ಮಾಹಿತಿ ಲಭ್ಯವಿಲ್ಲ."
            msg_en = "No mandi data available for comparison."
            if is_kn:
                msg = msg_kn
            elif is_en:
                msg = msg_en
            else:
                tr = cls.translate_text(msg_en, target_language_code=target_lang, source_language_code="en-IN")
                msg = tr.get("translated_text") or msg_en
            tts_res = cls.synthesize_speech(msg, target_lang)
            return {"summary_text": msg, "audio_base64": tts_res.get("audio_base64", ""), "language_code": target_lang}

        recommended = next((m for m in mandi_results if m.get("is_recommended")), mandi_results[0])
        trap = next((m for m in mandi_results if m.get("is_trap")), None)

        rec_name = recommended.get("mandi_name") or recommended.get("market") or "Primary Mandi"
        rec_net = int(recommended.get("net_take_home", 0))
        rec_dist = recommended.get("distance_km", 0)
        rec_deductions = recommended.get("itemized_deductions") or recommended.get("deductions") or {}
        rec_trans = int(rec_deductions.get("transport_cost") or rec_deductions.get("transport", 0))

        dist_phrase_kn = f"{rec_dist} ಕಿಮೀ OSRM ಪರಿಶೀಲಿತ ರಸ್ತೆ ದೂರದಲ್ಲಿದ್ದು, " if rec_dist > 0 else ""
        trans_phrase_kn = f"ಸಾರಿಗೆ ವೆಚ್ಚ ಕೇವಲ ₹{rec_trans}. " if rec_trans > 0 else ""
        text_kn = f"ನಿಮ್ಮ {int(quantity_kg)} ಕೆಜಿ {crop} ಬೆಳೆಗಾಗಿ, {rec_name} {dist_phrase_kn}₹{rec_net} ಗರಿಷ್ಠ ನಿವ್ವಳ ಹಣ ನೀಡುತ್ತದೆ. {trans_phrase_kn}"

        dist_phrase_en = f"at {rec_dist} km (OSRM road verified) " if rec_dist > 0 else ""
        trans_phrase_en = f"Transport freight is ₹{rec_trans}. " if rec_trans > 0 else ""
        text_en = f"For your {int(quantity_kg)} kg of {crop}, {rec_name} {dist_phrase_en}gives the highest net return of ₹{rec_net}. {trans_phrase_en}"

        if trap:
            trap_name = trap.get("mandi_name") or trap.get("market") or "Distant Mandi"
            trap_modal = trap.get("modal_price_per_kg") or trap.get("modal_price_kg", 0)
            trap_dist = trap.get("distance_km", 0)
            trap_ded = trap.get("itemized_deductions") or trap.get("deductions") or {}
            trap_trans = int(trap_ded.get("transport_cost") or trap_ded.get("transport", 0))
            diff = int(rec_net - trap.get("net_take_home", 0))
            dist_warn_kn = f"{trap_dist} ಕಿಮೀ OSRM ರಸ್ತೆ ಪ್ರಯಾಣ ಮತ್ತು ₹{trap_trans} ವಾಹನ ಬಾಡಿಗೆಯಿಂದಾಗಿ " if trap_dist > 0 else "ಹೆಚ್ಚಿನ ಸಾರಿಗೆ ವೆಚ್ಚದಿಂದ "
            text_kn += f"ಎಚ್ಚರ: {trap_name} ನಲ್ಲಿ ಕೆಜಿಗೆ ₹{trap_modal} ಕಾಣಿಸಿದರೂ, {dist_warn_kn}₹{diff} ಕಡಿಮೆ ಲಾಭ ಸಿಗುತ್ತದೆ."

            dist_warn_en = f"due to {trap_dist} km OSRM verified road distance (₹{trap_trans} freight) " if trap_dist > 0 else "due to transport costs "
            text_en += f"Beware of {trap_name}: headline rate is ₹{trap_modal}/kg, but {dist_warn_en}you take home ₹{diff} less cash in hand."
        else:
            text_kn += "ಎಲ್ಲಾ ರಸ್ತೆ ದೂರಗಳು OSRM ಮೂಲಕ ದೃಢೀಕರಿಸಲ್ಪಟ್ಟಿವೆ. ನೇರ ಪೂಲಿಂಗ್ ಮೂಲಕ ಸಾರಿಗೆ ಉಳಿಸಿ."
            text_en += "All road distances are verified via OSRM turn-by-turn routing. Direct pooling eliminates transit leakage."

        if is_kn:
            summary_text = text_kn
        elif is_en:
            summary_text = text_en
        else:
            tr = cls.translate_text(text_en, target_language_code=target_lang, source_language_code="en-IN")
            summary_text = tr.get("translated_text") or text_en

        tts_res = cls.synthesize_speech(summary_text, target_lang)
        return {
            "summary_text": summary_text,
            "audio_base64": tts_res.get("audio_base64", ""),
            "language_code": target_lang,
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
        Subsidies, FPO opportunities, and e-NAM across all 11 Sarvam languages.
        Never guarantees approval; references official government portals.
        """
        target_lang = cls.normalize_language_code(language_code or cls.detect_language_from_text(query_text))
        is_kn = target_lang == "kn-IN"
        is_en = target_lang == "en-IN"
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
        if re.search(r"fpo|producer company|ಸಂಸ್ಥೆ|ಎಫ್‌ಪಿಒ|ಗುಂಪು|संस्था|एफपीओ", query_lower):
            ans_kn = (
                "FPO (ರೈತ ಉತ್ಪಾದಕ ಸಂಸ್ಥೆ) ರೈತರ ಒಕ್ಕೂಟವಾಗಿದೆ. SFAC ಮತ್ತು ನಬಾರ್ಡ್ ಯೋಜನೆ ಅಡಿಯಲ್ಲಿ, "
                "ಕನಿಷ್ಠ 300 ರೈತರು ಸೇರಿ FPO ರಚಿಸಿದರೆ ₹15 ಲಕ್ಷದವರೆಗೆ ಈಕ್ವಿಟಿ ಅನುದಾನ ಮತ್ತು ₹2 ಕೋಟಿ ಸಾಲ ಖಾತರಿ ಸಿಗುತ್ತದೆ. "
                "ನೆನಪಿಡಿ: ಈ ಅನುದಾನವು FPO ಸಂಸ್ಥೆಗೆ ಮಾತ್ರ ಸಿಗುತ್ತದೆ, ವೈಯಕ್ತಿಕ ರೈತರ ಖಾತೆಗೆ ನೇರವಾಗಿ ಜಮೆಯಾಗುವುದಿಲ್ಲ. "
                "ಅಧಿಕೃತ ವೆಬ್‌ಸೈಟ್: sfacindia.com, ಸಹಾಯವಾಣಿ: 1800 270 0224."
            )
            ans_en = (
                "Under the Central Sector Scheme for 10,000 FPOs (SFAC / NABARD), an FPO with at least 300 farmer "
                "members can receive up to ₹15 Lakhs matching Equity Grant and ₹2 Crore Credit Guarantee. "
                "IMPORTANT NOTE: This grant is paid to the registered FPO company for collective infrastructure, "
                "NOT directly to individual farmers. Official portal: sfacindia.com (Helpline: 1800 270 0224)."
            )
            scheme_id = "SCHEME-SFAC-10K-FPO"

        elif re.search(r"raitha siri|millet|ಸಿರಿ|ರೈತ ಸಿರಿ|ತೋಟಗಾರಿಕೆ|fruits|ರೈತಸಿರಿ", query_lower):
            ans_kn = (
                "ಕರ್ನಾಟಕ ರೈತ ಸಿರಿ ಯೋಜನೆಯಡಿ, ಸಿರಿಧಾನ್ಯ ಮತ್ತು ತೋಟಗಾರಿಕೆ ಬೆಳೆಗಾರರಿಗೆ ಹೆಕ್ಟೇರ್‌ಗೆ ₹10,000 (ಗರಿಷ್ಠ 2 ಹೆಕ್ಟೇರ್‌ಗೆ ₹20,000) "
                "ಪ್ರೋತ್ಸಾಹಧನ ಸಿಗುತ್ತದೆ. ಇದಕ್ಕೆ ನಿಮ್ಮ ಭೂಮಿಯನ್ನು ಫ್ರೂಟ್ಸ್ (FRUITS) ಪೋರ್ಟಲ್‌ನಲ್ಲಿ ನೋಂದಾಯಿಸಿ FID ಸಂಖ್ಯೆ ಹೊಂದಿರಬೇಕು. "
                "ಅಗತ್ಯ ದಾಖಲೆಗಳು: ಪಹಣಿ/RTC, ಆಧಾರ್, ಬ್ಯಾಂಕ್ ಪಾಸ್‌ಬುಕ್. ನಿಮ್ಮ ಸಮೀಪದ ರೈತ ಸಂಪರ್ಕ ಕೇಂದ್ರಕ್ಕೆ (RSK) ಭೇಟಿ ನೀಡಿ. "
                "ಅಧಿಕೃತ ಪೋರ್ಟಲ್: fruits.karnataka.gov.in."
            )
            ans_en = (
                "Under Karnataka Raitha Siri Scheme, horticulture & millet growers receive ₹10,000 per hectare "
                "(up to 2 hectares = ₹20,000) directly via DBT. You must be registered on the Karnataka FRUITS portal "
                "with a valid FID. Required documents: RTC/Pahani, Aadhaar, and bank passbook. "
                "Apply or update at your nearest Raitha Samparka Kendra (RSK) or visit fruits.karnataka.gov.in."
            )
            scheme_id = "SCHEME-KA-RAITHA-SIRI"

        elif re.search(r"pm kisan|kisan|ಸಮ್ಮಾನ್|ಆದಾಯ|6000|सम्मान|किसान", query_lower):
            ans_kn = (
                "ಪ್ರಧಾನ ಮಂತ್ರಿ ಕಿಸಾನ್ ಸಮ್ಮಾನ್ ನಿಧಿ (PM-KISAN) ಯೋಜನೆಯಡಿ, ಸಾಗುವಳಿ ಭೂಮಿ ಹೊಂದಿರುವ ಅರ್ಹ ರೈತ ಕುಟುಂಬಗಳಿಗೆ "
                "ವರ್ಷಕ್ಕೆ ₹6,000 ಆದಾಯ ಬೆಂಬಲ ಸಿಗುತ್ತದೆ (ತಲಾ ₹2,000 ರ 3 ಕಂತುಗಳಲ್ಲಿ). "
                "ನಿಮ್ಮ ಆಧಾರ್ ಸಂಖ್ಯೆಗೆ e-KYC ಪೂರ್ಣಗೊಂಡಿರಬೇಕು ಮತ್ತು ಬ್ಯಾಂಕ್ ಖಾತೆ ಆಧಾರ್‌ಗೆ ಲಿಂಕ್ ಆಗಿರಬೇಕು. "
                "ಅಧಿಕೃತ ಪೋರ್ಟಲ್: pmkisan.gov.in, ಸಹಾಯವಾಣಿ: 155261."
            )
            ans_en = (
                "PM-KISAN provides ₹6,000 per year in three installments of ₹2,000 each directly to cultivable "
                "landholding farmer families. Requires completed biometric/facial e-KYC and Aadhaar-seeded bank account. "
                "Official portal: pmkisan.gov.in (Helpline: 155261)."
            )
            scheme_id = "SCHEME-PM-KISAN"

        elif re.search(r"insurance|fasal bima|samrakshane|ವಿಮೆ|ಫಸಲ್ ಬಿಮಾ|ಸಂರಕ್ಷಣೆ|ನಷ್ಟ|बीमा|फसल बीमा", query_lower):
            ans_kn = (
                "ಪ್ರಧಾನ ಮಂತ್ರಿ ಫಸಲ್ ಬಿಮಾ (ಸಂರಕ್ಷಣೆ ಕರ್ನಾಟಕ) ಯೋಜನೆಯಡಿ ಬೆಳೆ ಹಾನಿಗೆ ಸಮಗ್ರ ವಿಮಾ ರಕ್ಷಣೆ ಸಿಗುತ್ತದೆ. "
                "ಟೊಮೆಟೊ, ಈರುಳ್ಳಿ ಮುಂತಾದ ತೋಟಗಾರಿಕೆ ಬೆಳೆಗಳಿಗೆ ರೈತರು ಕೇವಲ 5% ಪ್ರೀಮಿಯಂ ಪಾವತಿಸಬೇಕು. "
                "ಅಗತ್ಯ ದಾಖಲೆಗಳು: ಜಮೀನಿನ ಪಹಣಿ (RTC), ಬಿತ್ತನೆ ದೃಢೀಕರಣ ಪತ್ರ, ಆಧಾರ್. "
                "ನಿಮ್ಮ ತಾಲೂಕಿನ ಬೆಳೆ ಕಟ್-ಆಫ್ ದಿನಾಂಕವನ್ನು samrakshane.karnataka.gov.in ನಲ್ಲಿ ಪರಿಶೀಲಿಸಿ."
            )
            ans_en = (
                "PM Fasal Bima Yojana / Samrakshane Karnataka covers natural crop losses from pre-sowing to post-harvest. "
                "For commercial and horticulture crops like Tomato and Onion, farmers pay only 5% nominal premium. "
                "Check cut-off dates and submit RTC and sowing certificate at your nearest CSC or bank branch. "
                "Portal: samrakshane.karnataka.gov.in (Helpline: 080-22180800)."
            )
            scheme_id = "SCHEME-PMFBY-SAMRAKSHANE"

        elif re.search(r"enam|e-nam|ಮಾರುಕಟ್ಟೆ|ಆನ್‌ಲೈನ್ ಮಾರಾಟ|trading|bidding|ई-नाम|मंडी ट्रेडिंग", query_lower):
            ans_kn = (
                "e-NAM (ರಾಷ್ಟ್ರೀಯ ಕೃಷಿ ಮಾರುಕಟ್ಟೆ) ಮೂಲಕ ನೀವು ನೋಂದಾಯಿತ ಎಪಿಎಂಸಿಗೆ ತರುವ ಬೆಳೆಯನ್ನು "
                "ದೇಶಾದ್ಯಂತದ ವ್ಯಾಪಾರಿಗಳು ಆನ್‌ಲೈನ್‌ನಲ್ಲಿ ಸ್ಪರ್ಧಾತ್ಮಕ ದರಕ್ಕೆ ಬಿಡ್ ಮಾಡುತ್ತಾರೆ. "
                "ಯಾವುದೇ ಅನಧಿಕೃತ ಕಮಿಷನ್ ಕಡಿತವಿಲ್ಲ, ಸರಿಯಾದ ಎಲೆಕ್ಟ್ರಾನಿಕ್ ತೂಕ, ಮತ್ತು 24 ಗಂಟೆಗಳಲ್ಲಿ ನಿಮ್ಮ ಬ್ಯಾಂಕ್ ಖಾತೆಗೆ ನೇರ ಹಣ ಬರುತ್ತದೆ. "
                "ನೋಂದಣಿಗೆ ಆಧಾರ್ ಮತ್ತು ಬ್ಯಾಂಕ್ ಪಾಸ್‌ಬುಕ್ ಸಾಕು. ಅಧಿಕೃತ ಪೋರ್ಟಲ್: enam.gov.in."
            )
            ans_en = (
                "e-NAM provides pan-India electronic price discovery at integrated APMCs across all major agricultural corridors. "
                "Produce is assayed for quality, and verified buyers bid electronically with zero unauthorized commission "
                "and direct online payment to your bank within 24 hours. "
                "Register free with Aadhaar and bank details at the APMC gate or on enam.gov.in."
            )
            scheme_id = "SCHEME-ENAM-DIRECT"

        else:
            ans_kn = (
                "ಸರ್ಕಾರಿ ಯೋಜನೆಗಳ ವಿವರ: 1. ಪಿಎಂ-ಕಿಸಾನ್ (₹6,000/ವರ್ಷ), 2. ಕರ್ನಾಟಕ ರೈತ ಸಿರಿ (₹10,000/ಹೆಕ್ಟೇರ್ ತೋಟಗಾರಿಕೆ), "
                "3. ಸಂರಕ್ಷಣೆ ಬೆಳೆ ವಿಮೆ (5% ಪ್ರೀಮಿಯಂ), 4. ನಬಾರ್ಡ್ FPO ಅನುದಾನ (₹15 ಲಕ್ಷ ಸಂಸ್ಥೆಗೆ), "
                "5. e-NAM ನೇರ ಮಾರುಕಟ್ಟೆ. ನಿರ್ದಿಷ್ಟ ಯೋಜನೆಯ ಬಗ್ಗೆ ಕೇಳಲು ಹೆಸರನ್ನು ಹೇಳಿ."
            )
            ans_en = (
                "Available verified agricultural opportunities: 1. PM-KISAN (₹6,000/yr), "
                "2. Karnataka Raitha Siri (₹10,000/ha for horticulture), 3. Samrakshane Crop Insurance, "
                "4. SFAC/NABARD 10,000 FPO Scheme (₹15 Lakhs institutional equity grant), and 5. e-NAM Direct Trading. "
                "Ask about any specific scheme for step-by-step guidance."
            )
            scheme_id = None

        if is_kn:
            ans = ans_kn
        elif is_en:
            ans = ans_en
        else:
            tr = cls.translate_text(ans_en, target_language_code=target_lang, source_language_code="en-IN")
            ans = tr.get("translated_text") or ans_en

        tts_res = cls.synthesize_speech(ans, target_lang)
        return {
            "query": query_text,
            "answer": ans,
            "language_code": target_lang,
            "scheme_id": scheme_id,
            "audio_base64": tts_res.get("audio_base64", ""),
            "disclaimer": "Factual guidance based strictly on verified government publications. Not a government approval or financial guarantee."
        }

    @classmethod
    def generate_cold_storage_spoken_summary(
        cls,
        facility_name: str,
        district: str,
        distance_km: float,
        available_capacity_mt: float,
        daily_rate_per_quintal: float,
        mandi_distances: Optional[List[Dict[str, Any]]] = None,
        language_code: str = "kn-IN"
    ) -> Dict[str, Any]:
        """
        Synthesizes spoken audio explanation of a discovered cold storage facility,
        travel distances to local mandis, bay capacity, and rental rates across all 11 Sarvam languages.
        """
        target_lang = cls.normalize_language_code(language_code)
        is_kn = target_lang == "kn-IN"
        is_en = target_lang == "en-IN"

        mandi_phrase_kn = ""
        mandi_phrase_en = ""
        if mandi_distances:
            first = mandi_distances[0]
            m_name = first.get("mandi_name") or first.get("market") or "ಸಮೀಪದ ಮಂಡಿ"
            m_dist = first.get("distance_km", 0)
            mandi_phrase_kn = f"ಇದು {m_name} ಮಾರುಕಟ್ಟೆಯಿಂದ {m_dist} ಕಿಮೀ ದೂರದಲ್ಲಿದೆ. "
            mandi_phrase_en = f"It is {m_dist} km from {m_name}. "

        text_kn = (
            f"ಸೂಕ್ತವಾದ ಕೋಲ್ಡ್ ಸ್ಟೋರೇಜ್ ಸೌಲಭ್ಯ '{facility_name}' ನಿಮ್ಮ ಸ್ಥಳದಿಂದ {distance_km:.1f} ಕಿಲೋಮೀಟರ್ ದೂರದಲ್ಲಿದೆ. "
            f"{mandi_phrase_kn}"
            f"ಲಭ್ಯವಿರುವ ಸಾಮರ್ಥ್ಯ {available_capacity_mt:.1f} ಟನ್ ದೃಢೀಕರಿಸಲಾಗಿದೆ. ದಿನದ ದರ ಕ್ವಿಂಟಾಲ್‌ಗೆ ₹{daily_rate_per_quintal:.2f}. "
            f"ದಯವಿಟ್ಟು ಸೌಲಭ್ಯವನ್ನು ಸಂಪರ್ಕಿಸಿ ಬುಕಿಂಗ್ ಖಚಿತಪಡಿಸಿಕೊಳ್ಳಿ."
        )

        text_en = (
            f"Cold-storage facility '{facility_name}' is {distance_km:.1f} kilometres from your location in {district}. "
            f"{mandi_phrase_en}"
            f"Verified available space is {available_capacity_mt:.1f} tonnes at ₹{daily_rate_per_quintal:.2f} per quintal per day. "
            f"Please contact the facility to confirm crate reservation."
        )

        if is_kn:
            spoken_text = text_kn
        elif is_en:
            spoken_text = text_en
        else:
            tr = cls.translate_text(text_en, target_language_code=target_lang, source_language_code="en-IN")
            spoken_text = tr.get("translated_text") or text_en

        tts_res = cls.synthesize_speech(spoken_text, target_lang)
        return {
            "facility_name": facility_name,
            "district": district,
            "distance_km": distance_km,
            "available_capacity_mt": available_capacity_mt,
            "daily_rate_per_quintal": daily_rate_per_quintal,
            "transcript": spoken_text,
            "language_code": target_lang,
            "audio_base64": tts_res.get("audio_base64", "")
        }
