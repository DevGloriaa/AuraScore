"use client";
import React, { useRef, useState, KeyboardEvent, ChangeEvent } from "react";

interface OTPInputProps {
  length?: number;
  onComplete: (otp: string) => void;
}

export default function OTPInput({ length = 6, onComplete }: OTPInputProps) {
  const [otp, setOtp] = useState<string[]>(new Array(length).fill(""));
  const inputRefs = useRef<(HTMLInputElement | null)[]>([]);

  const handleChange = (e: ChangeEvent<HTMLInputElement>, index: number) => {
    const value = e.target.value;
    if (isNaN(Number(value))) return;

    const newOtp = [...otp];
    newOtp[index] = value.substring(value.length - 1);
    setOtp(newOtp);

    if (value !== "" && index < length - 1) {
      inputRefs.current[index + 1]?.focus();
    }

    const combined = newOtp.join("");
    if (combined.length === length) {
      onComplete(combined);
    } else {
      onComplete(""); 
    }
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>, index: number) => {
    if (e.key === "Backspace" && !otp[index] && index > 0) {
      inputRefs.current[index - 1]?.focus();
    }
  };

  return (
    <div className="flex gap-2 justify-between w-full mx-auto">
      {otp.map((digit, index) => (
        <input
          key={index}
          type="text"
          inputMode="numeric"
          maxLength={1}
          ref={(el) => {
            inputRefs.current[index] = el;
          }}
          value={digit}
          onChange={(e) => handleChange(e, index)}
          onKeyDown={(e) => handleKeyDown(e, index)}
          className="w-10 h-12 sm:w-12 sm:h-14 text-center text-xl font-bold bg-[#09090B] border border-[#27272A] rounded-xl text-white focus:outline-none focus:border-[#C8102E] focus:ring-1 focus:ring-[#C8102E] transition-all shadow-inner"
        />
      ))}
    </div>
  );
}
