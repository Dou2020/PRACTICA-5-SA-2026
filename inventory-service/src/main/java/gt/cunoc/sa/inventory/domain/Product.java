package gt.cunoc.sa.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Sin setters de stock: el stock solo se modifica con los UPDATE atómicos de
 * ProductRepository, nunca leyendo-modificando-guardando la entidad.
 */
@Entity
@Table(name = "products")
public class Product {

    @Id
    private String sku;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer stock;

    protected Product() {
    }

    public String getSku() { return sku; }
    public String getName() { return name; }
    public Integer getStock() { return stock; }
}
