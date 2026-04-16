"use client";
import React from "react";
import { motion } from "framer-motion";

interface HeroProps {
  onLoginClick: () => void;
}

export default function Hero({ onLoginClick }: HeroProps) {
  return (
    <section className="relative w-full min-h-screen flex items-center pt-24 pb-32 overflow-hidden bg-[#09090B]">
      {/* Background Ambient Glows */}
      <div className="absolute top-0 left-1/4 w-[500px] h-[500px] bg-[#C8102E]/15 rounded-full blur-[120px] -translate-y-1/2 pointer-events-none" />
      <div className="absolute bottom-0 right-1/4 w-[600px] h-[600px] bg-[#2563EB]/10 rounded-full blur-[150px] translate-y-1/4 pointer-events-none" />

      <div className="max-w-[1200px] mx-auto px-6 grid grid-cols-1 lg:grid-cols-2 gap-16 items-center w-full z-10">
        
        {/* LEFT COLUMN */}
        <div className="max-w-xl">
          <motion.div 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.1 }}
            className="inline-flex items-center gap-2 px-3 py-1.5 rounded-full bg-[#18181B] border border-[#27272A] text-gray-300 text-sm font-medium mb-8 shadow-lg"
          >
            <span className="w-2 h-2 rounded-full bg-[#C8102E] animate-pulse shadow-[0_0_8px_#C8102E]"></span>
            Powered by Mono & Gemini AI
          </motion.div>
          
          <div className="mb-6 overflow-hidden">
             <motion.h1 
               initial={{ y: "100%", opacity: 0 }}
               animate={{ y: 0, opacity: 1 }}
               transition={{ duration: 0.9, ease: [0.16, 1, 0.3, 1] }}
               className="text-5xl md:text-7xl font-bold text-white leading-[1.1] tracking-tight"
             >
               Decentralized credit for the <br/>
               <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#C8102E] via-[#F97316] to-[#C8102E] animate-gradient-x">modern web.</span>
             </motion.h1>
          </div>

          <motion.p 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="text-lg text-gray-400 mb-10 leading-relaxed max-w-lg"
          >
            AuraScore securely aggregates your financial history, runs quantitative AI analysis, and mints an immutable, self-sovereign credit profile. You own your data.
          </motion.p>
          
          <motion.div 
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="flex flex-col sm:flex-row gap-4"
          >
            <button 
              onClick={onLoginClick}
              className="relative px-8 py-4 bg-[#C8102E] text-white font-semibold rounded-xl overflow-hidden group shadow-[0_0_25px_rgba(200,16,46,0.25)] border border-[#C8102E]/50"
            >
              <span className="relative z-10">Get Started</span>
              <div className="absolute inset-0 h-full w-full bg-gradient-to-r from-[#C8102E] to-[#F97316] opacity-0 group-hover:opacity-100 transition-opacity duration-500 ease-out" />
            </button>
            <a 
              href="#simulator"
              className="px-8 py-4 bg-[#18181B] hover:bg-[#27272A] text-white font-semibold rounded-xl border border-[#27272A] transition-colors text-center"
            >
              Learn more
            </a>
          </motion.div>
        </div>

        {/* RIGHT COLUMN - Dashboard Graphic */}
        <motion.div 
          initial={{ opacity: 0, scale: 0.95, filter: "blur(10px)" }}
          animate={{ opacity: 1, scale: 1, filter: "blur(0px)" }}
          transition={{ duration: 1.2, delay: 0.4, ease: [0.16, 1, 0.3, 1] }}
          className="relative w-full h-[500px] flex items-center justify-center lg:translate-x-8"
        >
          {/* Dashboard Container */}
          <div className="absolute w-full max-w-md bg-[#18181B]/80 backdrop-blur-2xl border border-[#27272A] shadow-2xl rounded-3xl p-8 z-10">
            <div className="flex justify-between items-center mb-10">
              <h3 className="text-gray-400 font-medium">Aura Identity Profile</h3>
              <div className="bg-[#2563EB]/10 text-[#2563EB] text-xs px-3 py-1.5 rounded-full border border-[#2563EB]/30 font-medium flex items-center gap-1.5">
                <div className="w-1.5 h-1.5 rounded-full bg-[#2563EB]"></div>
                Verified On-Chain
              </div>
            </div>
            
            <div className="flex flex-col items-center justify-center mb-10">
              <div className="relative flex items-center justify-center w-56 h-56">
                <svg className="absolute inset-0 w-full h-full -rotate-90">
                  <circle cx="50%" cy="50%" r="46%" fill="transparent" stroke="#27272A" strokeWidth="6" className="opacity-50"></circle>
                  <circle cx="50%" cy="50%" r="46%" fill="transparent" stroke="url(#gradient)" strokeWidth="6" strokeDasharray="289" strokeDashoffset="40" strokeLinecap="round" className="drop-shadow-[0_0_15px_rgba(200,16,46,0.3)]"></circle>
                  <defs>
                    <linearGradient id="gradient" x1="0%" y1="0%" x2="100%" y2="100%">
                      <stop offset="0%" stopColor="#C8102E" />
                      <stop offset="100%" stopColor="#2563EB" />
                    </linearGradient>
                  </defs>
                </svg>
                <div className="text-center">
                  <span className="text-7xl font-bold font-mono text-white tracking-tighter shadow-sm">785</span>
                  <p className="text-sm text-gray-500 mt-1 uppercase tracking-widest font-semibold flex items-center justify-center gap-1">
                    High Velocity
                    <svg className="w-4 h-4 text-green-500" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" /></svg>
                  </p>
                </div>
              </div>
            </div>

            <div className="space-y-4 mb-8">
              <div className="bg-[#09090B]/60 p-4 rounded-xl border border-[#27272A] flex gap-4 items-start shadow-inner">
                 <div className="w-8 h-8 rounded-lg bg-[#2563EB]/10 flex items-center justify-center shrink-0 border border-[#2563EB]/20">
                    <svg className="w-4 h-4 text-[#2563EB]" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 10V3L4 14h7v7l9-11h-7z" /></svg>
                 </div>
                 <div>
                    <p className="text-xs text-gray-400 mb-1">Quantitative Insight</p>
                    <p className="text-white font-medium text-sm leading-snug">Consistent cash flow velocity aligns perfectly with the Diamond-Handed Builder persona.</p>
                 </div>
              </div>
            </div>

            <button disabled className="w-full py-4 bg-[#18181B] text-green-500 font-medium rounded-xl border border-[#27272A]/50 cursor-not-allowed">
              Soulbound Token Active
            </button>
          </div>
          
          {/* Decorative Elements */}
          <div className="absolute top-[10%] -right-[5%] w-32 h-32 bg-gradient-to-br from-[#18181B] to-[#27272A] border border-[#3F3F46] rounded-2xl -z-10 shadow-xl opacity-80 backdrop-blur-md -rotate-6"></div>
          <div className="absolute bottom-[20%] -left-[10%] w-24 h-24 bg-[#09090B] border border-[#2563EB]/30 rounded-full -z-10 shadow-[0_0_30px_rgba(37,99,235,0.2)] backdrop-blur-md"></div>
        </motion.div>
      </div>
    </section>
  );
}