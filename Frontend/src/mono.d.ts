declare module '@mono.co/connect.js';

interface InterswitchPaymentResponse {
	resp?: string;
	[key: string]: unknown;
}

interface InterswitchCheckoutPayload {
	merchant_code: string;
	pay_item_id: string;
	txn_ref: string;
	amount: number;
	currency: string;
	hash: string;
	onPaymentCompleted: (response: InterswitchPaymentResponse) => void;
	onClose?: () => void;
}

interface Window {
	webpay?: {
		checkout?: (payload: InterswitchCheckoutPayload) => void;
	};
	webpayCheckout?: (payload: InterswitchCheckoutPayload) => void;
	__iswInlineLoadPromise?: Promise<void>;
}