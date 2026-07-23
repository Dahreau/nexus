#!/usr/bin/env python3
"""
Remplit la base avec des données de test, en passant PAR LES VRAIES API
(pas d'insertion directe en Mongo) pour respecter la logique métier :
hash du mot de passe, JWT, dénormalisation (sellerName/sellerId/priceAtPurchase),
vérification de stock, etc.

Prérequis : `docker compose up` doit tourner (les 5 services + Mongo).
Usage     : pip install requests && python3 scripts/seed-data.py
"""

import requests

USER = "http://localhost:8081/api/auth"
PRODUCT = "http://localhost:8082/api/products"
CART = "http://localhost:8085/api/carts"
ORDER = "http://localhost:8084/api/orders"

SELLERS = [
    {"name": "Alice Vendeuse", "email": "alice.seller@test.com", "password": "password123", "role": "SELLER"},
    {"name": "Bob Vendeur", "email": "bob.seller@test.com", "password": "password123", "role": "SELLER"},
]
CLIENTS = [
    {"name": "Charlie Client", "email": "charlie.client@test.com", "password": "password123", "role": "CLIENT"},
    {"name": "Dana Cliente", "email": "dana.client@test.com", "password": "password123", "role": "CLIENT"},
]

PRODUCTS_PER_SELLER = [
    {"name": "Clavier mécanique", "description": "Switches rouges, rétroéclairé", "price": 79.99, "quantity": 15},
    {"name": "Souris sans fil", "description": "1600 DPI, batterie 6 mois", "price": 24.5, "quantity": 40},
    {"name": "Casque audio", "description": "Réduction de bruit active", "price": 129.0, "quantity": 8},
]


def register_or_login(user):
    r = requests.post(f"{USER}/register", json=user, timeout=5)
    if r.status_code == 200:
        print(f"  créé : {user['email']}")
        return r.json()
    r = requests.post(f"{USER}/login", json={"email": user["email"], "password": user["password"]}, timeout=5)
    r.raise_for_status()
    print(f"  déjà existant, login : {user['email']}")
    return r.json()


def auth_header(token):
    return {"Authorization": f"Bearer {token}"}


def main():
    print("1. Comptes vendeurs")
    sellers = [register_or_login(u) for u in SELLERS]

    print("2. Comptes clients")
    clients = [register_or_login(u) for u in CLIENTS]

    print("3. Produits (répartis entre les vendeurs)")
    created_products = []
    for i, seller in enumerate(sellers):
        headers = auth_header(seller["token"])
        for p in PRODUCTS_PER_SELLER:
            payload = dict(p)
            payload["name"] = f"{p['name']} ({SELLERS[i]['name'].split()[0]})"
            r = requests.post(PRODUCT, json=payload, headers=headers, timeout=5)
            if r.status_code == 200:
                prod = r.json()
                created_products.append(prod)
                print(f"  {prod.get('name')} — {prod.get('price')}€ (vendeur {SELLERS[i]['name']})")
            else:
                print(f"  échec création produit : {r.status_code} {r.text}")

    if not created_products:
        print("Aucun produit créé, arrêt (vérifie que les services tournent).")
        return

    print("4. Ajout au panier (le 1er client achète des produits du 2e vendeur, pas les siens)")
    client_headers = auth_header(clients[0]["token"])
    # produits du 2e vendeur seulement, pour être sûr qu'un client n'achète jamais son propre produit
    products_to_buy = [p for p in created_products if p.get("userId") != clients[0]["userId"]][:2]
    for p in products_to_buy:
        r = requests.post(CART, json={"productId": p["id"], "quantity": 2}, headers=client_headers, timeout=5)
        if r.status_code == 200:
            print(f"  ajouté au panier : {p['name']} x2")
        else:
            print(f"  échec ajout panier : {r.status_code} {r.text}")

    print("5. Checkout (crée une commande PENDING)")
    r = requests.post(
        f"{ORDER}/checkout",
        json={"shippingAddress": "12 rue de Test, 75000 Paris", "paymentMethod": "CARD"},
        headers=client_headers,
        timeout=5,
    )
    if r.status_code == 200:
        order = r.json()
        print(f"  commande créée : {order.get('id')} — {order.get('totalAmount')}€")
    else:
        print(f"  échec checkout : {r.status_code} {r.text}")

    print("\nTerminé. Comptes de test (mot de passe pour tous : password123) :")
    for u in SELLERS + CLIENTS:
        print(f"  {u['role']:<6} {u['email']}")


if __name__ == "__main__":
    main()
