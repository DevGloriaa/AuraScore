"use client";
import React from "react";
import { motion } from "framer-motion";

export default function HowItWorks() {
  const steps = [
    {
      num: "01",
      title: "Verify your phone number",
      description: "Sign in seamlessly using just your phone number via a secure OTP.",
    },
    {
      num: "02",
      title: "AI Evaluation via Interswitch",
      description: "Our proprietary engine evaluates your real-time financial data securely.",
    },
    {
      num: "03",
      title: "Mint your Aura Identity",
      description: "Get your personalized score instantly and mint it as a verifiable credential.",
    }
  ];

  return (
    <section id="how-it-works" className="w-full py-32 bg-[#121214]">
      <div className="max-w-[1200px] mx-auto px-6">
        <motion.div 
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.6 }}
          className="text-center mb-24"
        >
          <h2 className="text-3xl md:text-5xl font-bold text-white mb-6">How It Works</h2>
          <p className="text-gray-400 max-w-2xl mx-auto text-lg">Get access to decentralized credit in three simple steps.</p>
        </motion.div>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-12 relative">
          {/* Connecting line for desktop */}
          <div className="hidden md:block absolute top-[28px] left-[16%] right-[16%] h-[2px] bg-gradient-to-r from-[#C8102E]/0 via-[#C8102E]/50 to-[#2563EB]/0" />

          {steps.map((step, idx) => (
            <motion.div 
              key={idx} 
              initial={{ opacity: 0, scale: 0.9 }}
              whileInView={{ opacity: 1, scale: 1 }}
              viewport={{ once: true, margin: "-50px" }}
              transition={{ duration: 0.5, delay: idx * 0.2 }}
              className="relative flex flex-col items-center text-center group"
            >
              <div className="w-14 h-14 rounded-full bg-[#18181B] border-2 border-[#C8102E] shadow-[0_0_15px_rgba(200,16,46,0.3)] group-hover:shadow-[0_0_25px_rgba(200,16,46,0.6)] group-hover:scale-110 group-hover:border-[#F97316] transition-all duration-300 flex items-center justify-center text-white font-bold text-lg mb-8 relative z-10">
                {step.num}
              </div>
              <h3 className="text-xl font-bold text-white mb-3">{step.title}</h3>
              <p className="text-gray-400 leading-relaxed text-sm max-w-[280px]">
                {step.description}
              </p>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}
