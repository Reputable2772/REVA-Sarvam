export type BuyerType = 'Supermarket' | 'Hotel' | 'Processor' | 'Wholesaler';

export type QualityGrade = 'Grade A' | 'Grade B' | 'Grade C' | 'Any';

export interface BuyerRequirement {
  id: string;
  buyerName: string;
  buyerType: BuyerType;
  crop: string;
  variety?: string;
  requiredGrade: QualityGrade;
  targetQuantityKg: number;
  minAcceptableKg?: number;
  maxPricePerKg: number;
  minPricePerKg: number;
  destinationLocation: string;
  preferredPickupHub: string;
  paymentTerms: string;
  active: boolean;
  createdAt: string;
}

export interface HarvestLot {
  lot_id: string;
  farmer_id: string;
  farmer_name: string;
  village: string;
  district: string;
  phone?: string;
  crop: string;
  variety: string;
  quantity_kg: number;
  quality_grade: QualityGrade;
  harvest_date: string;
  status: 'ready_for_sale' | 'pooled' | 'contracted' | 'delivered';
}

export interface AggregatedLot {
  pool_id: string;
  crop: string;
  variety?: string;
  grade: QualityGrade;
  total_quantity_kg: number;
  participating_lots: string[];
  hub_location: string;
}

export interface MatchScoreBreakdown {
  cropMatch: boolean;
  gradeMatch: boolean;
  quantityScore: number; // 0 - 40
  priceScore: number;    // 0 - 30
  logisticsScore: number;// 0 - 30
  totalScore: number;    // 0 - 100
}

export interface MatchResult {
  buyerRequirementId: string;
  buyerName: string;
  buyerType: BuyerType;
  crop: string;
  requiredGrade: QualityGrade;
  targetQuantityKg: number;
  offeredPricePerKg: number;
  fulfillmentType: 'single_lot' | 'virtual_fpo_pool';
  lotOrPoolId: string;
  matchedQuantityKg: number;
  compatibilityScore: number; // 0 - 100
  scoreBreakdown: MatchScoreBreakdown;
  recommendation: string;
}

export type NegotiationStatus =
  | 'LISTED'
  | 'MATCHED'
  | 'OFFER_SENT'
  | 'COUNTER_OFFER'
  | 'ACCEPTED'
  | 'REJECTED'
  | 'FULFILLED';

export interface NegotiationHistoryEntry {
  by: string;
  role: 'farmer' | 'buyer' | 'platform';
  action: string;
  pricePerKg?: number;
  quantityKg?: number;
  notes?: string;
  timestamp: string;
}

export interface OfferNegotiation {
  id: string;
  buyerRequirementId: string;
  buyerName: string;
  lotOrPoolId: string;
  isAggregatedPool: boolean;
  crop: string;
  quantityKg: number;
  currentPricePerKg: number;
  status: NegotiationStatus;
  history: NegotiationHistoryEntry[];
  createdAt: string;
  updatedAt: string;
}
