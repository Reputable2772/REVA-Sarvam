import { describe, it } from 'node:test';
import assert from 'node:assert';
import { MatchingEngine } from '../services/matchingEngine.js';
import { NegotiationService } from '../services/negotiationService.js';
import { SEED_BUYER_REQUIREMENTS, SEED_HARVEST_LOTS } from '../data/seedData.js';
import { AggregatedLot } from '../types/index.js';

describe('FarmRoute AI - Marketplace & Matching Engine', () => {
  const engine = new MatchingEngine([...SEED_BUYER_REQUIREMENTS]);
  const negotiation = new NegotiationService();

  it('should list all pre-seeded buyer requirements', () => {
    const buyers = engine.getBuyers();
    assert.strictEqual(buyers.length, 5);
    const reliance = buyers.find((b) => b.buyerName.includes('Reliance'));
    assert.ok(reliance);
    assert.strictEqual(reliance?.targetQuantityKg, 600);
    assert.strictEqual(reliance?.maxPricePerKg, 30.0);
  });

  it('should match single 100kg tomato lot and recommend virtual FPO pooling', () => {
    const singleLot = SEED_HARVEST_LOTS[0]; // 100kg tomato Grade A
    const matches = engine.matchSingleLot(singleLot);

    assert.ok(matches.length > 0);
    // Reliance requires 600kg, so 100kg lot recommendation should suggest pooling
    const relianceMatch = matches.find((m) => m.buyerName.includes('Reliance'));
    assert.ok(relianceMatch);
    assert.ok(relianceMatch?.recommendation.includes('Virtual FPO'));
    assert.strictEqual(relianceMatch?.fulfillmentType, 'single_lot');
  });

  it('should match 600kg Virtual FPO pooled harvest with 100% full fulfillment score', () => {
    const pooledLot: AggregatedLot = {
      pool_id: 'VFPO-KLR-TOM-01',
      crop: 'Tomato',
      variety: 'Hybrid Red',
      grade: 'Grade A',
      total_quantity_kg: 600,
      participating_lots: [
        'FR-KLR-00251',
        'FR-KLR-00252',
        'FR-KLR-00253',
        'FR-KLR-00254',
        'FR-KLR-00255',
        'FR-KLR-00256'
      ],
      hub_location: 'Kolar Central Hub'
    };

    const matches = engine.matchAggregatedPool(pooledLot);
    assert.ok(matches.length > 0);

    const topMatch = matches[0];
    assert.strictEqual(topMatch.fulfillmentType, 'virtual_fpo_pool');
    assert.strictEqual(topMatch.scoreBreakdown.quantityScore, 40); // Full capacity score
    assert.strictEqual(topMatch.scoreBreakdown.gradeMatch, true);
    assert.strictEqual(topMatch.scoreBreakdown.cropMatch, true);
    assert.ok(topMatch.compatibilityScore >= 90);
  });

  it('should correctly negotiate offers through complete state lifecycle', () => {
    // 1. Create offer
    const offer = negotiation.createOffer({
      buyerRequirementId: 'BUYER-REQ-001',
      buyerName: 'Reliance Retail',
      lotOrPoolId: 'VFPO-KLR-TOM-01',
      isAggregatedPool: true,
      crop: 'Tomato',
      quantityKg: 600,
      initialPricePerKg: 28.0,
      createdBy: 'FarmRoute Engine'
    });

    assert.strictEqual(offer.status, 'OFFER_SENT');
    assert.strictEqual(offer.currentPricePerKg, 28.0);

    // 2. Counter offer by buyer
    const counter = negotiation.transitionStatus({
      offerId: offer.id,
      newStatus: 'COUNTER_OFFER',
      actionBy: 'Reliance Procurement',
      role: 'buyer',
      counterPricePerKg: 27.5,
      notes: 'Countering at 27.5/kg for next day early morning delivery'
    });

    assert.strictEqual(counter.status, 'COUNTER_OFFER');
    assert.strictEqual(counter.currentPricePerKg, 27.5);

    // 3. Acceptance by platform/farmers
    const accepted = negotiation.transitionStatus({
      offerId: offer.id,
      newStatus: 'ACCEPTED',
      actionBy: 'FPO Coordinator (Agent P)',
      role: 'platform',
      notes: 'Accepted counter offer of ₹27.5/kg'
    });

    assert.strictEqual(accepted.status, 'ACCEPTED');

    // 4. Fulfillment
    const fulfilled = negotiation.transitionStatus({
      offerId: offer.id,
      newStatus: 'FULFILLED',
      actionBy: 'Logistics Assurance',
      role: 'platform',
      notes: 'Delivered and verified at Whitefield DC'
    });

    assert.strictEqual(fulfilled.status, 'FULFILLED');
    assert.strictEqual(fulfilled.history.length, 4);
  });

  it('should reject invalid state transitions', () => {
    const offer = negotiation.createOffer({
      buyerRequirementId: 'BUYER-REQ-002',
      buyerName: 'BigBasket',
      lotOrPoolId: 'FR-KLR-00251',
      isAggregatedPool: false,
      crop: 'Tomato',
      quantityKg: 100,
      initialPricePerKg: 27.0,
      createdBy: 'Farmer Manjunath'
    });

    // Cannot transition directly from OFFER_SENT to FULFILLED without ACCEPTED
    assert.throws(() => {
      negotiation.transitionStatus({
        offerId: offer.id,
        newStatus: 'FULFILLED',
        actionBy: 'Farmer',
        role: 'farmer'
      });
    }, /Invalid status transition/);
  });
});
