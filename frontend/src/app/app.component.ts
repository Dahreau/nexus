import { Component, ViewChild } from '@angular/core';
import { CartComponent } from './cart.component';
import { AuthService } from './services/auth.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  // styleUrls: ['./app.component.css']   // ← SUPPRIME CETTE LIGNE ou crée un fichier vide
})
export class AppComponent {
  @ViewChild('cart') cartComponent!: CartComponent;
  isLoggedIn$: Observable<boolean>;

  // Mobile nav collapse state — handled manually since only Bootstrap's CSS is loaded (no bootstrap.bundle.js), so data-bs-toggle has no effect.
  navOpen = false;

  constructor(private readonly auth: AuthService) {
    this.isLoggedIn$ = this.auth.isLoggedIn$;
  }

  toggleNav(): void {
    this.navOpen = !this.navOpen;
  }

  closeNav(): void {
    this.navOpen = false;
  }

  toggleCart(): void {
    this.cartComponent?.toggleCart();
  }
  loadCart(): void {
    this.cartComponent?.loadCart();
  }

  logout(): void {
    this.auth.logout();
  }

  isSeller(): boolean {
    return this.auth.isSeller();
  }

  getUserName(): string | null {
    return this.auth.getUserName();
  }
}