from sqlalchemy import Column, String, Integer, Float, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from datetime import datetime, timezone
from backend.app.database import Base

class SchemeSource(Base):
    __tablename__ = "scheme_sources"

    source_id = Column(String(50), primary_key=True)
    name = Column(String(150), nullable=False)
    portal_name = Column(String(100), nullable=False)
    authority_type = Column(String(50), nullable=False) # CENTRAL_GOVT, STATE_GOVT_KARNATAKA, INSTITUTIONAL_NABARD_SFAC, MARKET_INTELLIGENCE
    official_url = Column(String(255), nullable=False)
    helpdesk_contact = Column(String(100), nullable=True)
    last_checked_date = Column(String(30), nullable=False, default="2026-11-13")
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    schemes = relationship("Scheme", back_populates="source")

class Scheme(Base):
    __tablename__ = "schemes"

    scheme_id = Column(String(50), primary_key=True)
    source_id = Column(String(50), ForeignKey("scheme_sources.source_id"), nullable=False)
    title_en = Column(String(200), nullable=False)
    title_kn = Column(String(200), nullable=False)
    category = Column(String(50), nullable=False) # DIRECT_INCOME_SUPPORT, HORTICULTURE_INCENTIVE, CROP_INSURANCE, FPO_INSTITUTIONAL, MARKET_ACCESS
    target_level = Column(String(30), nullable=False, default="INDIVIDUAL_FARMER") # INDIVIDUAL_FARMER vs FPO_COMPANY
    issuing_department = Column(String(150), nullable=False)
    benefit_description_en = Column(Text, nullable=False)
    benefit_description_kn = Column(Text, nullable=False)
    benefit_amount_spec = Column(String(100), nullable=False)
    eligibility_criteria_en = Column(Text, nullable=False)
    eligibility_criteria_kn = Column(Text, nullable=False)
    exclusion_criteria_en = Column(Text, nullable=False)
    exclusion_criteria_kn = Column(Text, nullable=False)
    required_documents_json = Column(Text, nullable=False, default="[]")
    application_steps_json = Column(Text, nullable=False, default="[]")
    application_method = Column(String(50), default="ONLINE_PORTAL_OR_RSK")
    application_window_start = Column(String(30), nullable=True)
    application_window_end = Column(String(30), nullable=True)
    is_window_confirmed = Column(Boolean, default=True)
    official_portal_url = Column(String(255), nullable=False)
    official_contact = Column(String(100), nullable=True)
    verification_status = Column(String(30), default="OFFICIALLY_VERIFIED") # OFFICIALLY_VERIFIED, PARTIALLY_VERIFIED, PENDING_VERIFICATION, EXPIRED
    last_verified_date = Column(String(30), nullable=False, default="2026-11-13")
    freshness_label = Column(String(50), default="Active 2026 Guidelines")
    applicable_crops_json = Column(Text, default="[]") # Empty means all crops
    applicable_states_json = Column(Text, default="[]") # Empty means all India
    max_landholding_hectares = Column(Float, nullable=True)
    requires_fruits_id = Column(Boolean, default=False)
    requires_fpo_membership = Column(Boolean, default=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    source = relationship("SchemeSource", back_populates="schemes")
    matches = relationship("FarmerOpportunityMatch", back_populates="scheme")
    saved_by = relationship("FarmerSavedOpportunity", back_populates="scheme")

class FarmerOpportunityMatch(Base):
    __tablename__ = "farmer_opportunity_matches"

    id = Column(Integer, primary_key=True, autoincrement=True)
    farmer_id = Column(String(50), index=True, nullable=False)
    phone = Column(String(20), index=True, nullable=True)
    scheme_id = Column(String(50), ForeignKey("schemes.scheme_id"), nullable=False)
    match_status = Column(String(30), nullable=False) # LIKELY_ELIGIBLE, POTENTIALLY_RELEVANT, MORE_INFO_REQUIRED, INELIGIBLE
    relevance_reason_en = Column(Text, nullable=False)
    relevance_reason_kn = Column(Text, nullable=False)
    missing_fields_json = Column(Text, default="[]")
    matched_rules_json = Column(Text, default="[]")
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    scheme = relationship("Scheme", back_populates="matches")

class FarmerSavedOpportunity(Base):
    __tablename__ = "farmer_saved_opportunities"

    id = Column(Integer, primary_key=True, autoincrement=True)
    farmer_id = Column(String(50), index=True, nullable=False)
    scheme_id = Column(String(50), ForeignKey("schemes.scheme_id"), nullable=False)
    saved_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    scheme = relationship("Scheme", back_populates="saved_by")

class FarmerBenefitNotification(Base):
    __tablename__ = "farmer_benefit_notifications"

    id = Column(Integer, primary_key=True, autoincrement=True)
    farmer_id = Column(String(50), index=True, nullable=False)
    scheme_id = Column(String(50), nullable=True)
    dedup_key = Column(String(100), unique=True, index=True, nullable=False)
    title_en = Column(String(150), nullable=False)
    title_kn = Column(String(150), nullable=False)
    body_en = Column(Text, nullable=False)
    body_kn = Column(Text, nullable=False)
    why_received_en = Column(Text, nullable=False)
    why_received_kn = Column(Text, nullable=False)
    source_url = Column(String(255), nullable=True)
    is_read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
