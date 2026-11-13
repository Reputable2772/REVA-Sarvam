import { describe, it } from 'node:test';
import assert from 'node:assert';
import { LogisticsEngine } from '../services/logisticsEngine.js';
import { NetbackEngine } from '../services/netbackEngine.js';
import { VirtualFpoEngine } from '../services/virtualFpoEngine.js';
import { NegotiationService } from '../services/negotiationService.js';
import { SEED_HARVEST_LOTS } from '../data/seedData.js';
import { HarvestLot } from '../types/index.js';

describe('FarmRoute AI — Hackathon E2E Integration & Stress Test Suite', () => {
  // 1. RANDOM / FUZZ TESTING: Logistics Fleet Matching
  it('should correctly select optimal vehicle across 50 random payload weights', () => {
    for (let i = 0; i < 50; i++) {
      const weight = Math.floor(Math.random() * 2490) + 10; // 10 kg to 2500 kg
      const vehicle = LogisticsEngine.recommendVehicle(weight);

      assert.ok(
        vehicle.maxPayloadKg >= weight,
        `Vehicle ${vehicle.name} payload ${vehicle.maxPayloadKg} must handle weight ${weight}`
      );

      if (weight <= 1000) {
        assert.strictEqual(vehicle.id, 'VEH-ACE', `Weight ${weight} should get Tata Ace`);
      } else if (weight <= 1500) {
        assert.strictEqual(vehicle.id, 'VEH-BOLERO', `Weight ${weight} should get Bolero`);
      } else {
        assert.strictEqual(vehicle.id, 'VEH-407', `Weight ${weight} should get Tata 407`);
      }
    }
  });

  // 2. MATHEMATICAL CONSERVATION: Proportional Freight Split
  it('should guarantee exact freight cost conservation (sum of parts == total) under randomized farmer lot contributions', () => {
    for (let run = 0; run < 10; run++) {
      const randomLots: HarvestLot[] = SEED_HARVEST_LOTS.map((base, idx) => ({
        ...base,
        lot_id: `LOT-RND-${run}-${idx}`,
        quantity_kg: Math.floor(Math.random() * 120) + 40 // 40 kg to 160 kg per farmer
      }));

      const plan = LogisticsEngine.allocateSharedFreight(randomLots, 'Bengaluru DC, Whitefield');
      const totalCost = plan.totalFreightCost;
      const sumFarmerShares = plan.breakdownPerFarmer.reduce(
        (sum, f) => sum + f.allocatedFreightCost,
        0
      );

      // Total distributed must equal total freight within small integer rounding margin (<= ₹6)
      assert.ok(
        Math.abs(totalCost - sumFarmerShares) <= 6,
        `Freight conservation failed in run ${run}: total ${totalCost} vs sum ${sumFarmerShares}`
      );

      // Verify every farmer achieves positive savings compared to hiring a private vehicle
      plan.breakdownPerFarmer.forEach((f) => {
        assert.ok(f.freightSavings > 0, `Farmer ${f.farmerName} must have positive savings`);
        assert.ok(f.savingsPercentage > 50, `Farmer ${f.farmerName} should save > 50% on freight`);
      });
    }
  });

  // 3. ECONOMIC DECISION VALIDATION: Netback Comparison under Market Fluctuations
  it('should reliably calculate netback advantage across varying market rates', () => {
    const netbackEngine = new NetbackEngine();
    const testRates = [
      { mandi: 18.0, buyer: 25.0 },
      { mandi: 22.0, buyer: 28.5 },
      { mandi: 26.0, buyer: 32.0 }
    ];

    testRates.forEach(({ mandi, buyer }) => {
      const res = netbackEngine.calculateNetback({
        lot_id: 'FR-KLR-00251',
        crop: 'Tomato',
        quantity_kg: 100,
        location: 'Mulbagal',
        mandiRatePerKg: mandi,
        directBuyerRatePerKg: buyer
      });

      const mandiOpt = res.options[0];
      const buyerOpt = res.options[1];

      // Direct Buyer Net should decisively beat Mandi Net
      assert.ok(
        buyerOpt.net_return > mandiOpt.net_return,
        `Buyer net (₹${buyerOpt.net_return}) must beat mandi net (₹${mandiOpt.net_return})`
      );
      assert.strictEqual(res.recommendation, 'Direct Buyer via Virtual FPO');
      assert.ok(res.farmer_savings > 500, 'Farmer savings should exceed ₹500/100kg');
    });
  });

  // 4. NEGOTIATION STATE MACHINE: Illegal state transition prevention
  it('should enforce strict state transition rules and prevent arbitrary jumps', () => {
    const service = new NegotiationService();
    const offer = service.createOffer({
      buyerRequirementId: 'BUYER-REQ-001',
      buyerName: 'Reliance Retail',
      lotOrPoolId: 'VFPO-KLR-TOMATO-01',
      isAggregatedPool: true,
      crop: 'Tomato',
      quantityKg: 600,
      initialPricePerKg: 28.5,
      createdBy: 'Reliance Procurement',
      notes: 'Initial bulk offer'
    });

    assert.strictEqual(offer.status, 'OFFER_SENT');

    // Valid: OFFER_SENT -> COUNTER_OFFER
    const counter = service.transitionStatus({
      offerId: offer.id,
      newStatus: 'COUNTER_OFFER',
      actionBy: 'Farmer Rep',
      role: 'farmer',
      counterPricePerKg: 29.0,
      notes: 'Requested +₹0.50 for premium Grade A'
    });
    assert.strictEqual(counter.status, 'COUNTER_OFFER');

    // Invalid: Cannot jump from COUNTER_OFFER directly to FULFILLED without ACCEPTED
    assert.throws(() => {
      service.transitionStatus({
        offerId: offer.id,
        newStatus: 'FULFILLED',
        actionBy: 'Hacker',
        role: 'buyer'
      });
    }, /Invalid status transition from COUNTER_OFFER to FULFILLED/);

    // Valid: COUNTER_OFFER -> ACCEPTED
    const accepted = service.transitionStatus({
      offerId: offer.id,
      newStatus: 'ACCEPTED',
      actionBy: 'Reliance Retail',
      role: 'buyer',
      notes: 'Accepted farmer counter price'
    });
    assert.strictEqual(accepted.status, 'ACCEPTED');

    // Valid: ACCEPTED -> FULFILLED
    const fulfilled = service.transitionStatus({
      offerId: offer.id,
      newStatus: 'FULFILLED',
      actionBy: 'Platform Logistics',
      role: 'platform',
      notes: 'Delivery completed & payment settled'
    });
    assert.strictEqual(fulfilled.status, 'FULFILLED');
  });

  // 5. VIRTUAL FPO MATCHING & AGGREGATION
  it('should accurately aggregate 6 farmer lots into full 600kg buyer PO', () => {
    const fpoEngine = new VirtualFpoEngine(SEED_HARVEST_LOTS);
    const pool = fpoEngine.poolLots({
      crop: 'Tomato',
      grade: 'Grade A',
      target_quantity_kg: 600,
      price_per_kg: 28.5,
      total_transport_cost: 900,
      participating_lots: SEED_HARVEST_LOTS.map((l) => l.lot_id)
    });

    assert.strictEqual(pool.total_pooled_kg, 600);
    assert.strictEqual(pool.status, 'fulfilled');
    assert.strictEqual(pool.farmer_shares.length, 6);
    assert.strictEqual(pool.total_order_value, 600 * 28.5);

    // Verify individual payout sum equals total order value minus transport
    const totalPayouts = pool.farmer_shares.reduce((acc, f) => acc + f.net_payout, 0);
    const expectedPayouts = pool.total_order_value - pool.shared_transport_total;
    assert.strictEqual(totalPayouts, expectedPayouts);
  });
});
