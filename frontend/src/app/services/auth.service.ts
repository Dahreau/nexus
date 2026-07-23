import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly base = 'http://localhost:8081/api/auth';
  private readonly loggedIn = new BehaviorSubject<boolean>(!!this.getToken());
  public isLoggedIn$: Observable<boolean> = this.loggedIn.asObservable();

  constructor(private readonly http: HttpClient, private readonly router: Router) {}

  register(body: any) { return this.http.post<any>(`${this.base}/register`, body); }
  login(body: any) { return this.http.post<any>(`${this.base}/login`, body); }

  setToken(token: string) {
      localStorage.setItem('token', token);
      this.loggedIn.next(true);
  }
  getToken(): string | null { return localStorage.getItem('token'); }
  logout() {
      localStorage.removeItem('token');
      this.loggedIn.next(false);
      this.router.navigate(['/login']);
  }

  isSeller(): boolean {
    const token = this.getToken();
    if (!token) return false;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload?.role === 'SELLER';
    } catch { return false; }
  }

  getUserId(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      console.log('Token payload:', payload);
      return payload.userId || payload.sub || null;
    } catch(e) {
      console.error('Token error:', e);
      return null;
    }
  }

  getUserName(): string | null {
    const token = this.getToken();
    if (!token) return null;
    try {
      const payload = JSON.parse(atob(token.split('.')[1]));
      return payload?.name || null;
    } catch { return null; }
  }
}
