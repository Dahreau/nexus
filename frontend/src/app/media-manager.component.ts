import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { ProductService } from './services/product.service';
import { AuthService } from './services/auth.service';

@Component({
  selector: 'app-media-manager',
  template: `
    <div class="card mb-3">
      <div class="card-body">
        <h5 class="card-title">Upload Media</h5>
        <form (submit)="upload($event)">
          <div class="mb-3">
            <label class="form-label">Product</label>
            <select class="form-select" [(ngModel)]="productId" name="productId">
              <option value="">-- choose product --</option>
              <option *ngFor="let p of products" [value]="p.id || p._id">{{p.name}} ({{p.id || p._id}})</option>
            </select>
          </div>
          <div class="mb-3">
            <input class="form-control" type="file" (change)="onFile($event)" />
          </div>
          <button class="btn btn-success" type="submit">Upload</button>
          <div *ngIf="error" class="text-danger mt-2">{{error}}</div>
        </form>
      </div>
    </div>
  `
})
export class MediaManagerComponent implements OnInit {
  @Output() uploaded = new EventEmitter<void>();
  productId = '';
  file: File | null = null;
  error = '';
  products: any[] = [];
  constructor(private readonly http: HttpClient, private readonly productService: ProductService, private readonly auth: AuthService) {}

  ngOnInit(): void {
    // load products so the user can pick one by name (and we show the id in the option)
    this.productService.listAll().subscribe({ 
      next: (data: any[]) => {
        const userId = this.auth.getUserId();
        this.products = data.filter(p => p.userId === userId);
      }, 
      error: () => this.products = [] 
    });
  }

  onFile(evt: any) {
    this.file = evt.target.files[0];
  }

  upload(evt: Event) {
    evt.preventDefault();
    this.error = '';
    if (!this.file) { this.error = 'Choose a file'; return; }
    if (this.file.size > 2 * 1024 * 1024) { this.error = 'File too large (max 2MB)'; return; }
    const allowed = ['image/png','image/jpeg','image/gif'];
    if (!allowed.includes(this.file.type)) { this.error = 'Unsupported file type'; return; }
    const token = localStorage.getItem('token');
    if (!token) { this.error = 'Login first'; return; }
    const form = new FormData();
    form.append('file', this.file);
  form.append('productId', this.productId);
    const headers = new HttpHeaders().set('Authorization', 'Bearer ' + token);
    this.http.post('http://localhost:8083/api/media/upload', form, { headers }).subscribe({ next: () => { alert('Uploaded'); this.uploaded.emit(); }, error: (e) => this.error = 'Upload failed' });
  }
}
