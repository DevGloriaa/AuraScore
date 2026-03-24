"use client";

import React, { useState, useEffect } from "react";
import { motion } from "framer-motion";

export default function Navbar() {
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    const handleScroll = () => {
      setScrolled(window.scrollY > 20);
    };
    window.addEventListener("scroll", handleScroll);
    return () => window.removeEventListener("scroll", handleScroll);
  }, []);

  return (
    <motion.nav 
      initial={{ y: -100 }}
      animate={{ y: 0 }}
      transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
      className={`fixed top-0 w-full z-40 transition-all duration-300 ${
        scrolled ? "bg-[#09090B]/80 backdrop-blur-xl border-b border-[#27272A] shadow-lg py-4" : "bg-transparent py-6"
      }`}
    >
      <div className="max-w-[1200px] mx-auto px-6 flex items-center justify-between">
        <div className="flex items-center gap-3 cursor-pointer group">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#C8102E] to-[#F97316] p-[1px] shadow-[0_0_15px_rgba(200,16,46,0.5)] group-hover:shadow-[0_0_25px_rgba(200,16,46,0.8)] transition-all duration-300">
            <div className="w-full h-full bg-[#18181B] rounded-[11px] flex items-center justify-center">
              <span className="text-transparent bg-clip-text bg-gradient-to-br from-[#C8102E] to-[#F97316] font-bold text-xl leading-none">A</span>
            </div>
          </div>
          <span className="text-white font-bold text-xl tracking-wide">AuraScore</span>
        </div>

        <div className="hidden md:flex items-center gap-8">
          <a href="#features" className="text-gray-400 hover:text-white transition-colors font-medium">Features</a>
          <a href="#how-it-works" className="text-gray-400 hover:text-white transition-colors font-medium">How It Works</a>
        </div>
      </div>
    </motion.nav>
  );
}
