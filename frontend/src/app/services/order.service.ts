import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Order, UserStats, Page, CheckoutRequest } from '../models/order.model';

@Injectable({ providedIn: 'root' })
export class OrderService {
  private readonly baseUrl = 'http://localhost:8084/api/orders';

  constructor(private readonly http: HttpClient) {}

  // ==================== CLIENT ====================

  getMyOrders(params: {
    status?: string;
    start?: string;
    end?: string;
    keyword?: string;
    page?: number;
    size?: number;
  }): Observable<Page<Order>> {
    let httpParams = new HttpParams();
    if (params.status) httpParams = httpParams.set('status', params.status);
    if (params.start) httpParams = httpParams.set('start', params.start);
    if (params.end) httpParams = httpParams.set('end', params.end);
    if (params.keyword) httpParams = httpParams.set('keyword', params.keyword);
    if (params.page !== undefined) httpParams = httpParams.set('page', String(params.page));
    if (params.size !== undefined) httpParams = httpParams.set('size', String(params.size));
    return this.http.get<Page<Order>>(this.baseUrl, { params: httpParams });
  }

  getUserStats(): Observable<UserStats> {
    return this.http.get<UserStats>(`${this.baseUrl}/stats/user`);
  }

  cancelOrder(orderId: string): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/${orderId}/cancel`, {});
  }

  redoOrder(orderId: string): Observable<Order> {
    return this.http.post<Order>(`${this.baseUrl}/${orderId}/redo`, {});
  }

  deleteOrder(orderId: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${orderId}`);
  }

  // ==================== VENDEUR ====================

  getSellerOrders(params?: {
    status?: string;
    start?: string;
    end?: string;
    keyword?: string;
    page?: number;
    size?: number;
  }): Observable<Page<Order>> {
    let httpParams = new HttpParams();
    if (params?.status) httpParams = httpParams.set('status', params.status);
    if (params?.start) httpParams = httpParams.set('start', params.start);
    if (params?.end) httpParams = httpParams.set('end', params.end);
    if (params?.keyword) httpParams = httpParams.set('keyword', params.keyword);
    if (params?.page !== undefined) httpParams = httpParams.set('page', String(params.page));
    if (params?.size !== undefined) httpParams = httpParams.set('size', String(params.size));
    return this.http.get<Page<Order>>(`${this.baseUrl}/seller`, { params: httpParams });
  }

  getSellerStats(): Observable<any> {
    return this.http.get<any>(`${this.baseUrl}/stats/seller`);
  }

  updateOrderStatus(orderId: string, status: string): Observable<Order> {
    const params = new HttpParams().set('status', status);
    return this.http.put<Order>(`${this.baseUrl}/${orderId}/status`, null, { params });
  }

  // ==================== CHECKOUT ====================

  checkout(request: CheckoutRequest): Observable<Order> {
    return this.http.post<Order>(`http://localhost:8084/api/orders/checkout`, request);
  }
}