import { HarvestLot } from '../types/index.js';

export interface VehicleSpec {
  id: string;
  name: string;
  model: string;
  maxPayloadKg: number;
  baseCharge: number;
  perKmRate: number;
  loadingUnloadingCost: number;
  description: string;
}

export interface VillageLocation {
  name: string;
  taluk: string;
  district: string;
  coordinates: { lat: number; lon: number };
  distanceToKolarHubKm: number;
  distanceToMulbagalKm: number;
  distanceToBangarpetKm: number;
  distanceToBangaloreDcKm: number;
}

export interface FarmerFreightShare {
  farmerId: string;
  farmerName: string;
  lotId: string;
  village: string;
  quantityKg: number;
  sharePercentage: number;
  allocatedFreightCost: number;
  soloTransportCost: number;
  freightSavings: number;
  savingsPercentage: number;
}

export interface LogisticsPlanResult {
  planId: string;
  recommendedVehicle: VehicleSpec;
  totalWeightKg: number;
  capacityUtilizationPercent: number;
  pickupSequence: string[];
  destination: string;
  totalDistanceKm: number;
  totalFreightCost: number;
  breakdownPerFarmer: FarmerFreightShare[];
  summary: {
    totalPooledFarmers: number;
    totalSavingsAcrossFarmers: number;
    averageSavingsPercent: number;
    co2SavedKgEst: number;
  };
  generatedAt: string;
}

export const FLEET_SPECS: VehicleSpec[] = [
  {
    id: 'VEH-ACE',
    name: 'Tata Ace (Chhota Hathi)',
    model: 'Tata Ace Gold Petrol/CNG',
    maxPayloadKg: 1000,
    baseCharge: 500,
    perKmRate: 18,
    loadingUnloadingCost: 200,
    description: 'Optimal for local village pickups & short-to-medium runs up to 1 Ton'
  },
  {
    id: 'VEH-BOLERO',
    name: 'Mahindra Bolero Maxi Truck',
    model: 'Bolero Maxi Truck Plus',
    maxPayloadKg: 1500,
    baseCharge: 750,
    perKmRate: 22,
    loadingUnloadingCost: 350,
    description: 'Heavy rural pickup capacity for clusters up to 1.5 Tons'
  },
  {
    id: 'VEH-407',
    name: 'Tata 407 LPT',
    model: 'Tata 407 SFC 29 WB',
    maxPayloadKg: 2500,
    baseCharge: 1200,
    perKmRate: 28,
    loadingUnloadingCost: 500,
    description: 'Bulk institutional transport for larger Virtual FPO consolidations up to 2.5 Tons'
  }
];

export const VILLAGE_DIRECTORY: Record<string, VillageLocation> = {
  Mulbagal: {
    name: 'Mulbagal',
    taluk: 'Mulbagal',
    district: 'Kolar',
    coordinates: { lat: 13.1643, lon: 78.3962 },
    distanceToKolarHubKm: 28,
    distanceToMulbagalKm: 0,
    distanceToBangarpetKm: 32,
    distanceToBangaloreDcKm: 95
  },
  Devarayasamudra: {
    name: 'Devarayasamudra',
    taluk: 'Mulbagal',
    district: 'Kolar',
    coordinates: { lat: 13.195, lon: 78.315 },
    distanceToKolarHubKm: 22,
    distanceToMulbagalKm: 12,
    distanceToBangarpetKm: 26,
    distanceToBangaloreDcKm: 90
  },
  Avani: {
    name: 'Avani',
    taluk: 'Mulbagal',
    district: 'Kolar',
    coordinates: { lat: 13.1, lon: 78.33 },
    distanceToKolarHubKm: 24,
    distanceToMulbagalKm: 14,
    distanceToBangarpetKm: 18,
    distanceToBangaloreDcKm: 92
  },
  Tayalur: {
    name: 'Tayalur',
    taluk: 'Mulbagal',
    district: 'Kolar',
    coordinates: { lat: 13.14, lon: 78.44 },
    distanceToKolarHubKm: 34,
    distanceToMulbagalKm: 8,
    distanceToBangarpetKm: 36,
    distanceToBangaloreDcKm: 102
  },
  Byrakur: {
    name: 'Byrakur',
    taluk: 'Mulbagal',
    district: 'Kolar',
    coordinates: { lat: 13.21, lon: 78.48 },
    distanceToKolarHubKm: 40,
    distanceToMulbagalKm: 14,
    distanceToBangarpetKm: 42,
    distanceToBangaloreDcKm: 108
  },
  Nangali: {
    name: 'Nangali',
    taluk: 'Mulbagal',
    district: 'Kolar',
    coordinates: { lat: 13.18, lon: 78.55 },
    distanceToKolarHubKm: 45,
    distanceToMulbagalKm: 18,
    distanceToBangarpetKm: 48,
    distanceToBangaloreDcKm: 115
  },
  Kolar: {
    name: 'Kolar APMC',
    taluk: 'Kolar',
    district: 'Kolar',
    coordinates: { lat: 13.1367, lon: 78.1292 },
    distanceToKolarHubKm: 0,
    distanceToMulbagalKm: 28,
    distanceToBangarpetKm: 18,
    distanceToBangaloreDcKm: 68
  },
  Bangarpet: {
    name: 'Bangarpet APMC',
    taluk: 'Bangarpet',
    district: 'Kolar',
    coordinates: { lat: 12.9967, lon: 78.1887 },
    distanceToKolarHubKm: 18,
    distanceToMulbagalKm: 32,
    distanceToBangarpetKm: 0,
    distanceToBangaloreDcKm: 75
  }
};

export class LogisticsEngine {
  public static getFleet(): VehicleSpec[] {
    return FLEET_SPECS;
  }

  public static recommendVehicle(totalWeightKg: number): VehicleSpec {
    if (totalWeightKg <= 0) {
      throw new Error('Weight must be greater than zero');
    }

    const eligible = FLEET_SPECS.filter((v) => v.maxPayloadKg >= totalWeightKg);
    if (eligible.length === 0) {
      throw new Error(
        `Total load (${totalWeightKg} kg) exceeds maximum single-vehicle capacity of ${FLEET_SPECS[FLEET_SPECS.length - 1].maxPayloadKg} kg. Multi-truck split required.`
      );
    }

    // Pick lowest capacity that safely handles the load
    return eligible[0];
  }

  public static calculateInterVillageDistance(origin: string, dest: string): number {
    const origData = VILLAGE_DIRECTORY[origin];
    const destData = VILLAGE_DIRECTORY[dest];

    if (!origData || !destData) {
      // Baseline regional default if village not explicitly mapped
      return 15;
    }

    // Haversine formula with road curvature adjustment (1.25x)
    const R = 6371; // km
    const dLat = ((destData.coordinates.lat - origData.coordinates.lat) * Math.PI) / 180;
    const dLon = ((destData.coordinates.lon - origData.coordinates.lon) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((origData.coordinates.lat * Math.PI) / 180) *
        Math.cos((destData.coordinates.lat * Math.PI) / 180) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    const straightDist = R * c;

    return Math.round(straightDist * 1.25 * 10) / 10;
  }

  public static optimizePickupRoute(villages: string[], destinationHub = 'Kolar'): {
    stops: string[];
    totalDistanceKm: number;
  } {
    const uniqueVillages = Array.from(new Set(villages));
    if (uniqueVillages.length === 0) {
      return { stops: [destinationHub], totalDistanceKm: 0 };
    }

    // Sort villages farthest to nearest relative to destination to create a sequential collection run
    const sorted = [...uniqueVillages].sort((a, b) => {
      const distA = VILLAGE_DIRECTORY[a]?.distanceToBangaloreDcKm || 50;
      const distB = VILLAGE_DIRECTORY[b]?.distanceToBangaloreDcKm || 50;
      return distB - distA; // Farthest first
    });

    let current = sorted[0];
    const route = [current];
    let distance = 0;

    for (let i = 1; i < sorted.length; i++) {
      const next = sorted[i];
      distance += this.calculateInterVillageDistance(current, next);
      route.push(next);
      current = next;
    }

    // From final pickup to destination
    const finalToDest = VILLAGE_DIRECTORY[current]?.distanceToBangaloreDcKm || 70;
    distance += finalToDest;
    route.push(destinationHub);

    return {
      stops: route,
      totalDistanceKm: Math.round(distance)
    };
  }

  public static allocateSharedFreight(
    lots: HarvestLot[],
    destination = 'Bengaluru DC, Whitefield'
  ): LogisticsPlanResult {
    if (!lots || lots.length === 0) {
      throw new Error('Cannot allocate freight for empty lot list');
    }

    const totalWeightKg = lots.reduce((acc, l) => acc + l.quantity_kg, 0);
    const vehicle = this.recommendVehicle(totalWeightKg);

    const villages = lots.map((l) => l.village);
    const { stops, totalDistanceKm } = this.optimizePickupRoute(villages, 'Bengaluru DC');

    // Total Freight Cost = Base + (km * perKmRate) + Loading/Unloading
    const totalFreightCost =
      vehicle.baseCharge + totalDistanceKm * vehicle.perKmRate + vehicle.loadingUnloadingCost;

    let totalSoloCost = 0;
    const breakdownPerFarmer: FarmerFreightShare[] = lots.map((lot) => {
      const sharePercentage = Math.round((lot.quantity_kg / totalWeightKg) * 1000) / 10;
      const allocatedFreightCost = Math.round((lot.quantity_kg / totalWeightKg) * totalFreightCost);

      // Solo transport baseline: hiring an entire auto/mini-truck individually to city
      const soloBaseline = 1500 + (VILLAGE_DIRECTORY[lot.village]?.distanceToBangaloreDcKm || 90) * 12;
      totalSoloCost += soloBaseline;

      const savings = Math.max(0, soloBaseline - allocatedFreightCost);
      const savingsPct = Math.round((savings / soloBaseline) * 100);

      return {
        farmerId: lot.farmer_id,
        farmerName: lot.farmer_name,
        lotId: lot.lot_id,
        village: lot.village,
        quantityKg: lot.quantity_kg,
        sharePercentage,
        allocatedFreightCost,
        soloTransportCost: soloBaseline,
        freightSavings: savings,
        savingsPercentage: savingsPct
      };
    });

    const totalSavingsAcrossFarmers = totalSoloCost - totalFreightCost;
    const averageSavingsPercent =
      Math.round(
        (breakdownPerFarmer.reduce((acc, b) => acc + b.savingsPercentage, 0) /
          breakdownPerFarmer.length) *
          10
      ) / 10;

    return {
      planId: `LOG-PLAN-${Date.now().toString(36).toUpperCase()}`,
      recommendedVehicle: vehicle,
      totalWeightKg,
      capacityUtilizationPercent: Math.round((totalWeightKg / vehicle.maxPayloadKg) * 100),
      pickupSequence: stops,
      destination,
      totalDistanceKm,
      totalFreightCost,
      breakdownPerFarmer,
      summary: {
        totalPooledFarmers: lots.length,
        totalSavingsAcrossFarmers,
        averageSavingsPercent,
        co2SavedKgEst: Math.round(lots.length * 28.5) // approx 28.5 kg CO2 saved per consolidated trip
      },
      generatedAt: new Date().toISOString()
    };
  }
}
