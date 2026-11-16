-- Categorías y catálogo inicial (los mismos 6 productos de la etapa anterior).
-- Los usuarios de demostración se crean desde Java (DataInitializer) para cifrar la contraseña con BCrypt.

INSERT INTO categories (name) VALUES ('Alimentos'), ('Higiene'), ('Salud');

INSERT INTO products (name, category_id, price, stock, offer, description, emoji, active) VALUES
('Alimento perro adulto',   (SELECT id FROM categories WHERE name = 'Alimentos'), 18990, 16, TRUE,  'Nutrición completa para perros adultos.',        '🐕', TRUE),
('Arena sanitaria premium', (SELECT id FROM categories WHERE name = 'Higiene'),    7990, 22, FALSE, 'Control de olores y alta absorción.',            '🐈', TRUE),
('Antiparasitario externo', (SELECT id FROM categories WHERE name = 'Salud'),     12490,  9, TRUE,  'Protección mensual contra pulgas y garrapatas.', '🩺', TRUE),
('Snack dental',            (SELECT id FROM categories WHERE name = 'Alimentos'),  4290, 30, FALSE, 'Premio saludable para su cuidado oral.',         '🦴', TRUE),
('Shampoo hipoalergénico',  (SELECT id FROM categories WHERE name = 'Higiene'),    8990, 12, FALSE, 'Limpieza suave para pieles sensibles.',          '🫧', TRUE),
('Vitaminas multiespecie',  (SELECT id FROM categories WHERE name = 'Salud'),     10990, 14, TRUE,  'Apoyo diario para perros y gatos.',              '💊', TRUE);
