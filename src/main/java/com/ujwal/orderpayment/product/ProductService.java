package com.ujwal.orderpayment.product;

import com.ujwal.orderpayment.common.NotFoundException;
import com.ujwal.orderpayment.product.ProductDtos.ProductRequest;
import com.ujwal.orderpayment.product.ProductDtos.ProductResponse;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository repo;

    public ProductService(ProductRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(String q, Pageable pageable) {
        return repo.findByNameContainingIgnoreCase(q, pageable).map(ProductResponse::from);
    }

    @Cacheable(cacheNames = "products", key = "#id.toString()")
    @Transactional(readOnly = true)
    public ProductResponse get(Long id) {
        return repo.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
    }

    @Transactional
    public ProductResponse create(ProductRequest req) {
        Product p = new Product();
        apply(p, req);
        return ProductResponse.from(repo.save(p));
    }

    @CacheEvict(cacheNames = "products", key = "#id.toString()")
    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product p = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("Product " + id + " not found"));
        apply(p, req);
        return ProductResponse.from(p);
    }

    @CacheEvict(cacheNames = "products", key = "#id.toString()")
    @Transactional
    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("Product " + id + " not found");
        }
        repo.deleteById(id);
    }

    private static void apply(Product p, ProductRequest req) {
        p.setName(req.name().trim());
        p.setDescription(req.description());
        p.setPrice(req.price());
        p.setStock(req.stock());
    }
}
