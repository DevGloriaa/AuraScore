"use client";
import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";

export default function Simulator() {
  const [volume, setVolume] = useState(50);
  const [consistency, setConsistency] = useState(50);
  const [history, setHistory] = useState(50);
  const [score, setScore] = useState(0);

  // The "Algorithm"
  useEffect(() => {
    // Weighting the factors to generate a score out of 850
    const calculatedScore = Math.floor(
      (volume * 0.35 + consistency * 0.45 + history * 0.2) * 8.5
    );
    setScore(calculatedScore > 850 ? 850 : calculatedScore);
  }, [volume, consistency, history]);

  // Determine Aura Level
  const getAuraLevel = () => {
    if (score >= 750) return { label: "God Tier", color: "text-red-400" };
    if (score >= 600) return { label: "Verified Builder", color: "text-white" };
    if (score >= 400) return { label: "Rising Star", color: "text-slate-300" };
    return { label: "Ghost", color: "text-slate-500" };
  };

  const aura = getAuraLevel();

  return (
    <section className="py-24 relative overflow-hidden bg-[#050505]">
      {/* Background Glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-red-900/10 blur-[150px] pointer-events-none rounded-full" />

      <div className="max-w-7xl mx-auto px-6 relative z-10 flex flex-col lg:flex-row items-center gap-16">
        
        {/* Left Side: The Copy */}
        <div className="lg:w-1/2 space-y-8">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true }}
            transition={{ duration: 0.8 }}
          >
            <h2 className="text-4xl md:text-5xl font-black text-white mb-6 leading-tight">
              Don't take our word for it. <br />
              <span className="text-transparent bg-clip-text bg-gradient-to-r from-red-500 to-red-800">
                Test the protocol.
              </span>
            </h2>
            <p className="text-lg text-slate-400 leading-relaxed">
              Traditional credit scores penalize you for not using credit cards. The Aura Protocol looks at your actual cash flow, consistency, and on-chain footprint. Play with the simulator to see how your daily habits translate to your decentralized identity.
            </p>
          </motion.div>
        </div>

        {/* Right Side: The Interactive Glass Card */}
        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          whileInView={{ opacity: 1, scale: 1 }}
          viewport={{ once: true }}
          transition={{ duration: 0.8, delay: 0.2 }}
          className="lg:w-1/2 w-full max-w-md bg-white/[0.02] border border-white/10 backdrop-blur-2xl p-8 rounded-3xl shadow-2xl relative"
        >
          {/* Score Display */}
          <div className="text-center mb-10">
            <div className="text-sm font-bold tracking-widest text-slate-500 uppercase mb-2">Simulated Aura</div>
            <div className="text-7xl font-black text-white drop-shadow-[0_0_15px_rgba(255,255,255,0.2)] transition-all duration-300">
              {score}
            </div>
            <div className={`mt-2 font-medium uppercase tracking-widest text-sm transition-colors duration-300 ${aura.color}`}>
              {aura.label}
            </div>
          </div>

          {/* Sliders */}
          <div className="space-y-8">
            <SliderControl label="Transaction Volume" value={volume} setValue={setVolume} />
            <SliderControl label="Income Consistency" value={consistency} setValue={setConsistency} />
            <SliderControl label="Wallet Age (Months)" value={history} setValue={setHistory} />
          </div>
        </motion.div>
      </div>
    </section>
  );
}

// Mini-component for the sliders
function SliderControl({ label, value, setValue }: { label: string; value: number; setValue: (val: number) => void }) {
  return (
    <div className="space-y-3">
      <div className="flex justify-between text-sm font-medium">
        <span className="text-slate-300">{label}</span>
        <span className="text-red-400">{value}%</span>
      </div>
      <input
        type="range"
        min="0"
        max="100"
        value={value}
        onChange={(e) => setValue(Number(e.target.value))}
        className="w-full h-2 bg-black/50 rounded-lg appearance-none cursor-pointer accent-red-600 focus:outline-none focus:ring-2 focus:ring-red-500/50"
      />
    </div>
  );
}