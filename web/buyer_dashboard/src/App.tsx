import React, { useState, useEffect } from 'react';
import { 
  Truck, 
  ShoppingBag, 
  TrendingUp, 
  ShieldCheck, 
  CheckCircle2, 
  MapPin, 
  ArrowRight, 
  Layers, 
  Scale,
  Sparkles,
  RefreshCw,
  PhoneCall
} from 'lucide-react';

interface VehicleSpec {
  id: string;
  name: string;
  model: string;
  maxPayloadKg: number;
  baseCharge: number;
  perKmRate: number;
}

interface FarmerFreightShare {
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

interface LogisticsPlan {
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
}

export function App() {
  const [activeTab, setActiveTab] = useState<'requirements' | 'matching' | 'logistics' | 'inspection' | 'reconciliation'>('requirements');
  const [activeBuyer, setActiveBuyer] = useState('Reliance Retail (Bengaluru DC, Whitefield)');
  const [isPrivacyUnlocked, setIsPrivacyUnlocked] = useState(false);
  const [orderStage, setOrderStage] = useState<number>(3); // 0 to 6
  const [logisticsPlan, setLogisticsPlan] = useState<LogisticsPlan | null>(null);
  const [loading, setLoading] = useState(false);

  // Pre-seeded 600kg tomato pool (6 farmers x 100kg)
  const [farmerPool] = useState([
    { id: 'FR-KLR-00251', name: 'Manjunath Gowda', village: 'Mulbagal', qty: 100, phone: '+91 98450 11001', grade: 'Grade A', status: 'Accepted' },
    { id: 'FR-KLR-00252', name: 'Ramesh Reddy', village: 'Devarayasamudra', qty: 100, phone: '+91 98450 11002', grade: 'Grade A', status: 'Accepted' },
    { id: 'FR-KLR-00253', name: 'Anjanappa', village: 'Avani', qty: 100, phone: '+91 98450 11003', grade: 'Grade A', status: 'Accepted' },
    { id: 'FR-KLR-00254', name: 'Shivaraj K', village: 'Tayalur', qty: 100, phone: '+91 98450 11004', grade: 'Grade A', status: 'Accepted' },
    { id: 'FR-KLR-00255', name: 'Venkatesh Murthy', village: 'Byrakur', qty: 100, phone: '+91 98450 11005', grade: 'Grade A', status: 'Accepted' },
    { id: 'FR-KLR-00256', name: 'Lakshmamma', village: 'Nangali', qty: 100, phone: '+91 98450 11006', grade: 'Grade A', status: 'Accepted' },
  ]);

  const stages = [
    { title: 'ORDER_CONFIRMED', desc: 'Buyer PO confirmed for 600kg Grade A Tomato' },
    { title: 'PICKUP_VERIFIED', desc: 'Consolidated Tata Ace dispatched to Kolar corridor' },
    { title: 'ADVANCE_RELEASED (SIMULATED)', desc: '30% advance disbursed against confirmed PO' },
    { title: 'DELIVERED', desc: 'Arrived at Whitefield Distribution Center' },
    { title: 'WEIGHED & INSPECTED', desc: '588 kg verified (Grade A: 510kg, Grade B: 60kg, Waste: 18kg)' },
    { title: 'FINAL_PAYMENT (SIMULATED)', desc: 'Net balance settled directly to farmer accounts' },
    { title: 'COMPLETED', desc: 'Digital receipt generated and transaction closed' }
  ];

  // Fetch logistics plan on load
  useEffect(() => {
    fetchLogistics();
  }, []);

  const fetchLogistics = async () => {
    setLoading(true);
    try {
      const res = await fetch('/api/logistics/plan', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ destination: 'Bengaluru DC, Whitefield' })
      });
      if (res.ok) {
        const data = await res.json();
        setLogisticsPlan(data.plan);
      }
    } catch (err) {
      console.error('Failed to fetch logistics plan:', err);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 flex flex-col font-sans">
      {/* Top Banner */}
      <div className="bg-emerald-600 text-emerald-950 px-4 py-2 text-xs font-semibold flex items-center justify-between">
        <div className="flex items-center space-x-2">
          <span className="bg-emerald-950 text-emerald-100 px-2 py-0.5 rounded text-[10px] tracking-wider uppercase">DEMO DATA</span>
          <span>FarmRoute AI — Live Prototype connected to AGMARKNET & Sarvam AI Indic Engine</span>
        </div>
        <div className="flex items-center space-x-4">
          <span>Kolar Tomato Benchmark: ₹24.00/kg (Agmarknet)</span>
          <span className="bg-emerald-800 text-emerald-100 px-2 py-0.5 rounded text-[10px]">Android ADB Live: A015</span>
        </div>
      </div>

      {/* Main Header */}
      <header className="border-b border-slate-800 bg-slate-950/80 backdrop-blur px-6 py-4 flex items-center justify-between sticky top-0 z-50">
        <div className="flex items-center space-x-3">
          <div className="bg-emerald-500 text-slate-950 p-2 rounded-xl font-bold shadow-lg shadow-emerald-500/20">
            <TrendingUp className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-xl font-bold bg-gradient-to-r from-emerald-400 to-teal-200 bg-clip-text text-transparent">
              FarmRoute AI
            </h1>
            <p className="text-xs text-slate-400">Institutional Buyer Portal & Virtual FPO Exchange</p>
          </div>
        </div>

        <div className="flex items-center space-x-4">
          <div className="text-right">
            <span className="text-xs text-slate-400 block">Active Buyer Account</span>
            <select 
              value={activeBuyer} 
              onChange={(e) => setActiveBuyer(e.target.value)}
              className="bg-slate-800 border border-slate-700 text-sm rounded-lg px-3 py-1 text-slate-200 focus:outline-none focus:border-emerald-500"
            >
              <option>Reliance Retail (Bengaluru DC, Whitefield)</option>
              <option>BigBasket Fresh (Hosakote Hub)</option>
              <option>Nandi Food Processing Ltd (Malur)</option>
              <option>Empire Restaurant Group (Bulk Kitchen)</option>
            </select>
          </div>
          <button 
            onClick={fetchLogistics}
            className="p-2 bg-slate-800 hover:bg-slate-700 rounded-lg text-slate-300 border border-slate-700 transition"
            title="Refresh Engines"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </header>

      {/* Navigation Tabs */}
      <nav className="border-b border-slate-800 bg-slate-950 px-6 flex space-x-6 text-sm">
        <button 
          onClick={() => setActiveTab('requirements')}
          className={`py-3 font-medium flex items-center space-x-2 border-b-2 transition ${activeTab === 'requirements' ? 'border-emerald-500 text-emerald-400' : 'border-transparent text-slate-400 hover:text-slate-200'}`}
        >
          <ShoppingBag className="w-4 h-4" />
          <span>1. Bulk Requirement</span>
        </button>
        <button 
          onClick={() => setActiveTab('matching')}
          className={`py-3 font-medium flex items-center space-x-2 border-b-2 transition ${activeTab === 'matching' ? 'border-emerald-500 text-emerald-400' : 'border-transparent text-slate-400 hover:text-slate-200'}`}
        >
          <Layers className="w-4 h-4" />
          <span>2. Virtual FPO Match (600 kg)</span>
        </button>
        <button 
          onClick={() => setActiveTab('logistics')}
          className={`py-3 font-medium flex items-center space-x-2 border-b-2 transition ${activeTab === 'logistics' ? 'border-emerald-500 text-emerald-400' : 'border-transparent text-slate-400 hover:text-slate-200'}`}
        >
          <Truck className="w-4 h-4" />
          <span>3. Logistics Engine (Task 5)</span>
        </button>
        <button 
          onClick={() => setActiveTab('inspection')}
          className={`py-3 font-medium flex items-center space-x-2 border-b-2 transition ${activeTab === 'inspection' ? 'border-emerald-500 text-emerald-400' : 'border-transparent text-slate-400 hover:text-slate-200'}`}
        >
          <Scale className="w-4 h-4" />
          <span>4. Weighment & Escrow</span>
        </button>
        <button 
          onClick={() => setActiveTab('reconciliation')}
          className={`py-3 font-medium flex items-center space-x-2 border-b-2 transition ${activeTab === 'reconciliation' ? 'border-emerald-500 text-emerald-400' : 'border-transparent text-slate-400 hover:text-slate-200'}`}
        >
          <Sparkles className="w-4 h-4" />
          <span>5. Netback Advantage</span>
        </button>
      </nav>

      {/* Main Content Body */}
      <main className="flex-1 p-6 max-w-7xl mx-auto w-full space-y-6">

        {/* Tab 1: Requirements */}
        {activeTab === 'requirements' && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            <div className="md:col-span-2 bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 space-y-5">
              <div className="flex items-center justify-between border-b border-slate-700/60 pb-4">
                <div>
                  <h2 className="text-lg font-bold text-slate-100">Institutional Purchase Requirement</h2>
                  <p className="text-xs text-slate-400">Post verified bulk demand to trigger automated Virtual FPO consolidation</p>
                </div>
                <span className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/30 px-2.5 py-1 rounded-full text-xs font-semibold">
                  Active PO #REQ-001
                </span>
              </div>

              <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <label className="text-xs text-slate-400 block mb-1">Target Crop</label>
                  <input readOnly value="Tomato (Hybrid Red)" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-slate-200 font-medium" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">Quality Grade Required</label>
                  <input readOnly value="Grade A (Table Variety)" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-slate-200 font-medium" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">Total Target Volume</label>
                  <input readOnly value="600 kg (Min 500 kg)" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-emerald-400 font-bold" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">Contract Offered Price</label>
                  <input readOnly value="₹28.50 / kg" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-emerald-400 font-bold" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">Delivery Destination</label>
                  <input readOnly value="Bengaluru DC, Whitefield" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-slate-200" />
                </div>
                <div>
                  <label className="text-xs text-slate-400 block mb-1">Target Sourcing Hub</label>
                  <input readOnly value="Kolar Central Corridor (Mulbagal Cluster)" className="w-full bg-slate-900 border border-slate-700 rounded-lg px-3 py-2 text-slate-200" />
                </div>
              </div>

              <div className="bg-slate-900/80 border border-slate-700/80 rounded-xl p-4 flex items-center justify-between">
                <div>
                  <h4 className="font-semibold text-sm text-slate-200">Matching Status</h4>
                  <p className="text-xs text-emerald-400">100% Volume Matched across 6 Smallholder Lots (600 kg)</p>
                </div>
                <button 
                  onClick={() => setActiveTab('matching')}
                  className="bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-bold text-xs px-4 py-2.5 rounded-lg flex items-center space-x-1.5 transition"
                >
                  <span>Review Virtual FPO Match</span>
                  <ArrowRight className="w-3.5 h-3.5" />
                </button>
              </div>
            </div>

            {/* Price Intelligence Widget */}
            <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 space-y-4">
              <h3 className="font-bold text-sm text-slate-200 flex items-center space-x-2">
                <span>Real-Time Mandi Price Benchmark</span>
                <span className="text-[10px] bg-slate-700 text-slate-300 px-1.5 py-0.5 rounded">Agmarknet</span>
              </h3>
              <div className="space-y-3">
                <div className="bg-slate-900/80 p-3 rounded-xl flex items-center justify-between border border-slate-700/40">
                  <div>
                    <span className="text-xs text-slate-400 block">Kolar APMC</span>
                    <span className="text-lg font-bold text-slate-100">₹24.00<span className="text-xs font-normal text-slate-400">/kg</span></span>
                  </div>
                  <span className="text-xs text-slate-400">Modal Rate</span>
                </div>
                <div className="bg-slate-900/80 p-3 rounded-xl flex items-center justify-between border border-slate-700/40">
                  <div>
                    <span className="text-xs text-slate-400 block">Bangarpet APMC</span>
                    <span className="text-lg font-bold text-amber-400">₹28.00<span className="text-xs font-normal text-slate-400">/kg</span></span>
                  </div>
                  <span className="text-[10px] bg-amber-500/20 text-amber-300 px-1.5 py-0.5 rounded">Headline Trap</span>
                </div>
                <div className="bg-emerald-950/40 border border-emerald-500/30 p-3 rounded-xl flex items-center justify-between">
                  <div>
                    <span className="text-xs text-emerald-400 block font-medium">Virtual FPO Direct</span>
                    <span className="text-lg font-bold text-emerald-300">₹28.50<span className="text-xs font-normal text-emerald-400">/kg</span></span>
                  </div>
                  <span className="text-[10px] bg-emerald-500/20 text-emerald-300 px-2 py-0.5 rounded font-bold">Best Net</span>
                </div>
              </div>
              <p className="text-[11px] text-slate-400 leading-relaxed">
                Direct procurement bypasses APMC market cess (2%) and middleman commission (4–5%), unlocking higher returns for both buyer and farmer.
              </p>
            </div>
          </div>
        )}

        {/* Tab 2: Matching */}
        {activeTab === 'matching' && (
          <div className="space-y-6">
            <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6">
              <div className="flex items-center justify-between mb-4">
                <div>
                  <h2 className="text-lg font-bold text-slate-100">Virtual FPO Aggregation Proposal (600 kg)</h2>
                  <p className="text-xs text-slate-400">6 Smallholder Tomato Lots Combined to Fulfill Reliance PO #REQ-001</p>
                </div>
                <div className="flex items-center space-x-3">
                  <button 
                    onClick={() => setIsPrivacyUnlocked(!isPrivacyUnlocked)}
                    className={`text-xs px-3 py-1.5 rounded-lg border font-medium flex items-center space-x-1.5 transition ${isPrivacyUnlocked ? 'bg-emerald-500/20 text-emerald-300 border-emerald-500/40' : 'bg-amber-500/20 text-amber-300 border-amber-500/40'}`}
                  >
                    <ShieldCheck className="w-3.5 h-3.5" />
                    <span>{isPrivacyUnlocked ? 'Privacy: Mutual Accepted (Unlocked)' : 'Privacy Gate: Active (Masked)'}</span>
                  </button>
                  <button 
                    onClick={() => setActiveTab('logistics')}
                    className="bg-emerald-500 hover:bg-emerald-600 text-slate-950 font-bold text-xs px-4 py-2 rounded-lg flex items-center space-x-1 transition"
                  >
                    <span>View Logistics Dispatch</span>
                    <ArrowRight className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>

              {/* Farmer Supply Table */}
              <div className="overflow-x-auto rounded-xl border border-slate-700/60">
                <table className="w-full text-left text-sm">
                  <thead className="bg-slate-900/80 text-xs text-slate-400 uppercase tracking-wider">
                    <tr>
                      <th className="py-3 px-4">Harvest Lot ID</th>
                      <th className="py-3 px-4">Farmer Name</th>
                      <th className="py-3 px-4">Village / Corridor</th>
                      <th className="py-3 px-4">Quantity</th>
                      <th className="py-3 px-4">Quality Grade</th>
                      <th className="py-3 px-4">Contact Privacy</th>
                      <th className="py-3 px-4">Offer Status</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-700/60 text-slate-300">
                    {farmerPool.map((f, idx) => (
                      <tr key={f.id} className="hover:bg-slate-800/40 transition">
                        <td className="py-3 px-4 font-mono font-semibold text-emerald-400">{f.id}</td>
                        <td className="py-3 px-4 font-medium">{isPrivacyUnlocked ? f.name : `Farmer #${idx + 1} (Kolar Cluster)`}</td>
                        <td className="py-3 px-4 flex items-center space-x-1">
                          <MapPin className="w-3.5 h-3.5 text-slate-500" />
                          <span>{f.village}</span>
                        </td>
                        <td className="py-3 px-4 font-bold text-slate-100">{f.qty} kg</td>
                        <td className="py-3 px-4">
                          <span className="bg-emerald-500/20 text-emerald-300 px-2 py-0.5 rounded text-xs font-semibold">{f.grade}</span>
                        </td>
                        <td className="py-3 px-4 font-mono text-xs text-slate-400">
                          {isPrivacyUnlocked ? (
                            <span className="text-emerald-300 flex items-center space-x-1">
                              <PhoneCall className="w-3 h-3" />
                              <span>{f.phone}</span>
                            </span>
                          ) : (
                            <span className="text-slate-500">•••• •••••• (Mutual Acceptance Required)</span>
                          )}
                        </td>
                        <td className="py-3 px-4">
                          <span className="bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 px-2 py-0.5 rounded text-xs font-medium flex items-center space-x-1 w-max">
                            <CheckCircle2 className="w-3 h-3" />
                            <span>{f.status}</span>
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: Logistics Engine (Task 5) */}
        {activeTab === 'logistics' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
              {/* Vehicle Assignment Card */}
              <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-700/60 pb-3">
                  <div className="flex items-center space-x-2">
                    <Truck className="w-5 h-5 text-emerald-400" />
                    <h3 className="font-bold text-sm text-slate-100">Recommended Transport Vehicle</h3>
                  </div>
                  <span className="bg-emerald-500/20 text-emerald-300 px-2 py-0.5 rounded text-xs font-bold">Task 5</span>
                </div>

                {logisticsPlan ? (
                  <div className="space-y-3">
                    <div>
                      <span className="text-lg font-bold text-slate-100">{logisticsPlan.recommendedVehicle.name}</span>
                      <p className="text-xs text-slate-400">{logisticsPlan.recommendedVehicle.model}</p>
                    </div>

                    <div className="grid grid-cols-2 gap-3 text-xs bg-slate-900/60 p-3 rounded-xl border border-slate-700/40">
                      <div>
                        <span className="text-slate-400 block">Total Payload</span>
                        <span className="font-bold text-slate-200">{logisticsPlan.totalWeightKg} kg</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Max Capacity</span>
                        <span className="font-bold text-slate-200">{logisticsPlan.recommendedVehicle.maxPayloadKg} kg</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Capacity Utilization</span>
                        <span className="font-bold text-emerald-400">{logisticsPlan.capacityUtilizationPercent}%</span>
                      </div>
                      <div>
                        <span className="text-slate-400 block">Total Route Distance</span>
                        <span className="font-bold text-slate-200">{logisticsPlan.totalDistanceKm} km</span>
                      </div>
                    </div>

                    <div className="p-3 bg-emerald-950/40 border border-emerald-500/30 rounded-xl">
                      <span className="text-xs text-emerald-400 block font-medium">Consolidated Route Freight Cost</span>
                      <span className="text-2xl font-bold text-emerald-300">₹{logisticsPlan.totalFreightCost}</span>
                      <p className="text-[11px] text-emerald-400/80 mt-1">Split proportionally: ~₹{Math.round(logisticsPlan.totalFreightCost / 6)} per farmer</p>
                    </div>
                  </div>
                ) : (
                  <p className="text-xs text-slate-400">Loading logistics engine calculation...</p>
                )}
              </div>

              {/* Waypoint Routing Sequence */}
              <div className="md:col-span-2 bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 space-y-4">
                <div className="flex items-center justify-between border-b border-slate-700/60 pb-3">
                  <div>
                    <h3 className="font-bold text-sm text-slate-100">Optimized Rural Pickup Route (Road-Curvature 1.25x)</h3>
                    <p className="text-xs text-slate-400">Sequential village pickup order to minimize transit time and carbon footprint</p>
                  </div>
                  <span className="text-xs text-slate-400">Kolar $\rightarrow$ Whitefield DC</span>
                </div>

                {logisticsPlan && (
                  <div className="flex items-center justify-between bg-slate-900/80 p-4 rounded-xl border border-slate-700/40 overflow-x-auto">
                    {logisticsPlan.pickupSequence.map((stop, idx) => (
                      <React.Fragment key={stop}>
                        <div className="flex flex-col items-center text-center px-2">
                          <span className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold mb-1 ${idx === logisticsPlan.pickupSequence.length - 1 ? 'bg-emerald-500 text-slate-950' : 'bg-slate-700 text-slate-200'}`}>
                            {idx + 1}
                          </span>
                          <span className="text-xs font-semibold text-slate-200 whitespace-nowrap">{stop}</span>
                          <span className="text-[10px] text-slate-500">{idx === 0 ? 'Start' : idx === logisticsPlan.pickupSequence.length - 1 ? 'Destination' : 'Pickup Hub'}</span>
                        </div>
                        {idx < logisticsPlan.pickupSequence.length - 1 && (
                          <div className="h-0.5 bg-slate-700 flex-1 mx-2 min-w-[20px]" />
                        )}
                      </React.Fragment>
                    ))}
                  </div>
                )}

                {/* Farmer Savings Breakdown Table */}
                <div className="space-y-2">
                  <h4 className="text-xs font-bold uppercase text-slate-400 tracking-wider">Per-Farmer Proportional Freight Allocation</h4>
                  <div className="overflow-x-auto rounded-xl border border-slate-700/40">
                    <table className="w-full text-left text-xs">
                      <thead className="bg-slate-900/60 text-slate-400">
                        <tr>
                          <th className="py-2.5 px-3">Farmer</th>
                          <th className="py-2.5 px-3">Village</th>
                          <th className="py-2.5 px-3">Qty</th>
                          <th className="py-2.5 px-3">Pooled Freight</th>
                          <th className="py-2.5 px-3">Solo Hire Baseline</th>
                          <th className="py-2.5 px-3 text-emerald-400">Farmer Savings</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-700/40 text-slate-300">
                        {logisticsPlan?.breakdownPerFarmer.map((f) => (
                          <tr key={f.farmerId} className="hover:bg-slate-800/30">
                            <td className="py-2 px-3 font-medium text-slate-200">{f.farmerName}</td>
                            <td className="py-2 px-3 text-slate-400">{f.village}</td>
                            <td className="py-2 px-3 font-bold">{f.quantityKg} kg</td>
                            <td className="py-2 px-3 font-semibold text-slate-100">₹{f.allocatedFreightCost}</td>
                            <td className="py-2 px-3 text-slate-500 line-through">₹{f.soloTransportCost}</td>
                            <td className="py-2 px-3 text-emerald-400 font-bold">+₹{f.freightSavings} ({f.savingsPercentage}%)</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 4: Inspection & Escrow */}
        {activeTab === 'inspection' && (
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Delivery Lifecycle Timeline */}
            <div className="md:col-span-2 bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 space-y-6">
              <div className="flex items-center justify-between border-b border-slate-700/60 pb-3">
                <div>
                  <h3 className="font-bold text-sm text-slate-100">Order Delivery & Verification Lifecycle</h3>
                  <p className="text-xs text-slate-400">Order #FR-ORD-2026-000001 (Simulated Payment Milestones)</p>
                </div>
                <div className="flex space-x-2">
                  <button 
                    disabled={orderStage <= 0}
                    onClick={() => setOrderStage((s) => Math.max(0, s - 1))}
                    className="px-2.5 py-1 bg-slate-800 border border-slate-700 rounded text-xs disabled:opacity-40"
                  >
                    Prev Stage
                  </button>
                  <button 
                    disabled={orderStage >= stages.length - 1}
                    onClick={() => setOrderStage((s) => Math.min(stages.length - 1, s + 1))}
                    className="px-2.5 py-1 bg-emerald-500 text-slate-950 font-bold rounded text-xs disabled:opacity-40"
                  >
                    Next Stage
                  </button>
                </div>
              </div>

              <div className="space-y-4">
                {stages.map((stg, idx) => (
                  <div key={stg.title} className="flex items-start space-x-3">
                    <div className={`w-6 h-6 rounded-full flex items-center justify-center text-xs font-bold mt-0.5 ${idx <= orderStage ? 'bg-emerald-500 text-slate-950' : 'bg-slate-800 text-slate-500 border border-slate-700'}`}>
                      {idx <= orderStage ? '✓' : idx + 1}
                    </div>
                    <div>
                      <span className={`text-sm font-semibold block ${idx <= orderStage ? 'text-slate-100' : 'text-slate-500'}`}>
                        {stg.title}
                      </span>
                      <span className="text-xs text-slate-400">{stg.desc}</span>
                    </div>
                  </div>
                ))}
              </div>

              <div className="p-3 bg-amber-500/10 border border-amber-500/30 rounded-xl text-xs text-amber-300">
                <span className="font-bold block mb-0.5">NBFC Advance Funding Disclaimer:</span>
                "In production, the 30% advance payout is funded by a partner bank/NBFC against the buyer's confirmed PO upon collection verification."
              </div>
            </div>

            {/* Quality Grade Split Breakdown */}
            <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 space-y-4">
              <h3 className="font-bold text-sm text-slate-100 flex items-center space-x-2">
                <Scale className="w-4 h-4 text-emerald-400" />
                <span>Collection Center Weighment</span>
              </h3>

              <div className="space-y-3 text-xs">
                <div className="bg-slate-900/60 p-3 rounded-xl border border-slate-700/40 space-y-1">
                  <div className="flex justify-between">
                    <span className="text-slate-400">Declared Batch Total</span>
                    <span className="font-bold text-slate-200">600.0 kg</span>
                  </div>
                  <div className="flex justify-between">
                    <span className="text-slate-400">Actual Weighed Total</span>
                    <span className="font-bold text-emerald-400">588.0 kg</span>
                  </div>
                  <div className="flex justify-between text-amber-400 text-[11px]">
                    <span>Transit Moisture Variance</span>
                    <span>-12.0 kg (2.0%)</span>
                  </div>
                </div>

                <div className="space-y-2">
                  <span className="text-slate-400 font-semibold block">Multi-Buyer Grade Split Allocation:</span>
                  <div className="p-2.5 bg-slate-900/60 rounded-lg border border-slate-700/40 flex justify-between items-center">
                    <div>
                      <span className="font-bold text-slate-200 block">Grade A (85%)</span>
                      <span className="text-[10px] text-slate-400">510 kg $\rightarrow$ Reliance Supermarket @ ₹28.50</span>
                    </div>
                    <span className="font-bold text-emerald-400">₹14,535</span>
                  </div>
                  <div className="p-2.5 bg-slate-900/60 rounded-lg border border-slate-700/40 flex justify-between items-center">
                    <div>
                      <span className="font-bold text-slate-200 block">Grade B (10%)</span>
                      <span className="text-[10px] text-slate-400">60 kg $\rightarrow$ Empire Hotel Kitchen @ ₹22.00</span>
                    </div>
                    <span className="font-bold text-slate-300">₹1,320</span>
                  </div>
                  <div className="p-2.5 bg-slate-900/60 rounded-lg border border-slate-700/40 flex justify-between items-center">
                    <div>
                      <span className="font-bold text-slate-200 block">Grade C (3%)</span>
                      <span className="text-[10px] text-slate-400">18 kg $\rightarrow$ Nandi Tomato Processor @ ₹17.00</span>
                    </div>
                    <span className="font-bold text-slate-400">₹306</span>
                  </div>
                </div>

                <div className="p-3 bg-emerald-950/40 border border-emerald-500/30 rounded-xl flex justify-between items-center">
                  <span className="font-bold text-emerald-300">Total Net Settlement</span>
                  <span className="text-lg font-bold text-emerald-400">₹16,161</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 5: Reconciliation */}
        {activeTab === 'reconciliation' && (
          <div className="bg-slate-800/60 border border-slate-700/60 rounded-2xl p-6 space-y-6">
            <div className="border-b border-slate-700/60 pb-3">
              <h2 className="text-lg font-bold text-slate-100">Farmer Actual vs. Expected Reconciliation</h2>
              <p className="text-xs text-slate-400">Exposing the Bangarpet headline price trap and proving the Virtual FPO advantage</p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="bg-slate-900/80 p-4 rounded-xl border border-slate-700/60 space-y-3">
                <div className="flex justify-between items-center">
                  <span className="font-bold text-slate-200">Kolar APMC Mandi</span>
                  <span className="text-xs bg-slate-800 text-slate-400 px-2 py-0.5 rounded">Traditional</span>
                </div>
                <div className="space-y-1 text-xs">
                  <div className="flex justify-between text-slate-400"><span>Headline Rate</span><span>₹24.00/kg</span></div>
                  <div className="flex justify-between text-slate-400"><span>Gross Value (100kg)</span><span>₹2,400</span></div>
                  <div className="flex justify-between text-red-400"><span>Solo Transport</span><span>- ₹350</span></div>
                  <div className="flex justify-between text-red-400"><span>Commission (4%)</span><span>- ₹96</span></div>
                  <div className="flex justify-between text-red-400"><span>APMC Cess (2%)</span><span>- ₹48</span></div>
                  <div className="flex justify-between text-red-400"><span>Rejection Loss</span><span>- ₹150</span></div>
                  <div className="border-t border-slate-700/60 pt-2 flex justify-between font-bold text-slate-200 text-sm">
                    <span>Net Payout</span>
                    <span>₹1,756</span>
                  </div>
                </div>
              </div>

              <div className="bg-slate-900/80 p-4 rounded-xl border border-amber-500/30 space-y-3">
                <div className="flex justify-between items-center">
                  <span className="font-bold text-amber-400">Bangarpet APMC</span>
                  <span className="text-xs bg-amber-500/20 text-amber-300 px-2 py-0.5 rounded">Headline Trap</span>
                </div>
                <div className="space-y-1 text-xs">
                  <div className="flex justify-between text-slate-400"><span>Headline Rate</span><span className="text-amber-400 font-bold">₹28.00/kg</span></div>
                  <div className="flex justify-between text-slate-400"><span>Gross Value (100kg)</span><span>₹2,800</span></div>
                  <div className="flex justify-between text-red-400"><span>High Transport</span><span>- ₹750</span></div>
                  <div className="flex justify-between text-red-400"><span>Commission (5%)</span><span>- ₹140</span></div>
                  <div className="flex justify-between text-red-400"><span>APMC Cess (2%)</span><span>- ₹56</span></div>
                  <div className="flex justify-between text-red-400"><span>Rejection Loss</span><span>- ₹250</span></div>
                  <div className="border-t border-slate-700/60 pt-2 flex justify-between font-bold text-amber-300 text-sm">
                    <span>Net Payout</span>
                    <span>₹1,604</span>
                  </div>
                </div>
                <p className="text-[11px] text-amber-400/80 italic">Lower net money than Kolar despite ₹4 higher advertised price!</p>
              </div>

              <div className="bg-emerald-950/40 p-4 rounded-xl border border-emerald-500/40 space-y-3 shadow-lg shadow-emerald-500/10">
                <div className="flex justify-between items-center">
                  <span className="font-bold text-emerald-300">Virtual FPO Pooling</span>
                  <span className="text-xs bg-emerald-500/20 text-emerald-300 px-2 py-0.5 rounded font-bold">Recommended</span>
                </div>
                <div className="space-y-1 text-xs">
                  <div className="flex justify-between text-slate-300"><span>Contract Rate</span><span className="text-emerald-400 font-bold">₹28.50/kg</span></div>
                  <div className="flex justify-between text-slate-300"><span>Gross Value (100kg)</span><span>₹2,850</span></div>
                  <div className="flex justify-between text-emerald-400 font-semibold"><span>Prorated Freight</span><span>- ₹370</span></div>
                  <div className="flex justify-between text-emerald-400 font-semibold"><span>Commission (0%)</span><span>₹0</span></div>
                  <div className="flex justify-between text-emerald-400 font-semibold"><span>APMC Fee (0%)</span><span>₹0</span></div>
                  <div className="flex justify-between text-red-400"><span>Minor Variance (2%)</span><span>- ₹57</span></div>
                  <div className="border-t border-emerald-500/40 pt-2 flex justify-between font-bold text-emerald-300 text-base">
                    <span>Final Net Payout</span>
                    <span>₹2,423</span>
                  </div>
                </div>
                <div className="p-2 bg-emerald-500/20 rounded text-[11px] text-emerald-300 font-bold text-center">
                  +₹667 (+38%) Extra Cash Over Mandi
                </div>
              </div>
            </div>
          </div>
        )}

      </main>

      {/* Footer */}
      <footer className="border-t border-slate-800 bg-slate-950 px-6 py-3 text-xs text-slate-500 flex items-center justify-between">
        <span>FarmRoute AI • REVA-Sarvam Hackathon • Built collaboratively by Agents P, C, and H</span>
        <span>Physical Device: CMF A015 (Android 16 via ADB) • Central Repo: https://github.com/Reputable2772/REVA-Sarvam</span>
      </footer>
    </div>
  );
}
