"use client";

import Script from "next/script";

const merchantCode = "MX6072";
const payItemID = "9405967";
const amount = 50000;
const currency = "NGN";
const hash = "YOUR_PAYMENT_HASH";

export default function InterswitchPayment() {
  const handlePayment = () => {
    const txnRef = `AURA-${Date.now()}`;

    if (window.webpayCheckout) {
      window.webpayCheckout({
        merchant_code: merchantCode,
        pay_item_id: payItemID,
        txn_ref: txnRef,
        amount,
        currency,
        hash,
        onPaymentCompleted: (response) => {
          console.log("Payment callback:", response);
        },
        onClose: () => {
          console.log("Interswitch modal closed");
        },
      });
      return;
    }

    const legacyCheckout = window.webpay?.checkout;
    if (legacyCheckout) {
      legacyCheckout({
        merchant_code: merchantCode,
        pay_item_id: payItemID,
        txn_ref: txnRef,
        amount,
        currency,
        hash,
        onPaymentCompleted: (response) => {
          console.log("Legacy payment callback:", response);
        },
        onClose: () => {
          console.log("Legacy Interswitch modal closed");
        },
      });
      return;
    }

    console.error("Interswitch SDK not loaded yet.");
    alert("Payment gateway is still loading. Please try again in a moment.");
  };

  return (
    <>
      <Script
        src="https://newwebpay.qa.interswitchng.com/inline/sdk.js"
        strategy="afterInteractive"
      />

      <button
        type="button"
        onClick={handlePayment}
        className="inline-flex items-center justify-center rounded-xl bg-red-600 px-6 py-3 font-semibold text-white transition hover:bg-red-700"
      >
        Pay Fee
      </button>
    </>
  );
}
