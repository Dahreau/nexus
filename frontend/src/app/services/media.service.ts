import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs'; // ← AJOUTER CETTE LIGNE

@Injectable({ providedIn: 'root' })
export class MediaService {
  constructor(private readonly http: HttpClient) {}

  upload(formData: FormData): Observable<any> {
    return this.http.post('http://localhost:8083/api/media/upload', formData);
  }

  byProduct(productId: string): Observable<any[]> {
    return this.http.get<any[]>(`http://localhost:8083/api/media/product/${productId}`);
  }
}