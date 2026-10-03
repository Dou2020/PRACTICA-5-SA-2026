package gt.cunoc.sa.inventory.dto;

import gt.cunoc.sa.inventory.domain.Product;

public record ProductResponse(String sku, String name, Integer stock) {

    public static ProductResponse of(Product p) {
        return new ProductResponse(p.getSku(), p.getName(), p.getStock());
    }
}
