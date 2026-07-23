import { Component, OnInit } from '@angular/core';
import { CartService, Cart, CartRequest } from './services/cart.service';
import { Router } from '@angular/router';

@Component({
  selector: 'app-cart',
  templateUrl: 'cart.component.html',
  styleUrls: ['../styles/ui.css']
})
export class CartComponent implements OnInit {
  cart: Cart | null = null;
  isOpen = false;
  shippingAddress: string = '';

  constructor(
  private readonly cartService: CartService,
  private readonly router: Router      // ← ajouter
) {}

  ngOnInit(): void {
    this.loadCart();
    // Reload whenever another component (e.g. the product list) adds/changes an item.
    this.cartService.cartUpdated$.subscribe(() => this.loadCart());
  }

  loadCart(): void {
    this.cartService.getCart().subscribe({
      next: (data) => this.cart = data,
      error: (err) => console.error(err)
    });
  }

  toggleCart(): void {
    this.isOpen = !this.isOpen;
    if (this.isOpen) this.loadCart();
  }

  updateQuantity(productId: string, quantity: number): void {
    if (quantity < 1) return;
    const request: CartRequest = { productId, quantity };
    this.cartService.updateQuantity(request).subscribe({
      next: (data) => this.cart = data,
      error: (err) => console.error(err)
    });
  }

  removeItem(productId: string): void {
    this.cartService.removeFromCart(productId).subscribe({
      next: (data) => this.cart = data,
      error: (err) => console.error(err)
    });
  }

  clearCart(): void {
    this.cartService.clearCart().subscribe({
      next: () => this.cart = null,
      error: (err) => console.error(err)
    });
  }

  validateCart(): void {
    if (!this.shippingAddress || this.shippingAddress.trim() === '') {
      alert('Please enter a shipping address.');
      return;
    }
    const payload = { shippingAddress: this.shippingAddress, paymentMethod: 'PAY_ON_DELIVERY' };
    this.cartService.checkoutCart(payload).subscribe({
      next: () => {
        this.isOpen = false;
        this.shippingAddress = '';
        this.loadCart();
        alert('✅ Order placed!');
        this.router.navigate(['/profile/mes-commandes']);
      },
      error: (err) => {
        console.error('Checkout error', err);
        alert('❌ Error placing the order');
      }
    });
  }
}