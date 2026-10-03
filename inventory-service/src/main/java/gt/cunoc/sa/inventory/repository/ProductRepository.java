package gt.cunoc.sa.inventory.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import gt.cunoc.sa.inventory.domain.Product;

public interface ProductRepository extends JpaRepository<Product, String> {

    List<Product> findAllByOrderBySku();

    /**
     * Descuenta solo si alcanza, en una sola sentencia. Devuelve las filas
     * afectadas: 0 = no había stock suficiente.
     *
     * Leer el stock, comparar y guardar en Java sería una carrera: con la
     * ráfaga de órdenes concurrentes de la demo, dos requests leerían stock 1
     * y los dos descontarían. Acá la condición y el descuento son atómicos.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock - :qty where p.sku = :sku and p.stock >= :qty")
    int decrementStock(@Param("sku") String sku, @Param("qty") int qty);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Product p set p.stock = p.stock + :qty where p.sku = :sku")
    int incrementStock(@Param("sku") String sku, @Param("qty") int qty);
}
