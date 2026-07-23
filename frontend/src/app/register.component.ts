import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-register',
  template: `
    <form class="form" (submit)="register($event)">
      <p class="heading">Register</p>
      <input class="input" placeholder="Name" type="text" name="name" [(ngModel)]="name">
      <input class="input" placeholder="Email" type="text" name="email" [(ngModel)]="email">
      <input class="input" placeholder="Password" type="password" name="password" [(ngModel)]="password">
      <select class="input" [(ngModel)]="role" name="role">
        <option value="CLIENT">Client</option>
        <option value="SELLER">Seller</option>
      </select>
      <button class="btn" type="submit">Register</button>
      <div *ngIf="error" style="color:red; text-align:center; margin-top: 0.5rem;">{{error}}</div>
    </form>
  `
})
export class RegisterComponent {
  name=''; email=''; password=''; role='CLIENT'; error='';
  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  register(evt: Event) {
    evt.preventDefault();
    this.error='';
    this.auth.register({ name: this.name, email: this.email, password: this.password, role: this.role }).subscribe({
      next: res => { this.auth.setToken(res.token); this.router.navigate(['/']); },
      error: err => { this.error = err.error?.error || 'Register failed'; }
    });
  }
}
