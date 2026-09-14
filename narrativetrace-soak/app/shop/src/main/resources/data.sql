-- Same SKUs and prices as the ecommerce example's InMemoryProductCatalogService, so a reader
-- comparing the two recognizes the catalog. Stock (InMemoryInventoryService, unchanged from the
-- example) starts at 150/500/75 but ShopConfig restocks every SKU to 1,000,000 at startup — the
-- example's demo-sized seed is exhausted within seconds of sustained load; see ShopConfig's
-- RESTOCK_QUANTITY comment for the incident that found this.
MERGE INTO catalog (product_id, name, price) VALUES
  ('SKU-MECHANICAL-KB', 'Mechanical Keyboard', 89.99),
  ('SKU-MOUSE-PAD', 'Mouse Pad', 24.99),
  ('SKU-USB-HUB', 'USB Hub', 39.99);
