"use client";
import React from "react";
import { motion } from "framer-motion";

export default function HowItWorks() {
  const steps = [
    {
      num: "01",
      title: "Fund API Wallet",
      description: "Top up your institutional wallet securely using the Interswitch Payment Gateway to enable pay-per-call access.",
    },
    {
      num: "02",
      title: "Connect Mono API",
      description: "Ingest real-time user transaction history and financial data seamlessly via our secure Mono integration.",
    },
    {
      num: "03",
      title: "AI Risk Evaluation",
      description: "Our proprietary engine analyzes the raw transaction data to assess creditworthiness and default probability.",
    },
    {
      num: "04",
      title: "Retrieve Aura Score",
      description: "Receive an instant, highly accurate alternative credit profile to inform your lending decisions in milliseconds.",
    }
  ];

  return (
    <section id="how-it-works" className="w-full py-32 bg-[#0A0A0C] overflow-hidden">
      <div className="max-w-[1200px] mx-auto px-6 relative">
        
        {/* Header Section */}
        <motion.div 
          initial={{ opacity: 0, y: 30 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, margin: "-100px" }}
          transition={{ duration: 0.6 }}
          className="text-center mb-24 relative z-10"
        >
          <div className="inline-block mb-4 px-3 py-1 rounded-full bg-[#18181B] border border-[#27272A] text-[#C8102E] text-sm font-semibold tracking-wider">
            SYSTEM WORKFLOW
          </div>
          <h2 className="text-4xl md:text-5xl font-extrabold text-white mb-6 tracking-tight">
            How Aura Score Works
          </h2>
          <p className="text-gray-400 max-w-2xl mx-auto text-lg leading-relaxed">
            Integrate alternative credit profiling into your lending platform in four seamless steps.
          </p>
        </motion.div>

        {/* Steps Container */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-12 md:gap-6 relative">
          
          {/* Desktop Horizontal Connecting Line */}
          <div className="hidden md:block absolute top-[36px] left-[10%] right-[10%] h-[2px] bg-gradient-to-r from-[#C8102E]/0 via-[#C8102E]/40 to-[#C8102E]/0 z-0" />
          
          {/* Mobile Vertical Connecting Line */}
          <div className="md:hidden absolute top-[10%] bottom-[10%] left-[39px] w-[2px] bg-gradient-to-b from-[#C8102E]/0 via-[#C8102E]/40 to-[#C8102E]/0 z-0" />

          {steps.map((step, idx) => (
            <motion.div 
              key={idx} 
              initial={{ opacity: 0, y: 40 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, margin: "-50px" }}
              transition={{ duration: 0.5, delay: idx * 0.15 }}
              className="relative flex flex-row md:flex-col items-start md:items-center text-left md:text-center group z-10"
            >
              {/* Number Icon */}
              <div className="shrink-0 w-20 h-20 md:w-16 md:h-16 rounded-full bg-[#121214] border border-[#27272A] group-hover:border-[#C8102E] shadow-xl group-hover:shadow-[0_0_30px_rgba(200,16,46,0.2)] transition-all duration-500 flex items-center justify-center text-white font-black text-xl md:text-lg mb-0 md:mb-8 mr-6 md:mr-0 relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-b from-[#C8102E]/10 to-transparent opacity-0 group-hover:opacity-100 transition-opacity duration-500" />
                <span className="relative z-10">{step.num}</span>
              </div>

              {/* Text Content */}
              <div className="pt-2 md:pt-0">
                <h3 className="text-xl font-bold text-gray-100 mb-3 group-hover:text-white transition-colors duration-300">
                  {step.title}
                </h3>
                <p className="text-gray-400 leading-relaxed text-sm max-w-[260px] mx-auto group-hover:text-gray-300 transition-colors duration-300">
                  {step.description}
                </p>
              </div>
            </motion.div>
          ))}
        </div>
      </div>
    </section>
  );
}