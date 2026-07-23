import { Component, OnInit } from '@angular/core';
import { OrderService } from './services/order.service';
import { Order, UserStats, Page } from './models/order.model';

@Component({
  selector: 'app-client-dashboard',
  templateUrl: './client-dashboard.component.html',
})
export class ClientDashboardComponent implements OnInit {
  orders: Order[] = [];
  userStats: UserStats | null = null;
  currentPage = 0;
  pageSize = 10;
  totalPages = 0;
  filterStatus = '';
  filterKeyword = '';
  loading = false;
  error = '';

  constructor(private readonly orderService: OrderService) {}

  ngOnInit(): void {
    this.loadDashboard();
  }

  loadDashboard(): void {
    this.loadStats();
    this.loadOrders();
  }

  loadStats(): void {
    this.orderService.getUserStats().subscribe({
      next: (data) => {
        this.userStats = data;
        console.log('📊 Client stats:', data);
      },
      error: (err) => console.error('Stats error:', err)
    });
  }

  loadOrders(): void {
    this.loading = true;
    this.error = '';
    this.orderService.getMyOrders({
      status: this.filterStatus || undefined,
      keyword: this.filterKeyword || undefined,
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (page: Page<Order>) => {
        this.orders = page.content;
        console.log('📦 Orders received:', this.orders);
        this.totalPages = page.totalPages;
        this.currentPage = page.number;
        this.loading = false;
      },
      error: () => {
        this.error = 'Unable to load orders.';
        this.loading = false;
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadOrders();
  }


  cancelOrder(order: Order): void {
    if (!confirm(`Cancel order #${order.id}?`)) return;
    this.orderService.cancelOrder(order.id).subscribe({
      next: () => {
        alert('Order cancelled.');
        this.loadOrders();
        this.loadStats();
      },
      error: () => alert('Error cancelling the order.')
    });
  }

  redoOrder(order: Order): void {
    if (!confirm(`Recreate an order based on #${order.id}?`)) return;
    this.orderService.redoOrder(order.id).subscribe({
      next: () => {
        alert('New order created!');
        this.loadOrders();
        this.loadStats();
      },
      error: () => alert('Error recreating the order.')
    });
  }

  deleteOrder(order: Order): void {
    if (!confirm(`Permanently delete order #${order.id} from your history?`)) return;
    this.orderService.deleteOrder(order.id).subscribe({
      next: () => {
        alert('Order deleted.');
        this.loadOrders();
        this.loadStats();
      },
      error: () => alert('Error deleting the order.')
    });
  }

  // ==================== AFFICHAGE DES STATUTS ====================
getStatusLabel(status: string): string {
  const map: { [key: string]: string } = {
    'PENDING': '🟡 Pending',
    'PAID': '🟢 Paid',
    'SHIPPED': '📦 Shipped',
    'DELIVERED': '✅ Delivered',
    'CANCELLED': '❌ Cancelled'
  };
  return map[status] || '❓ Unknown';
}

getStatusColor(status: string): string {
  const map: { [key: string]: string } = {
    'PENDING': '#f59e0b',
    'PAID': '#10b981',
    'SHIPPED': '#3b82f6',
    'DELIVERED': '#8b5cf6',
    'CANCELLED': '#ef4444'
  };
  return map[status] || '#6b7280';
}
}