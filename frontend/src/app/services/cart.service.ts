import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, Subject } from 'rxjs';

export interface CartRequest {
  productId: string;
  quantity: number;
}

export interface Cart {
  id?: string;
  userId?: string;
  items?: any[];
  totalPrice?: number;
}

@Injectable({
  providedIn: 'root'
})
export class CartService {
  private readonly apiUrl = 'http://localhost:8085/api/carts';

  // Lets any component (e.g. ProductListComponent, which isn't a template
  // child of AppComponent and can't ViewChild it) tell the cart panel to
  // reload without depending on the component tree.
  private readonly cartUpdated = new Subject<void>();
  readonly cartUpdated$ = this.cartUpdated.asObservable();

  notifyCartUpdated(): void {
    this.cartUpdated.next();
  }

  constructor(private readonly http: HttpClient) {}

  getCart(): Observable<Cart> {
    return this.http.get<Cart>(this.apiUrl);
  }

  addToCart(request: CartRequest): Observable<Cart> {
    return this.http.post<Cart>(this.apiUrl, request);
  }

  updateQuantity(request: CartRequest): Observable<Cart> {
    return this.http.put<Cart>(this.apiUrl, request);
  }

  removeFromCart(productId: string): Observable<Cart> {
    return this.http.delete<Cart>(`${this.apiUrl}/${productId}`);
  }

  clearCart(): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/clear`);
  }
  checkoutCart(payload: any): Observable<any> {
  return this.http.post('http://localhost:8084/api/orders/checkout', payload);
  }
}