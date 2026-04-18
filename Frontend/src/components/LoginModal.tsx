"use client";
import React, { useEffect, useRef, useState } from "react";
// @ts-ignore
import Connect from "@mono.co/connect.js";
import { ethers } from "ethers";

interface LoginModalProps {
  isOpen: boolean;
  onClose: () => void;
}

// FIXED: Strictly matching the backend's snake_case JSON response
interface InitiatePaymentResponse {
  txn_ref: string;
  hash: string;
  product_id: string;
  pay_item_id: string;
  amount: string;
  currency: string;
  site_redirect_url: string;
}

type CheckoutFn = (payload: any) => void;

const INTERSWITCH_SDK_URL = "https://newwebpay.qa.interswitchng.com/inline-checkout.js";
const INTERSWITCH_SCRIPT_ID = "isw-inline-sdk";
const INTERSWITCH_AMOUNT = 50000;
const INTERSWITCH_CURRENCY = 566;
// FIXED: Defaulting to Vercel to match Interswitch Origin checks
const DEFAULT_REDIRECT_URL = "https://aurascoreapp.vercel.app/";

const API_BASE_URL = 
  typeof window !== 'undefined' && window.location.hostname === 'localhost'
    ? "http://localhost:8000"
    : "https://aurascore.onrender.com";

// 1. Safely resolve the checkout function without crashing
function resolveCheckoutFunction(): CheckoutFn | null {
  if (typeof window === 'undefined') return null;
  
  if ((window as any).webpayCheckout) {
    return (window as any).webpayCheckout;
  }

  const legacyCheckout = (window as any).webpay?.checkout;
  if (legacyCheckout) {
    return (payload: any) => legacyCheckout(payload);
  }

  return null;
}

// 2. The Judge-Proof SDK Loader (No CORS triggers, resilient to MetaMask)
async function loadInterswitchSDK(): Promise<CheckoutFn> {
  return new Promise((resolve, reject) => {
    // Fast path: already loaded
    const existingIsw = resolveCheckoutFunction();
    if (existingIsw) return resolve(existingIsw);

    let script = document.getElementById(INTERSWITCH_SCRIPT_ID) as HTMLScriptElement;

    // Inject purely standard script tag (NO crossOrigin attribute)
    if (!script) {
      script = document.createElement("script");
      script.id = INTERSWITCH_SCRIPT_ID;
      script.src = INTERSWITCH_SDK_URL;
      script.async = true;
      document.body.appendChild(script);
    }

    // Polling fallback to survive browser extension crashes
    let attempts = 0;
    const maxAttempts = 24; // 12 seconds max (500ms * 24)

    const checkInterval = setInterval(() => {
      attempts++;
      const isw = resolveCheckoutFunction();
      
      if (isw) {
        clearInterval(checkInterval);
        console.log("Interswitch SDK Loaded via Polling");
        resolve(isw);
      } else if (attempts >= maxAttempts) {
        clearInterval(checkInterval);
        reject(new Error("Gateway blocked by network or browser extension. Please check your connection."));
      }
    }, 500);
  });
}

export default function LoginModal({ isOpen, onClose }: LoginModalProps) {
  if (!isOpen) return null;

  const [step, setStep] = useState<number>(1);
  const [email, setEmail] = useState<string>("");
  const [walletAddress, setWalletAddress] = useState<string>("");
  const [monoCode, setMonoCode] = useState<string>("");
  const [txnRef, setTxnRef] = useState<string>("");
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [loadingMessage, setLoadingMessage] = useState<string>("Initializing protocol...");
  const [error, setError] = useState<string | null>(null);
  const [auraResult, setAuraResult] = useState<any>(null);

  const intervalRef = useRef<number | null>(null);
  const monoInstance = useRef<any>(null);

  useEffect(() => {
    // Pre-load the SDK quietly in the background
    loadInterswitchSDK().catch(() => {
      console.warn("Silent SDK pre-load timeout. Will retry on click.");
    });

    monoInstance.current = new Connect({
      key: "test_pk_tpejozw1np7i11dpxzxj",
      onSuccess: (data: any) => {
        const code = data?.code || "";
        setMonoCode(code);
        const addr = ethers.Wallet.createRandom().address;
        setWalletAddress(addr);
        setStep(3);
      },
    });
    monoInstance.current.setup();
  }, []);

  function startMono() {
    try {
      if (monoInstance.current) {
        monoInstance.current.open();
      } else {
        setError("Please wait, the secure connection is initializing.");
      }
    } catch (e) {
      setError("Failed to establish a secure connection. Please try again.");
    }
  }

  async function verifyPayment(txnReference: string) {
    const verifyRes = await fetch(`${API_BASE_URL.replace(/\/+$/, "")}/api/v1/score/verify-payment`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ txnRef: txnReference }),
    });

    const verifyJson = await verifyRes.json().catch(() => ({}));
    if (!verifyRes.ok) {
      throw new Error(
        verifyJson.error || verifyJson.details || "Payment verification failed."
      );
    }
  }

  async function launchInterswitchCheckout() {
    setError(null);
    setIsLoading(true);
    setLoadingMessage("Opening Interswitch Payment Gateway...");

    try {
      // Safely await the hardened loader
      const checkout = await loadInterswitchSDK();

      const initRes = await fetch(`${API_BASE_URL}/api/v1/score/initiate-payment`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: email || "demo@user.com" }),
      });

      const initJson = await initRes.json().catch(() => ({}));
      if (!initRes.ok) {
        throw new Error(
          initJson.error ||
            initJson.details ||
            `Unable to initialize payment (Status: ${initRes.status})`
        );
      }

      // FIXED: Destructure using the EXACT snake_case keys from the backend
      const payload: InitiatePaymentResponse = initJson?.data ?? initJson;
      const {
        txn_ref: paymentTxnRef,
        product_id,
        pay_item_id
      } = payload;

      if (!paymentTxnRef || !product_id || !pay_item_id) {
        throw new Error("Payment initialization returned incomplete checkout details.");
      }

      
      const checkoutPayload = {
        merchant_code: product_id,    
        pay_item_id: pay_item_id,
        pay_item_name: "Aura Score Underwriting",
        txn_ref: paymentTxnRef,
        amount: INTERSWITCH_AMOUNT,
        currency: INTERSWITCH_CURRENCY,
        cust_email: email || "demo@user.com",
        cust_name: "Aura Applicant",
        site_redirect_url: "https://aurascoreapp.vercel.app/", 
        mode: 'TEST',                 
        onComplete: async (response: any) => {
          const responseCode = String(response?.resp || "");
          if (responseCode !== "00") {
            setError(`Payment was not successful (resp: ${responseCode || "N/A"}).`);
            setIsLoading(false);
            return;
          }

          try {
            setLoadingMessage("Verifying payment...");
            await verifyPayment(paymentTxnRef);
            setTxnRef(paymentTxnRef);
            setStep(4);
          } catch (verificationError: any) {
            setError(
              verificationError?.message ||
                "Payment could not be verified. Please contact support."
            );
          } finally {
            setIsLoading(false);
          }
        },
        onClose: () => {
          setIsLoading(false);
        },
      };

      console.log("INTERSWITCH PAYLOAD:", checkoutPayload);
      checkout(checkoutPayload);
    } catch (err: any) {
      setError(err?.message || "Failed to launch Interswitch checkout.");
      setIsLoading(false);
    }
  }

  useEffect(() => {
    if (!isLoading || step === 3) return; 
    
    const messages = [
      "Securing identity...", 
      "Aggregating financial history...", 
      "Running hashing algorithm...", 
      "Executing AI risk evaluation...",
      "Finalizing credit profile..."
    ];
    let idx = 0;
    setLoadingMessage(messages[idx]);
    intervalRef.current = window.setInterval(() => {
      idx = (idx + 1) % messages.length;
      setLoadingMessage(messages[idx]);
    }, 4000) as unknown as number;
    
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [isLoading, step]);

  async function handleGenerate() {
    setError(null);
    setIsLoading(true);

    try {
      const fetchPromise = fetch(`${API_BASE_URL}/api/v1/score/generate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          code: monoCode,
          walletAddress: walletAddress, 
          customerReference: email || "demo@user.com",
          transactionReference: txnRef,
          identityData: {
            fullName: "Gloria Obiorah", 
            bvn: "00000000000"        
          }
        }),
      });

      const timeoutPromise = new Promise((_, reject) => 
        setTimeout(() => reject(new Error("Request timed out. Please verify network stability.")), 90000)
      );

      const res = await Promise.race([fetchPromise, timeoutPromise]) as Response;
      const json = await res.json();

      if (!res.ok) {
        throw new Error(json.error || json.details || `Service unavailable (Status: ${res.status})`);
      }
      
      console.log("Analysis Complete:", json);
      setAuraResult(json); 

    } catch (err: any) {
      console.error("System Notice:", err);
      setError(err.message || "An unexpected error occurred. Please try again.");
    } finally {
      setIsLoading(false);
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    }
  }

  function resetAll() {
    setStep(1);
    setEmail("");
    setWalletAddress("");
    setMonoCode("");
    setTxnRef("");
    setIsLoading(false);
    setError(null);
    setAuraResult(null);
  }

  const Spinner = () => (
    <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
    </svg>
  );

  const stepsList = [
    { num: 1, label: "Applicant Details" },
    { num: 2, label: "Fetch Financials" },
    { num: 3, label: "Underwriting Fee" },
    { num: 4, label: "Risk Assessment" },
  ];

  return (
    <div className="fixed inset-0 z-50 flex h-screen w-screen bg-[#0a0f16] text-white font-sans overflow-hidden">
      
      {/* LEFT SIDEBAR - The Progress Panel */}
      <div className="w-1/3 max-w-sm bg-[#0d131f] border-r border-white/5 p-10 flex flex-col relative z-20 shadow-2xl">
        <div className="mb-16">
          <h1 className="text-3xl font-black tracking-tighter bg-clip-text text-transparent bg-gradient-to-r from-red-500 to-red-800">
            AURA SCORE
          </h1>
          <p className="text-sm text-slate-500 mt-1 font-medium">Alternative Credit Truth.</p>
        </div>

        <div className="flex-1 space-y-10">
          {stepsList.map((s) => {
            const isActive = step === s.num;
            const isPast = step > s.num;
            return (
              <div key={s.num} className={`flex items-center gap-4 transition-all duration-300 ${isActive ? "opacity-100 translate-x-2" : "opacity-40"}`}>
                <div className={`flex items-center justify-center w-10 h-10 rounded-full font-bold text-sm transition-all duration-500
                  ${isActive ? "bg-red-600 text-white shadow-[0_0_20px_rgba(220,38,38,0.5)] border border-red-400" : 
                    isPast ? "bg-slate-800 text-slate-400 border border-slate-700" : "bg-transparent border border-slate-700 text-slate-600"}`}>
                  {isPast ? "✓" : s.num}
                </div>
                <span className={`font-semibold ${isActive ? "text-white" : "text-slate-400"}`}>{s.label}</span>
              </div>
            );
          })}
        </div>

        <button onClick={onClose} disabled={isLoading} className="mt-auto px-6 py-3 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors border border-white/5 w-max">
          Close Session
        </button>
      </div>

      {/* RIGHT MAIN AREA - The Action Panel */}
      <div className="flex-1 relative flex items-center justify-center p-12">
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-red-900/15 rounded-full blur-[120px] pointer-events-none" />

        <div className="relative w-full max-w-2xl bg-white/[0.02] border border-white/10 backdrop-blur-2xl p-12 rounded-[2rem] shadow-2xl z-10">
          
          {error && (
            <div className="mb-8 p-4 bg-red-950/50 border border-red-900 rounded-xl flex items-center justify-between">
              <div>
                <div className="text-red-400 font-semibold text-sm">Notice</div>
                <div className="text-red-200 text-sm mt-1">{error}</div>
              </div>
              <button onClick={() => setError(null)} className="px-4 py-2 bg-red-900/50 hover:bg-red-800/50 rounded-lg text-sm text-red-200 transition-colors">
                Dismiss
              </button>
            </div>
          )}

          {auraResult ? (
            <div className="text-center space-y-6 animate-in fade-in duration-700">
              <div className="inline-block px-4 py-1.5 bg-red-500/10 border border-red-500/20 text-red-400 rounded-full text-sm font-bold tracking-widest uppercase">
                {String(auraResult?.analysis?.persona ?? "Applicant Profile")}
              </div>
              
              <h2 className="text-[7rem] leading-none font-black text-transparent bg-clip-text bg-gradient-to-b from-white to-slate-500 drop-shadow-2xl">
                {String(auraResult?.analysis?.auraScore ?? "—")}
              </h2>

              <div className="bg-black/40 border border-white/10 rounded-2xl p-6 text-left space-y-4 max-w-lg mx-auto shadow-inner relative overflow-hidden">
                <div className="absolute top-0 left-0 w-full h-1 bg-gradient-to-r from-red-500/0 via-red-500/50 to-red-500/0"></div>
                
                <div className="flex items-center gap-2 mb-2">
                  <div className="w-2 h-2 rounded-full bg-red-500 animate-pulse"></div>
                  <span className="text-xs font-bold text-red-400 tracking-widest uppercase">AI Risk Analysis</span>
                </div>
                
                <p className="text-sm text-gray-300 leading-relaxed">
                  {String(auraResult?.analysis?.recommendation ?? "Financial data has been successfully evaluated against the underwriting risk model.")}
                </p>
                
                <div className="grid grid-cols-2 gap-4 pt-4 border-t border-white/5 mt-4">
                  <div>
                    <div className="text-xs text-slate-500 mb-1">Financial Velocity</div>
                    <div className="text-sm font-semibold text-white">{String(auraResult?.analysis?.financialVelocity ?? "Moderate")}</div>
                  </div>
                  <div>
                    <div className="text-xs text-slate-500 mb-1">Top Spending</div>
                    <div className="text-sm font-semibold text-white">{String(auraResult?.analysis?.topSpendingCategory ?? "General")}</div>
                  </div>
                </div>
              </div>

              <div className="pt-4 flex justify-center gap-4">
                <a href={String(auraResult?.blockchain?.blockchainReceipt ?? "#")} target="_blank" rel="noreferrer" 
                   className="inline-flex items-center px-8 py-4 bg-white text-black font-bold rounded-xl hover:bg-slate-200 transition-colors shadow-[0_0_30px_rgba(255,255,255,0.2)]">
                  Verify-on-chain
                </a>
                <button onClick={resetAll} className="px-8 py-4 bg-white/5 hover:bg-white/10 border border-white/10 rounded-xl font-bold transition-colors">
                  Close Review
                </button>
              </div>
            </div>
          ) : (
            <div className="min-h-[280px] flex flex-col justify-center">
              
              {step === 1 && (
                <div className="space-y-6 animate-in fade-in zoom-in-95 duration-500">
                  <div>
                    <h2 className="text-3xl font-bold mb-2">Initialize Applicant Review.</h2>
                    <p className="text-slate-400">Enter email to begin the applicant underwriting workflow.</p>
                  </div>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="Enter email address"
                    className="w-full px-6 py-4 bg-black/40 border border-white/10 rounded-xl text-white placeholder-slate-600 focus:outline-none focus:border-red-500 focus:ring-1 focus:ring-red-500 transition-all text-lg"
                  />
                  <div className="flex justify-end pt-4">
                    <button onClick={() => setStep(2)} disabled={!email || isLoading} 
                            className="flex items-center justify-center px-8 py-4 w-full sm:w-auto bg-gradient-to-r from-red-600 to-red-800 hover:from-red-500 hover:to-red-700 disabled:opacity-50 rounded-xl font-bold shadow-lg shadow-red-900/30 transition-all">
                      Proceed
                    </button>
                  </div>
                </div>
              )}

              {step === 2 && (
                <div className="space-y-6 animate-in fade-in zoom-in-95 duration-500">
                  <div>
                    <h2 className="text-3xl font-bold mb-2">Fetch Financial data</h2>
                    <p className="text-slate-400">Securely pull read-only transaction history to support the applicant review.</p>
                  </div>
                  <div className="pt-4">
                    <button onClick={startMono} disabled={isLoading} 
                            className="w-full flex items-center justify-center px-8 py-5 bg-white text-black hover:bg-slate-200 disabled:opacity-50 rounded-xl font-bold text-lg transition-all shadow-[0_0_30px_rgba(255,255,255,0.1)]">
                      Authenticate
                    </button>
                  </div>
                </div>
              )}

              {step === 3 && (
                <div className="space-y-6 animate-in fade-in zoom-in-95 duration-500">
                  <div>
                    <h2 className="text-3xl font-bold mb-2">Underwriting Fee.</h2>
                    <p className="text-slate-400">A standard API processing fee is required to underwrite this applicant using Aura Score.</p>
                  </div>
                  <div className="pt-4 flex items-center justify-between p-6 bg-black/40 border border-white/10 rounded-xl">
                    <div className="font-medium text-slate-300">Fee Due</div>
                    <div className="font-bold text-2xl text-white">₦500.00</div>
                  </div>
                  <div className="flex justify-end pt-4">
                    <button onClick={launchInterswitchCheckout} disabled={isLoading} 
                            className="flex items-center justify-center px-8 py-4 w-full sm:w-auto bg-gradient-to-r from-red-600 to-red-800 hover:from-red-500 hover:to-red-700 disabled:opacity-50 rounded-xl font-bold shadow-lg shadow-red-900/30 transition-all">
                      {isLoading ? <><Spinner /> Processing...</> : "Collect Fee"}
                    </button>
                  </div>
                </div>
              )}

              {step === 4 && (
                <div className="space-y-6 animate-in fade-in zoom-in-95 duration-500 text-center">
                  <div>
                    <h2 className="text-3xl font-bold mb-2">Finalizing Risk Assessment.</h2>
                    <p className="text-slate-400">Fee confirmed. Proceed to generate the applicant risk profile.</p>
                  </div>
                  
                  {isLoading ? (
                    <div className="py-12 flex flex-col items-center justify-center space-y-6">
                      <div className="relative">
                        <div className="w-20 h-20 border-4 border-red-500/20 border-t-red-600 rounded-full animate-spin"></div>
                        <div className="absolute inset-0 w-20 h-20 border-4 border-transparent border-b-red-400/50 rounded-full animate-spin direction-reverse"></div>
                      </div>
                      <div className="text-xl font-medium text-red-400 animate-pulse">{loadingMessage}</div>
                    </div>
                  ) : (
                    <div className="pt-8 flex flex-col sm:flex-row gap-4 justify-center">
                      <button onClick={handleGenerate} disabled={!monoCode || !walletAddress || !txnRef} 
                              className="flex items-center justify-center px-10 py-5 bg-gradient-to-r from-red-600 to-red-800 hover:from-red-500 hover:to-red-700 disabled:opacity-50 rounded-xl font-bold text-lg shadow-[0_0_40px_rgba(220,38,38,0.4)] transition-all">
                        Generate Risk Profile
                      </button>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}