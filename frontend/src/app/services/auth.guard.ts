import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './auth.service';

// Guard pour toute personne connectée (client ou vendeur)
export const authGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.getToken()) return true;
  router.navigate(['/login']);
  return false;
};

// Guard pour les vendeurs uniquement
export const sellerGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.getToken()) {
    router.navigate(['/login']);
    return false;
  }
  if (auth.isSeller()) return true;
  router.navigate(['/']);
  return false;
};

// "My orders" is open to any authenticated user. Sellers can buy too (cart is available to them), so they need access to their own order history as well.
export const clientGuard = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.getToken()) {
    router.navigate(['/login']);
    return false;
  }
  return true;
};