import React from "react";
import Link from "next/link";

export default function Protocol() {
  return (
    <main className="min-h-screen bg-[#050505] flex flex-col items-center justify-center relative overflow-hidden px-6">
      {/* Background glow */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[800px] h-[800px] bg-[#C8102E]/10 rounded-full blur-[150px] pointer-events-none" />

      <div className="max-w-3xl mx-auto text-center z-10 animate-in fade-in slide-in-from-bottom-8 duration-1000">
        
        <div className="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-white/5 border border-white/10 text-red-400 text-sm font-bold tracking-widest uppercase mb-8">
          The Aura Fact
        </div>

        <h1 className="text-4xl md:text-6xl font-black text-white leading-tight mb-8">
          Over <span className="text-transparent bg-clip-text bg-gradient-to-r from-red-500 to-red-800">350 million</span> adults in Africa are invisible to traditional banks.
        </h1>

        <p className="text-xl md:text-2xl text-slate-400 leading-relaxed font-light mb-12">
          Yet, they move billions of dollars in daily cash flow. The legacy system says they have no credit. We say the system is broken. 
          <br /><br />
          <strong className="text-white font-semibold">The Aura Protocol</strong> doesn't just score you based on debt. It analyzes your true financial velocity and mints your reputation directly onto the blockchain—making your identity borderless, immutable, and <span className="text-red-400 border-b border-red-400/30 pb-1">100% yours.</span>
        </p>

        <Link 
          href="/"
          className="inline-flex items-center justify-center px-8 py-4 bg-white text-black hover:bg-slate-200 rounded-xl font-bold text-lg transition-all shadow-[0_0_30px_rgba(255,255,255,0.1)]"
        >
          ← Return to the Protocol
        </Link>
      </div>
      
      {/* Cool tech grid background */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#80808012_1px,transparent_1px),linear-gradient(to_bottom,#80808012_1px,transparent_1px)] bg-[size:24px_24px] pointer-events-none [mask-image:radial-gradient(ellipse_60%_50%_at_50%_0%,#000_70%,transparent_100%)]"></div>
    </main>
  );
}