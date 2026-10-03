-- Database per service: una base y un usuario por microservicio.
-- Viven en el mismo contenedor solo para no levantar cuatro postgres; ningún
-- servicio conoce las credenciales de otro, así que no puede leer ni escribir
-- fuera de su base. Por eso no hay transacción ACID que cruce servicios y hace
-- falta la saga.
--
-- Las contraseñas son públicas a propósito (igual que en PRACTICA_1/2): es una
-- POC local, el trade-off que se estudia no es la gestión de secretos.

CREATE USER order_svc     WITH PASSWORD 'order_svc';
CREATE USER payment_svc   WITH PASSWORD 'payment_svc';
CREATE USER inventory_svc WITH PASSWORD 'inventory_svc';
CREATE USER shipping_svc  WITH PASSWORD 'shipping_svc';

CREATE DATABASE order_db     OWNER order_svc;
CREATE DATABASE payment_db   OWNER payment_svc;
CREATE DATABASE inventory_db OWNER inventory_svc;
CREATE DATABASE shipping_db  OWNER shipping_svc;