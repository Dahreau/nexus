import { Component, OnInit } from '@angular/core';
import { ProductService } from './services/product.service';
import { MediaService } from './services/media.service';
import { AuthService } from './services/auth.service';
import { OrderService } from './services/order.service';
import { firstValueFrom } from 'rxjs/internal/firstValueFrom';

@Component({
  selector: 'app-seller-dashboard',
  templateUrl: './seller-dashboard.component.html',
})
export class SellerDashboardComponent implements OnInit {
  // Produits
  name = '';
  price = 0;
  quantity = 0;
  description = '';
  myProducts: any[] = [];
  allProducts: any[] = [];
  editingProductId: string | null = null;
  currentUserId: string | null = null;

  // Images
  selectedFiles: File[] = [];
  imagePreviews: string[] = [];
  existingImages: any[] = [];

  // Commandes
  orders: any[] = [];
  stats: any = null;

  constructor(
    private readonly productService: ProductService,
    private readonly media: MediaService,
    private readonly auth: AuthService,
    private readonly orderService: OrderService
  ) {}

  ngOnInit(): void {
    this.loadMyProducts();
    this.loadSellerOrders();
    this.loadSellerStats();
  }

  // ==================== PRODUITS ====================

  loadMyProducts() {
    const userId = this.auth.getUserId();
    this.currentUserId = userId;

    this.productService.listAll().subscribe((data: any[]) => {
      this.allProducts = data;
      if (!userId) {
        this.myProducts = [];
        return;
      }
      this.myProducts = data.filter((p) => p.userId === userId);
      for (const p of this.myProducts) {
        const pid = p.id || p._id;
        this.media.byProduct(pid).subscribe({
          next: (meds) => (p.images = meds),
          error: () => (p.images = [])
        });
      }
    });
  }

  saveProduct(evt: Event) {
    evt.preventDefault();

    if (!this.price || this.price <= 0) {
      alert('Price must be greater than 0.');
      return;
    }
    if (this.quantity == null || this.quantity < 0) {
      alert('Quantity cannot be negative.');
      return;
    }

    const userId = this.auth.getUserId();
    const body = {
      name: this.name,
      price: this.price,
      quantity: this.quantity,
      description: this.description,
      userId: userId
    };

    const request = this.editingProductId
      ? this.productService.update(this.editingProductId, body)
      : this.productService.create(body);

    request.subscribe({
      next: (product: any) => {
        if (this.selectedFiles.length > 0) {
          const uploads = this.selectedFiles.map((file) => {
            const formData = new FormData();
            formData.append('file', file);
            formData.append('productId', product.id);
            return firstValueFrom(this.media.upload(formData));
          });
          Promise.all(uploads)
            .then(() => {
              alert(this.editingProductId ? 'Product updated with images' : 'Product created with images');
              this.cancelEdit();
              this.loadMyProducts();
            })
            .catch(() => {
              alert('Product saved but image upload failed');
              this.cancelEdit();
              this.loadMyProducts();
            });
        } else {
          alert(this.editingProductId ? 'Updated' : 'Created');
          this.cancelEdit();
          this.loadMyProducts();
        }
      },
      error: (err) => {
        const msg = err?.error?.error;
        alert(msg || (this.editingProductId ? 'Update failed' : 'Creation failed'));
      }
    });
  }

  editProduct(p: any) {
    this.editingProductId = p.id || p._id;
    this.name = p.name || '';
    this.price = Number(p.price || 0);
    this.quantity = Number(p.quantity || 0);
    this.description = p.description || '';
    this.existingImages = p.images || [];
    this.imagePreviews = [];
    this.selectedFiles = [];
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  cancelEdit() {
    this.editingProductId = null;
    this.name = '';
    this.price = 0;
    this.quantity = 0;
    this.description = '';
    this.existingImages = [];
    this.imagePreviews = [];
    this.selectedFiles = [];
  }

  confirmDelete(p: any) {
    if (!confirm('Delete "' + p.name + '"?')) return;
    const id = p.id || p._id;
    this.productService.delete(id).subscribe({
      next: () => {
        alert('Deleted');
        this.loadMyProducts();
      },
      error: () => alert('Delete failed')
    });
  }

  // ==================== IMAGES ====================

  onFileSelected(event: any) {
    const files = event.target.files;
    if (files) {
      for (const file of files) {
        this.selectedFiles.push(file);
        const reader = new FileReader();
        reader.onload = (e: any) => {
          this.imagePreviews.push(e.target.result);
        };
        reader.readAsDataURL(file);
      }
    }
  }

  removeTempImage(index: number) {
    this.selectedFiles.splice(index, 1);
    this.imagePreviews.splice(index, 1);
  }

  removeExistingImage(mediaId: string) {
    this.existingImages = this.existingImages.filter((m) => m.id !== mediaId);
    // Note : pour réellement supprimer du serveur, il faudrait appeler une API
  }

  // ==================== COMMANDES ====================

  // Only this seller's items in the order (an order can span multiple sellers).
  getSellerItems(order: any): any[] {
    if (!order?.items) return [];
    return order.items.filter((i: any) => i.sellerId === this.currentUserId);
  }

  loadSellerOrders() {
    this.orderService.getSellerOrders().subscribe({
      next: (data: any) => {
        this.orders = data.content || [];
      },
      error: (err) => console.error('Error loading orders:', err)
    });
  }

  loadSellerStats() {
    this.orderService.getSellerStats().subscribe({
      next: (data) => {
        this.stats = data;
      },
      error: (err) => console.error('Error loading stats:', err)
    });
  }
  // ==================== STATUTS ====================

// Info pour l'affichage
getStatusInfo(status: string) {
    const map: any = {
      'PENDING':   { label: 'Pending', icon: '🟡', color: '#f59e0b', next: 'PAID' },
      'PAID':      { label: 'Paid', icon: '🟢', color: '#10b981', next: 'SHIPPED' },
      'SHIPPED':   { label: 'Shipped', icon: '📦', color: '#3b82f6', next: 'DELIVERED' },
      'DELIVERED': { label: 'Delivered', icon: '✅', color: '#8b5cf6', next: null },
      'CANCELLED': { label: 'Cancelled', icon: '❌', color: '#ef4444', next: null }
    };
    return map[status] || { label: status, icon: '❓', color: '#6b7280', next: null };
  }

  // Retourne les statuts possibles pour le menu déroulant
  getAvailableTransitions(currentStatus: string) {
    const info = this.getStatusInfo(currentStatus);
    // Si le statut est terminé (DELIVERED ou CANCELLED), on ne propose que lui-même
    if (currentStatus === 'DELIVERED' || currentStatus === 'CANCELLED') {
      return [currentStatus];
    }
    // Sinon on propose : le statut actuel + le suivant
    const next = info.next;
    return next ? [currentStatus, next] : [currentStatus];
  }

  // Passer au statut suivant (bouton "Suivant")
  nextStatus(order: any) {
    const info = this.getStatusInfo(order.status);
    if (info.next) {
      this.updateOrderStatus(order.id, info.next);
    } else {
      alert('This order is already in its final state.');
    }
  }

  updateOrderStatus(orderId: string, newStatus: string) {
    this.orderService.updateOrderStatus(orderId, newStatus).subscribe({
      next: (updated) => {
        const index = this.orders.findIndex((o) => o.id === updated.id);
        if (index !== -1) this.orders[index] = updated;
        this.loadSellerStats();
      },
      error: (err) => {
        console.error('Status update error:', err);
        alert('Error changing status');
      }
    });
  }
}