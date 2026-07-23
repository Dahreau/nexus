export interface ProductSummary {
  productId: string;
  productName: string;
  quantity?: number;
}

export interface OrderItem {
  productId: string;
  productName: string;
  quantity: number;
  priceAtPurchase: number;
  sellerId?: string;
}

export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export interface Order {
  id: string;
  userId: string;
  items: OrderItem[];
  totalAmount: number;
  status: OrderStatus;
  paymentMethod: string;
  shippingAddress: string;
  createdAt: string;
  updatedAt: string;
}

export interface UserStats {
  totalSpent: number;
  totalOrders: number;
  topProducts: ProductSummary[];
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface CheckoutRequest {
  shippingAddress: string;
  paymentMethod: string;
  // ajoute d'autres champs si besoin
}