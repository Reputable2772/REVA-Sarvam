from sqlalchemy import Column, String, Integer, Float, Boolean, DateTime, ForeignKey, Text
from sqlalchemy.orm import relationship
from datetime import datetime, timezone
from backend.app.database import Base

class Farmer(Base):
    __tablename__ = "farmers"

    farmer_id = Column(String(50), primary_key=True)
    name = Column(String(100), nullable=False)
    village = Column(String(100), nullable=False)
    district = Column(String(100), nullable=False, default="Kolar")
    state = Column(String(100), nullable=False, default="Karnataka")
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    phone = Column(String(20), nullable=False)
    metadata_json = Column(Text, nullable=True, default="{}") # Extensible attributes
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    lots = relationship("HarvestLot", back_populates="farmer")

class HarvestLot(Base):
    __tablename__ = "harvest_lots"

    lot_id = Column(String(50), primary_key=True) # e.g. FR-KLR-20261113-0001
    farmer_id = Column(String(50), ForeignKey("farmers.farmer_id"), nullable=False)
    crop = Column(String(50), nullable=False, default="Tomato")
    variety = Column(String(50), nullable=False, default="Hybrid Red")
    quantity_kg = Column(Float, nullable=False)
    quality_grade = Column(String(20), nullable=False, default="Grade A") # Farmer-declared
    harvest_date = Column(String(20), nullable=False)
    status = Column(String(30), nullable=False, default="ready_for_sale") # ready_for_sale, pooled, weighed, delivered
    metadata_json = Column(Text, nullable=True, default="{}") # Extensible quality/harvest parameters
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    farmer = relationship("Farmer", back_populates="lots")
    pool_shares = relationship("FarmerPoolShare", back_populates="lot")
    weighment = relationship("OperatorWeighment", back_populates="lot", uselist=False)

class BuyerRequirement(Base):
    __tablename__ = "buyer_requirements"

    id = Column(String(50), primary_key=True)
    buyer_name = Column(String(150), nullable=False)
    buyer_type = Column(String(50), nullable=False, default="Supermarket")
    crop = Column(String(50), nullable=False, default="Tomato")
    variety = Column(String(50), nullable=False, default="Hybrid Red")
    required_grade = Column(String(20), nullable=False, default="Grade A")
    target_quantity_kg = Column(Float, nullable=False)
    min_acceptable_kg = Column(Float, nullable=True)
    max_price_per_kg = Column(Float, nullable=False)
    min_price_per_kg = Column(Float, nullable=False)
    destination_location = Column(String(200), nullable=False, default="Bengaluru DC, Whitefield")
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    preferred_pickup_hub = Column(String(100), nullable=False, default="Kolar Central Hub")
    payment_terms = Column(String(100), nullable=False, default="T+1 direct bank transfer")
    metadata_json = Column(Text, nullable=True, default="{}") # Extensible buyer contract specs
    active = Column(Boolean, default=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

class MandiPriceRecord(Base):
    """
    Standard schema for AGMARKNET, e-NAM, and Data.gov.in daily market price datasets.
    Allows dynamic ingestion of any commodity across any district or mandi.
    """
    __tablename__ = "mandi_price_records"

    id = Column(Integer, primary_key=True, autoincrement=True)
    state = Column(String(100), nullable=False, default="Karnataka")
    district = Column(String(100), nullable=False)
    market_name = Column(String(100), nullable=False) # e.g. Kolar, Bangarpet, Malur, Chintamani
    commodity = Column(String(100), nullable=False)   # e.g. Tomato, Onion, Potato
    variety = Column(String(100), nullable=False, default="Local/Hybrid")
    grade = Column(String(50), nullable=False, default="FAQ")
    arrival_date = Column(String(20), nullable=False) # YYYY-MM-DD
    arrivals_tonnes = Column(Float, nullable=True, default=0.0)
    min_price_per_quintal = Column(Float, nullable=False)
    max_price_per_quintal = Column(Float, nullable=False)
    modal_price_per_quintal = Column(Float, nullable=False)
    modal_price_per_kg = Column(Float, nullable=False) # modal_price / 100
    source = Column(String(50), default="AGMARKNET")
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

class VirtualFpoPool(Base):
    __tablename__ = "virtual_fpo_pools"

    pool_id = Column(String(50), primary_key=True)
    crop = Column(String(50), nullable=False, default="Tomato")
    grade = Column(String(20), nullable=False, default="Grade A")
    buyer_requirement_id = Column(String(50), ForeignKey("buyer_requirements.id"), nullable=True)
    target_quantity_kg = Column(Float, nullable=False, default=800.0)
    total_pooled_kg = Column(Float, nullable=False, default=0.0)
    contract_price_per_kg = Column(Float, nullable=False, default=28.50)
    total_freight_cost = Column(Float, nullable=False, default=3292.0)
    status = Column(String(30), nullable=False, default="pending") # pending, fulfilled, dispatched, settled
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    shares = relationship("FarmerPoolShare", back_populates="pool")
    escrow_orders = relationship("EscrowOrder", back_populates="pool")

class FarmerPoolShare(Base):
    __tablename__ = "farmer_pool_shares"

    id = Column(Integer, primary_key=True, autoincrement=True)
    pool_id = Column(String(50), ForeignKey("virtual_fpo_pools.pool_id"), nullable=False)
    farmer_id = Column(String(50), ForeignKey("farmers.farmer_id"), nullable=False)
    lot_id = Column(String(50), ForeignKey("harvest_lots.lot_id"), nullable=False)
    allocated_kg = Column(Float, nullable=False)
    share_pct = Column(Float, nullable=False)
    gross_amount = Column(Float, nullable=False)
    allocated_freight = Column(Float, nullable=False)
    net_payout = Column(Float, nullable=False)
    farmer_accepted = Column(Boolean, default=False)
    buyer_accepted = Column(Boolean, default=False)

    pool = relationship("VirtualFpoPool", back_populates="shares")
    lot = relationship("HarvestLot", back_populates="pool_shares")

class OperatorWeighment(Base):
    __tablename__ = "operator_weighments"

    id = Column(Integer, primary_key=True, autoincrement=True)
    lot_id = Column(String(50), ForeignKey("harvest_lots.lot_id"), nullable=False, unique=True)
    operator_id = Column(String(50), nullable=False, default="OP-KLR-01")
    declared_kg = Column(Float, nullable=False)
    actual_weighed_kg = Column(Float, nullable=False)
    grade_a_kg = Column(Float, nullable=False) # e.g. 88%
    grade_b_kg = Column(Float, nullable=False) # e.g. 10%
    grade_c_kg = Column(Float, nullable=False) # e.g. 2%
    notes = Column(Text, nullable=True)
    verified_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

    lot = relationship("HarvestLot", back_populates="weighment")

class EscrowOrder(Base):
    __tablename__ = "escrow_orders"

    id = Column(String(50), primary_key=True)
    pool_id = Column(String(50), ForeignKey("virtual_fpo_pools.pool_id"), nullable=False)
    buyer_id = Column(String(50), nullable=False)
    total_amount = Column(Float, nullable=False)
    grade_a_amount = Column(Float, nullable=False)
    grade_b_amount = Column(Float, nullable=False, default=0.0)
    grade_c_amount = Column(Float, nullable=False, default=0.0)
    status = Column(String(30), nullable=False, default="PENDING") # PENDING, ESCROW_LOCKED, DELIVERED, SETTLED
    is_simulated = Column(Boolean, default=True)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    settled_at = Column(DateTime, nullable=True)

    pool = relationship("VirtualFpoPool", back_populates="escrow_orders")

class AuditLog(Base):
    __tablename__ = "audit_logs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    action = Column(String(100), nullable=False)
    actor_role = Column(String(30), nullable=False)
    actor_id = Column(String(50), nullable=False)
    details = Column(Text, nullable=False)
    timestamp = Column(DateTime, default=lambda: datetime.now(timezone.utc))

class EmailOtpRecord(Base):
    """
    Email OTP Authentication Model
    """
    __tablename__ = "email_otp_records"

    id = Column(Integer, primary_key=True, autoincrement=True)
    email = Column(String(150), nullable=False, index=True)
    otp_code = Column(String(10), nullable=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))
    expires_at = Column(DateTime, nullable=False)
    verified = Column(Boolean, default=False)
    attempts = Column(Integer, default=0)

class ColdStorageFacility(Base):
    """
    Phase 14 — Cold Storage Facility Model
    """
    __tablename__ = "cold_storage_facilities"

    facility_id = Column(String(50), primary_key=True)
    name = Column(String(150), nullable=False)
    district = Column(String(100), nullable=False, default="Kolar")
    location = Column(String(200), nullable=False)
    latitude = Column(Float, nullable=True)
    longitude = Column(Float, nullable=True)
    total_capacity_tonnes = Column(Float, nullable=False, default=100.0)
    available_capacity_tonnes = Column(Float, nullable=False, default=45.0)
    daily_rate_per_quintal = Column(Float, nullable=False, default=18.0)
    temperature_celsius = Column(Float, nullable=False, default=12.0)
    contact_phone = Column(String(30), nullable=False, default="+91 98450 11223")
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

class ColdStorageBooking(Base):
    """
    Phase 14 — Farmer Cold Storage Booking Enquiry
    """
    __tablename__ = "cold_storage_bookings"

    booking_id = Column(String(50), primary_key=True)
    facility_id = Column(String(50), ForeignKey("cold_storage_facilities.facility_id"), nullable=False)
    farmer_id = Column(String(50), nullable=False)
    crop = Column(String(50), nullable=False, default="Tomato")
    quantity_kg = Column(Float, nullable=False)
    duration_days = Column(Integer, nullable=False, default=7)
    status = Column(String(30), nullable=False, default="REQUESTED") # REQUESTED, CONFIRMED, CHECKED_IN, RELEASED
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

class DevicePushToken(Base):
    """
    Phase 17 — Push Notification Device Registration
    """
    __tablename__ = "device_push_tokens"

    id = Column(Integer, primary_key=True, autoincrement=True)
    device_id = Column(String(100), nullable=False, unique=True)
    user_id = Column(String(50), nullable=False)
    fcm_token = Column(String(255), nullable=False)
    platform = Column(String(20), default="android")
    updated_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

class NotificationRecord(Base):
    """
    Phase 17 — In-app / Push Alert Storage
    """
    __tablename__ = "notification_records"

    id = Column(Integer, primary_key=True, autoincrement=True)
    recipient_id = Column(String(50), nullable=False)
    title = Column(String(200), nullable=False)
    body = Column(Text, nullable=False)
    type = Column(String(50), default="general") # offer_match, weather_alert, simulated_payment, logistics
    read = Column(Boolean, default=False)
    created_at = Column(DateTime, default=lambda: datetime.now(timezone.utc))

