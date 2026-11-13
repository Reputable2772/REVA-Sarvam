import {
  BuyerRequirement,
  HarvestLot,
  AggregatedLot,
  MatchResult,
  MatchScoreBreakdown,
  QualityGrade
} from '../types/index.js';

export class MatchingEngine {
  private buyers: BuyerRequirement[];

  constructor(initialBuyers: BuyerRequirement[]) {
    this.buyers = initialBuyers;
  }

  public getBuyers(): BuyerRequirement[] {
    return this.buyers;
  }

  public addBuyerRequirement(requirement: BuyerRequirement): void {
    this.buyers.push(requirement);
  }

  public evaluateGradeCompatibility(
    required: QualityGrade,
    offered: QualityGrade
  ): { compatible: boolean; score: number } {
    if (required === 'Any') {
      return { compatible: true, score: 25 };
    }
    if (required === offered) {
      return { compatible: true, score: 30 };
    }
    // High-grade produce can fulfill lower requirements (e.g., Grade A used for Grade B)
    if (required === 'Grade B' && offered === 'Grade A') {
      return { compatible: true, score: 28 };
    }
    if (required === 'Grade C' && (offered === 'Grade A' || offered === 'Grade B')) {
      return { compatible: true, score: 22 };
    }
    return { compatible: false, score: 0 };
  }

  public matchSingleLot(lot: HarvestLot): MatchResult[] {
    const results: MatchResult[] = [];

    for (const buyer of this.buyers) {
      if (!buyer.active) continue;

      // Crop check
      const cropMatch = buyer.crop.toLowerCase() === lot.crop.toLowerCase();
      if (!cropMatch) continue;

      // Grade check
      const gradeCheck = this.evaluateGradeCompatibility(buyer.requiredGrade, lot.quality_grade);
      if (!gradeCheck.compatible) continue;

      // Quantity scoring (Single lot)
      // If single lot is 100kg and buyer wants 600kg, single fulfillment is partial
      const targetKg = buyer.targetQuantityKg;
      const minKg = buyer.minAcceptableKg || targetKg * 0.8;
      let quantityScore = 0;

      if (lot.quantity_kg >= targetKg) {
        quantityScore = 40;
      } else if (lot.quantity_kg >= minKg) {
        quantityScore = 30;
      } else {
        // Under minimum requirement -> low single lot score
        quantityScore = Math.round((lot.quantity_kg / targetKg) * 20);
      }

      // Price score (0-30 based on maxPricePerKg)
      const priceScore = Math.min(30, Math.round((buyer.maxPricePerKg / 30) * 30));

      // Logistics score (0-30 based on hub proximity)
      const logisticsScore = buyer.preferredPickupHub.toLowerCase().includes('kolar') ? 30 : 20;

      const totalScore = gradeCheck.score + quantityScore + priceScore + logisticsScore;

      const breakdown: MatchScoreBreakdown = {
        cropMatch: true,
        gradeMatch: gradeCheck.compatible,
        quantityScore,
        priceScore,
        logisticsScore,
        totalScore
      };

      const recommendation =
        lot.quantity_kg < minKg
          ? `Individual lot (${lot.quantity_kg}kg) is below buyer target (${targetKg}kg). Highly recommended to pool into Virtual FPO.`
          : `Lot meets direct delivery thresholds for ${buyer.buyerName}.`;

      results.push({
        buyerRequirementId: buyer.id,
        buyerName: buyer.buyerName,
        buyerType: buyer.buyerType,
        crop: buyer.crop,
        requiredGrade: buyer.requiredGrade,
        targetQuantityKg: buyer.targetQuantityKg,
        offeredPricePerKg: buyer.maxPricePerKg,
        fulfillmentType: 'single_lot',
        lotOrPoolId: lot.lot_id,
        matchedQuantityKg: lot.quantity_kg,
        compatibilityScore: totalScore,
        scoreBreakdown: breakdown,
        recommendation
      });
    }

    return results.sort((a, b) => b.compatibilityScore - a.compatibilityScore);
  }

  public matchAggregatedPool(pool: AggregatedLot): MatchResult[] {
    const results: MatchResult[] = [];

    for (const buyer of this.buyers) {
      if (!buyer.active) continue;

      const cropMatch = buyer.crop.toLowerCase() === pool.crop.toLowerCase();
      if (!cropMatch) continue;

      const gradeCheck = this.evaluateGradeCompatibility(buyer.requiredGrade, pool.grade);
      if (!gradeCheck.compatible) continue;

      const targetKg = buyer.targetQuantityKg;
      const minKg = buyer.minAcceptableKg || targetKg * 0.8;
      let quantityScore = 0;

      if (pool.total_quantity_kg >= targetKg) {
        quantityScore = 40; // Perfect full truckload fulfillment
      } else if (pool.total_quantity_kg >= minKg) {
        quantityScore = 35;
      } else {
        quantityScore = Math.round((pool.total_quantity_kg / targetKg) * 35);
      }

      const priceScore = Math.min(30, Math.round((buyer.maxPricePerKg / 30) * 30));
      const logisticsScore = 30; // Aggregated hub collection

      const totalScore = gradeCheck.score + quantityScore + priceScore + logisticsScore;

      const breakdown: MatchScoreBreakdown = {
        cropMatch: true,
        gradeMatch: true,
        quantityScore,
        priceScore,
        logisticsScore,
        totalScore
      };

      results.push({
        buyerRequirementId: buyer.id,
        buyerName: buyer.buyerName,
        buyerType: buyer.buyerType,
        crop: buyer.crop,
        requiredGrade: buyer.requiredGrade,
        targetQuantityKg: buyer.targetQuantityKg,
        offeredPricePerKg: buyer.maxPricePerKg,
        fulfillmentType: 'virtual_fpo_pool',
        lotOrPoolId: pool.pool_id,
        matchedQuantityKg: Math.min(pool.total_quantity_kg, targetKg),
        compatibilityScore: totalScore,
        scoreBreakdown: breakdown,
        recommendation: `Virtual FPO aggregated lot (${pool.total_quantity_kg}kg) achieves high-volume direct contract at ₹${buyer.maxPricePerKg}/kg.`
      });
    }

    return results.sort((a, b) => b.compatibilityScore - a.compatibilityScore);
  }
}
