import { Router, Request, Response } from 'express';
import { MatchingEngine } from '../services/matchingEngine.js';
import { NegotiationService } from '../services/negotiationService.js';
import { SEED_BUYER_REQUIREMENTS, SEED_HARVEST_LOTS } from '../data/seedData.js';
import { BuyerRequirement, HarvestLot, AggregatedLot, NegotiationStatus } from '../types/index.js';

export function createMarketplaceRouter(): Router {
  const router = Router();
  const matchingEngine = new MatchingEngine([...SEED_BUYER_REQUIREMENTS]);
  const negotiationService = new NegotiationService();

  // In-memory store for harvest lots and pools
  const harvestLots = new Map<string, HarvestLot>();
  SEED_HARVEST_LOTS.forEach((lot) => harvestLots.set(lot.lot_id, lot));

  // 1. GET /api/marketplace/buyers - List all buyer requirements
  router.get('/buyers', (req: Request, res: Response) => {
    const buyers = matchingEngine.getBuyers();
    res.json({
      success: true,
      count: buyers.length,
      data: buyers
    });
  });

  // 2. POST /api/marketplace/buyers - Create buyer requirement
  router.post('/buyers', (req: Request, res: Response) => {
    const reqBody = req.body as Partial<BuyerRequirement>;
    if (!reqBody.buyerName || !reqBody.crop || !reqBody.targetQuantityKg || !reqBody.maxPricePerKg) {
      return res.status(400).json({
        success: false,
        error: 'Missing required buyer requirement fields (buyerName, crop, targetQuantityKg, maxPricePerKg).'
      });
    }

    const newReq: BuyerRequirement = {
      id: reqBody.id || `BUYER-REQ-${Date.now()}`,
      buyerName: reqBody.buyerName,
      buyerType: reqBody.buyerType || 'Wholesaler',
      crop: reqBody.crop,
      variety: reqBody.variety,
      requiredGrade: reqBody.requiredGrade || 'Grade A',
      targetQuantityKg: Number(reqBody.targetQuantityKg),
      minAcceptableKg: reqBody.minAcceptableKg ? Number(reqBody.minAcceptableKg) : undefined,
      maxPricePerKg: Number(reqBody.maxPricePerKg),
      minPricePerKg: reqBody.minPricePerKg ? Number(reqBody.minPricePerKg) : Number(reqBody.maxPricePerKg) * 0.85,
      destinationLocation: reqBody.destinationLocation || 'Bengaluru DC',
      preferredPickupHub: reqBody.preferredPickupHub || 'Kolar Central Hub',
      paymentTerms: reqBody.paymentTerms || 'T+2',
      active: true,
      createdAt: new Date().toISOString()
    };

    matchingEngine.addBuyerRequirement(newReq);
    res.status(201).json({
      success: true,
      data: newReq
    });
  });

  // 3. GET /api/marketplace/lots - List seed / registered harvest lots
  router.get('/lots', (req: Request, res: Response) => {
    res.json({
      success: true,
      data: Array.from(harvestLots.values())
    });
  });

  // 4. GET /api/marketplace/matches/:lot_id - Match for a single lot
  router.get('/matches/:lot_id', (req: Request, res: Response) => {
    const lotId = String(req.params.lot_id);
    const lot = harvestLots.get(lotId);
    if (!lot) {
      return res.status(404).json({
        success: false,
        error: `Harvest Lot ${lotId} not found.`
      });
    }

    const matches = matchingEngine.matchSingleLot(lot);
    res.json({
      success: true,
      lot,
      matchesCount: matches.length,
      matches
    });
  });

  // 5. POST /api/marketplace/matches/aggregate - Match for a Virtual FPO aggregated pool
  router.post('/matches/aggregate', (req: Request, res: Response) => {
    const body = req.body as Partial<AggregatedLot>;
    if (!body.crop || !body.total_quantity_kg) {
      return res.status(400).json({
        success: false,
        error: 'Missing crop or total_quantity_kg for aggregated pool matching.'
      });
    }

    const pool: AggregatedLot = {
      pool_id: body.pool_id || `VFPO-POOL-${Date.now()}`,
      crop: body.crop,
      variety: body.variety,
      grade: body.grade || 'Grade A',
      total_quantity_kg: Number(body.total_quantity_kg),
      participating_lots: body.participating_lots || [],
      hub_location: body.hub_location || 'Kolar Central Hub'
    };

    const matches = matchingEngine.matchAggregatedPool(pool);
    res.json({
      success: true,
      pool,
      matchesCount: matches.length,
      matches
    });
  });

  // 6. POST /api/marketplace/offers - Create new offer negotiation
  router.post('/offers', (req: Request, res: Response) => {
    const { buyerRequirementId, buyerName, lotOrPoolId, isAggregatedPool, crop, quantityKg, initialPricePerKg, createdBy, notes } = req.body;

    if (!buyerRequirementId || !buyerName || !lotOrPoolId || !quantityKg || !initialPricePerKg) {
      return res.status(400).json({
        success: false,
        error: 'Missing required offer fields.'
      });
    }

    const offer = negotiationService.createOffer({
      buyerRequirementId,
      buyerName,
      lotOrPoolId,
      isAggregatedPool: Boolean(isAggregatedPool),
      crop: crop || 'Tomato',
      quantityKg: Number(quantityKg),
      initialPricePerKg: Number(initialPricePerKg),
      createdBy: createdBy || 'FarmRoute Saathi',
      notes
    });

    res.status(201).json({
      success: true,
      data: offer
    });
  });

  // 7. GET /api/marketplace/offers - List all offers
  router.get('/offers', (req: Request, res: Response) => {
    const status = req.query.status as NegotiationStatus | undefined;
    const offers = negotiationService.listOffers(status);
    res.json({
      success: true,
      count: offers.length,
      data: offers
    });
  });

  // 8. PATCH /api/marketplace/offers/:id/status - Update negotiation status
  router.patch('/offers/:id/status', (req: Request, res: Response) => {
    const offerId = String(req.params.id);
    const { newStatus, actionBy, role, counterPricePerKg, notes } = req.body;

    if (!newStatus || !actionBy || !role) {
      return res.status(400).json({
        success: false,
        error: 'Missing newStatus, actionBy, or role.'
      });
    }

    try {
      const updated = negotiationService.transitionStatus({
        offerId,
        newStatus,
        actionBy,
        role,
        counterPricePerKg: counterPricePerKg ? Number(counterPricePerKg) : undefined,
        notes
      });

      res.json({
        success: true,
        data: updated
      });
    } catch (err: any) {
      res.status(400).json({
        success: false,
        error: err.message
      });
    }
  });

  return router;
}
