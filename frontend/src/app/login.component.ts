import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-login',
  template: `
    <form class="form" (submit)="login($event)">
      <p class="heading">Login</p>
      <input class="input" placeholder="Email" type="text" name="email" [(ngModel)]="email">
      <input class="input" placeholder="Password" type="password" name="password" [(ngModel)]="password">
      <button class="btn" type="submit">Submit</button>
      <div *ngIf="error" style="color:red; text-align:center; margin-top: 0.5rem;">{{error}}</div>
    </form>
  `
})
export class LoginComponent {
  email = '';
  password = '';
  error = '';
  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  login(evt: Event) {
    evt.preventDefault();
    this.error = '';
    this.auth.login({ email: this.email, password: this.password }).subscribe({
      next: res => { this.auth.setToken(res.token); this.router.navigate(['/']); },
      error: err => { this.error = err.error?.error || 'Login failed'; }
    });
  }
}
