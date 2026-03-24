# AuraScore

**AuraScore** is an AI-powered credit scoring platform built for the modern Nigerian. It leverages real-time transaction data and Interswitch's robust infrastructure to build accurate, fair, and actionable credit profiles that users actively own.

## Features

- **AI Credit Scoring:** Machine learning models analyze daily transactions to generate comprehensive, unbiased credit profiles.
- **Interswitch Integration:** Uses Africa's leading payment gateway to securely verify financial footrpints.
- **Blockchain Identity:** Credit scores can be minted as verifiable credentials on the blockchain, granting true decentralized ownership without centralized lock-in.

## Tech Stack

- **Framework:** Next.js (App Router, TypeScript)
- **Styling:** Tailwind CSS
- **Web3 Integration:** wagmi / viem / ethers.js
- **State/Querying:** @tanstack/react-query

## Getting Started

First, install the backend dependencies (make sure you are in the `Frontend` directory):

```bash
npm install
```

Then, run the development server:

```bash
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

## Structure

- `/src/app`: Application routes and global styles.
- `/src/components`: Reusable UI components including the Landing Page sections, Modals, and OTP Inputs.
- `/src/lib/api.ts`: API integration layer for communicating with the securely hosted backend.
