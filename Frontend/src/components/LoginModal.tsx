"use client";

import React, { useState } from "react";
import { motion, AnimatePresence } from "framer-motion";
import OTPInput from "./OTPInput";
import { sendOtp, verifyOtp } from "../lib/api";

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function LoginModal({ isOpen, onClose }: LoginModalProps) {
  const [step, setStep] = useState<"phone" | "otp">("phone");
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handlePhoneSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");

    if (!phone.replace(/\s+/g, '').match(/^\+?[0-9]{10,14}$/)) {
      setError("Please enter a valid phone number (e.g., +234...)");
      return;
    }

    setLoading(true);
    try {
      await sendOtp(phone);
      setStep("otp");
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : "Failed to send OTP. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  const handleVerifyOtp = async () => {
    if (otp.length !== 6) return;
    
    setError("");
    setLoading(true);
    try {
      await verifyOtp(phone, otp);
      onClose();
      setTimeout(() => {
        setStep("phone");
        setPhone("");
        setOtp("");
      }, 500);
    } catch (err: any) {
      setError(err.message || "Invalid OTP. Please try again.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <motion.div 
            initial={{ opacity: 0 }} 
            animate={{ opacity: 1 }} 
            exit={{ opacity: 0 }} 
            className="absolute inset-0 bg-[#09090B]/80 backdrop-blur-md"
            onClick={!loading ? onClose : undefined}
          />
          
          <motion.div 
            initial={{ opacity: 0, scale: 0.95, y: 20 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.95, y: 20 }}
            transition={{ type: "spring", damping: 25, stiffness: 300 }}
            className="relative w-full max-w-md bg-[#18181B] border border-[#27272A] rounded-2xl shadow-[0_0_50px_rgba(0,0,0,0.5)] p-8 overflow-hidden z-10"
          >
            {/* Top Glow */}
            <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-[#C8102E] to-[#2563EB]" />

            <button
              onClick={onClose}
              className="absolute top-6 right-6 text-gray-500 hover:text-white transition-colors"
              disabled={loading}
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>

            <h2 className="text-2xl font-bold text-white mb-2">
              {step === "phone" ? "Welcome to AuraScore" : "Verify Phone"}
            </h2>
            <div className="text-gray-400 mb-8 flex items-center gap-2 text-sm">
              {step === "phone" ? (
                <p>Enter your phone number to continue or create an account.</p>
              ) : (
                <p>We sent a secure code to <span className="text-white font-medium">{phone}</span></p>
              )}
            </div>

            {error && (
              <motion.div initial={{ opacity: 0, y: -10 }} animate={{ opacity: 1, y: 0 }} className="mb-6 p-4 bg-red-900/20 border border-red-500/30 rounded-xl text-red-400 text-sm flex gap-3 items-start">
                <svg className="w-5 h-5 shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" /></svg>
                {error}
              </motion.div>
            )}

            {step === "phone" ? (
              <form onSubmit={handlePhoneSubmit}>
                <div className="mb-6">
                  <label className="block text-sm font-medium text-gray-400 mb-2">Phone Number</label>
                  <input
                    type="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder="+234 800 000 0000"
                    className="w-full px-4 py-3.5 bg-[#09090B] border border-[#27272A] rounded-xl text-white focus:outline-none focus:border-[#C8102E] focus:ring-1 focus:ring-[#C8102E] transition-all"
                    disabled={loading}
                  />
                </div>
                <button
                  type="submit"
                  disabled={loading || phone.replace(/\s+/g, '').length < 10}
                  className="w-full py-4 bg-[#C8102E] hover:bg-[#A10D25] text-white font-semibold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex justify-center items-center h-14"
                >
                  {loading ? (
                     <div className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"></div>
                  ) : "Send Secure OTP"}
                </button>
              </form>
            ) : (
              <motion.div initial={{ opacity: 0, x: 20 }} animate={{ opacity: 1, x: 0 }}>
                <div className="mb-8">
                   <OTPInput length={6} onComplete={setOtp} />
                </div>
                
                {loading && (
                  <p className="text-sm text-center text-gray-400 mb-6 flex items-center justify-center gap-3">
                    <span className="w-4 h-4 border-2 border-[#C8102E]/30 border-t-[#C8102E] rounded-full animate-spin"></span>
                    Encrypting your identity...
                  </p>
                )}

                <button
                  onClick={handleVerifyOtp}
                  disabled={loading || otp.length !== 6}
                  className="w-full py-4 bg-[#C8102E] hover:bg-[#A10D25] text-white font-semibold rounded-xl disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex justify-center items-center h-14"
                >
                  Verify Access
                </button>
                <div className="mt-6 text-center">
                   <button 
                     onClick={() => { setStep("phone"); setOtp(""); setError(""); }} 
                     className="text-sm text-gray-500 hover:text-white transition-colors"
                     disabled={loading}
                   >
                     Wrong number? Start over
                   </button>
                </div>
              </motion.div>
            )}
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
}
