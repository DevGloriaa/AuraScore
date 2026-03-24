"use client";

import React, { useState } from "react";
import Navbar from "@/components/Navbar";
import Hero from "@/components/Hero";
import Features from "@/components/Features";
import HowItWorks from "@/components/HowItWorks";
import Footer from "@/components/Footer";
import LoginModal from "@/components/LoginModal";

export default function Home() {
  const [isLoginModalOpen, setIsLoginModalOpen] = useState(false);

  const handleOpenLogin = () => setIsLoginModalOpen(true);
  const handleCloseLogin = () => setIsLoginModalOpen(false);

  return (
    <main className="min-h-screen bg-[#09090B] text-white font-sans scroll-smooth">
      <Navbar />
      <Hero onLoginClick={handleOpenLogin} />
      <Features />
      <HowItWorks />
      <Footer />
      
      <LoginModal 
        isOpen={isLoginModalOpen} 
        onClose={handleCloseLogin} 
      />
    </main>
  );
}
