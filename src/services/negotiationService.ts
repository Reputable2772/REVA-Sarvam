import {
  OfferNegotiation,
  NegotiationStatus,
  NegotiationHistoryEntry
} from '../types/index.js';

export class NegotiationService {
  private offers: Map<string, OfferNegotiation> = new Map();
  private idCounter = 1;

  public createOffer(params: {
    buyerRequirementId: string;
    buyerName: string;
    lotOrPoolId: string;
    isAggregatedPool: boolean;
    crop: string;
    quantityKg: number;
    initialPricePerKg: number;
    createdBy: string;
    notes?: string;
  }): OfferNegotiation {
    const offerId = `OFFER-${String(this.idCounter++).padStart(4, '0')}`;
    const now = new Date().toISOString();

    const initialEntry: NegotiationHistoryEntry = {
      by: params.createdBy,
      role: params.isAggregatedPool ? 'platform' : 'farmer',
      action: 'CREATED_OFFER',
      pricePerKg: params.initialPricePerKg,
      quantityKg: params.quantityKg,
      notes: params.notes || 'Initial offer created from matching engine',
      timestamp: now
    };

    const offer: OfferNegotiation = {
      id: offerId,
      buyerRequirementId: params.buyerRequirementId,
      buyerName: params.buyerName,
      lotOrPoolId: params.lotOrPoolId,
      isAggregatedPool: params.isAggregatedPool,
      crop: params.crop,
      quantityKg: params.quantityKg,
      currentPricePerKg: params.initialPricePerKg,
      status: 'OFFER_SENT',
      history: [initialEntry],
      createdAt: now,
      updatedAt: now
    };

    this.offers.set(offerId, offer);
    return offer;
  }

  public getOffer(id: string): OfferNegotiation | undefined {
    return this.offers.get(id);
  }

  public listOffers(filterStatus?: NegotiationStatus): OfferNegotiation[] {
    const list = Array.from(this.offers.values());
    if (filterStatus) {
      return list.filter((o) => o.status === filterStatus);
    }
    return list;
  }

  public transitionStatus(params: {
    offerId: string;
    newStatus: NegotiationStatus;
    actionBy: string;
    role: 'farmer' | 'buyer' | 'platform';
    counterPricePerKg?: number;
    notes?: string;
  }): OfferNegotiation {
    const offer = this.offers.get(params.offerId);
    if (!offer) {
      throw new Error(`Offer ${params.offerId} not found.`);
    }

    const current = offer.status;
    const target = params.newStatus;

    const allowedTransitions: Record<NegotiationStatus, NegotiationStatus[]> = {
      LISTED: ['MATCHED', 'OFFER_SENT'],
      MATCHED: ['OFFER_SENT', 'REJECTED'],
      OFFER_SENT: ['COUNTER_OFFER', 'ACCEPTED', 'REJECTED'],
      COUNTER_OFFER: ['COUNTER_OFFER', 'ACCEPTED', 'REJECTED'],
      ACCEPTED: ['FULFILLED'],
      REJECTED: [],
      FULFILLED: []
    };

    if (!allowedTransitions[current].includes(target)) {
      throw new Error(
        `Invalid status transition from ${current} to ${target}. Allowed: [${allowedTransitions[current].join(', ')}]`
      );
    }

    const now = new Date().toISOString();
    if (params.counterPricePerKg !== undefined) {
      offer.currentPricePerKg = params.counterPricePerKg;
    }

    offer.status = target;
    offer.updatedAt = now;
    offer.history.push({
      by: params.actionBy,
      role: params.role,
      action: `STATUS_CHANGE_TO_${target}`,
      pricePerKg: offer.currentPricePerKg,
      notes: params.notes,
      timestamp: now
    });

    return offer;
  }
}
