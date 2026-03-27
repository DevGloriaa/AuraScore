# 🌟 Aura Score: Decentralized AI Credit Identity
**Submission for the Enyata x Interswitch Buildathon 2026**

🔗 **Live Demo Link:** [https://aurascoreapp.vercel.app](https://aurascoreapp.vercel.app)


---

## 📖 The Problem
Millions of individuals and rising entrepreneurs lack access to fair credit because traditional scoring models rely on static, outdated financial metrics. They fail to capture real-time cash flow, dynamic business activity, and genuine financial habits.

## 💡 Our Solution
**Aura Score** is a next-generation credit scoring engine. We replace outdated credit bureaus with an intelligent, decentralized pipeline. By analyzing live bank transactions with advanced AI, we generate a highly accurate credit profile and permanently mint it to the blockchain as an immutable, user-owned financial identity.

---

## 🚀 The Core Innovation
Our backend orchestrates a complex financial and cryptographic pipeline in under 20 seconds using a single API call:

1. **Open Banking Aggregation:** We integrate with the **Mono API** to securely exchange user consent tokens for live, read-only bank transaction history.
2. **AI Credit Analysis:** We feed the raw, unstructured financial data into **Google Gemini 2.5 Flash**. The AI evaluates cash flow, volatility, and spending habits to generate a dynamic "Aura Score" (300-850) alongside a professional financial narrative (e.g., "Active Business Owner").
3. **Web3 Identity Minting:** Using **Web3j**, our Spring Boot engine automatically signs and sends a transaction to a Solidity Smart Contract on the **Ethereum Sepolia Testnet**, permanently minting the AI's decision as an on-chain credential.

---

## 🛠️ Tech Stack
**Backend & Infrastructure:**
* **Java Spring Boot:** Core orchestration API and routing.
* **Web3j:** EVM blockchain integration and Smart Contract interaction.
* **Solidity:** Custom Smart Contract for Soulbound Token (SBT) credit profiles.
* **Google Gemini API:** AI-driven financial analysis and natural language generation.
* **Mono API:** Secure bank data extraction and webhook handling.

**Frontend & Design:**
* **Frontend:** Modern Web Technologies (HTML/CSS/JS/React)
* **Design:** Figma (UI/UX wireframing and prototyping)
* **Integration:** Mono Connect Widget

---

## 👥 Team & Contributions

This project was built entirely by a 2-person team. As per hackathon requirements, here is the breakdown of our specific contributions:

* 👩‍💻 **Gloria Obiorah (Team Lead)**
  * **Backend Architecture:** Built the core Spring Boot application and REST APIs.
  * **Web3 Integration:** Deployed the Smart Contract and built the Web3j Java integration to mint scores on the Sepolia blockchain.

* 🎨 **Treasure Ehiomhen**
  * **UI/UX Design:** Designed the user interfaces, user flows, and overall product experience.
  * **Frontend Development:** Built the client-side application and integrated the Mono Connect Widget to handle user bank authentication.
  * **Project Documentation:** Managed the product narrative, user flows, and presentation structuring.

---

## 💻 How to Run Locally 

### Prerequisites
* Java 17+
* Maven
* A Mono Developer Account (Public/Secret Keys)
* A Gemini AI API Key
* A Sepolia Wallet (Private Key & RPC URL)

### Setup Instructions
1. Clone the repository:
   ```bash
   git clone https://github.com/DevGloriaa/AuraScore.git
   cd aurascore
