import { describe, it } from 'node:test';
import assert from 'node:assert';
import { LogisticsEngine, FLEET_SPECS, VILLAGE_DIRECTORY } from '../services/logisticsEngine.js';
import { SEED_HARVEST_LOTS } from '../data/seedData.js';

describe('FarmRoute AI - Logistics Intelligence & Shared Freight Allocator (Task 5)', () => {
  it('should list all 3 vehicle specs in fleet', () => {
    const fleet = LogisticsEngine.getFleet();
    assert.strictEqual(fleet.length, 3);
    assert.strictEqual(fleet[0].id, 'VEH-ACE');
    assert.strictEqual(fleet[0].maxPayloadKg, 1000);
    assert.strictEqual(fleet[1].id, 'VEH-BOLERO');
    assert.strictEqual(fleet[1].maxPayloadKg, 1500);
    assert.strictEqual(fleet[2].id, 'VEH-407');
    assert.strictEqual(fleet[2].maxPayloadKg, 2500);
  });

  it('should recommend Tata Ace for loads <= 1000 kg', () => {
    const vehicle = LogisticsEngine.recommendVehicle(600);
    assert.strictEqual(vehicle.id, 'VEH-ACE');
    assert.strictEqual(vehicle.maxPayloadKg, 1000);
  });

  it('should recommend Bolero for loads between 1001 kg and 1500 kg', () => {
    const vehicle = LogisticsEngine.recommendVehicle(1350);
    assert.strictEqual(vehicle.id, 'VEH-BOLERO');
    assert.strictEqual(vehicle.maxPayloadKg, 1500);
  });

  it('should recommend Tata 407 for loads between 1501 kg and 2500 kg', () => {
    const vehicle = LogisticsEngine.recommendVehicle(2200);
    assert.strictEqual(vehicle.id, 'VEH-407');
    assert.strictEqual(vehicle.maxPayloadKg, 2500);
  });

  it('should reject loads exceeding max vehicle fleet capacity', () => {
    assert.throws(
      () => LogisticsEngine.recommendVehicle(3000),
      /exceeds maximum single-vehicle capacity/
    );
  });

  it('should compute road-curvature distance between villages', () => {
    const dist = LogisticsEngine.calculateInterVillageDistance('Mulbagal', 'Kolar');
    assert.ok(dist > 20 && dist < 40, `Distance should be realistic: got ${dist} km`);
  });

  it('should allocate shared freight proportionally across all 6 pooled farmers', () => {
    const plan = LogisticsEngine.allocateSharedFreight(
      SEED_HARVEST_LOTS,
      'Bengaluru DC, Whitefield'
    );

    assert.strictEqual(plan.recommendedVehicle.id, 'VEH-ACE');
    assert.strictEqual(plan.totalWeightKg, 600);
    assert.strictEqual(plan.breakdownPerFarmer.length, 6);

    // Sum of allocated freight costs should match total freight cost within rounding margin (<= ₹5)
    const sumAllocated = plan.breakdownPerFarmer.reduce(
      (acc, f) => acc + f.allocatedFreightCost,
      0
    );
    assert.ok(
      Math.abs(sumAllocated - plan.totalFreightCost) <= 6,
      `Allocated sum (${sumAllocated}) should match total (${plan.totalFreightCost})`
    );

    // Verify significant cost savings for each farmer compared to solo hire
    plan.breakdownPerFarmer.forEach((farmer) => {
      assert.ok(
        farmer.freightSavings > 1000,
        `Farmer ${farmer.farmerName} should save > ₹1,000 via pooling`
      );
      assert.ok(
        farmer.savingsPercentage >= 60,
        `Farmer savings percentage should be >= 60%, got ${farmer.savingsPercentage}%`
      );
    });
  });
});
