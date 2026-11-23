-- Foto opcional por producto. Puede ser una ruta del frontend (/img/productos/...)
-- o una URL https externa. Si es NULL, el frontend muestra el emoji del producto.

ALTER TABLE products ADD COLUMN image_url VARCHAR(255) NULL;

-- Fotos para el catálogo inicial (archivos servidos por el frontend en /img/productos).
UPDATE products SET image_url = '/img/productos/alimento-perro.webp'  WHERE name = 'Alimento perro adulto';
UPDATE products SET image_url = '/img/productos/arena-sanitaria.webp' WHERE name = 'Arena sanitaria premium';
UPDATE products SET image_url = '/img/productos/antiparasitario.webp' WHERE name = 'Antiparasitario externo';
UPDATE products SET image_url = '/img/productos/snack-dental.webp'    WHERE name = 'Snack dental';
UPDATE products SET image_url = '/img/productos/shampoo.webp'         WHERE name = 'Shampoo hipoalergénico';
UPDATE products SET image_url = '/img/productos/vitaminas.webp'       WHERE name = 'Vitaminas multiespecie';
