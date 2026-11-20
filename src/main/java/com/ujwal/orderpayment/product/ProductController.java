package com.ujwal.orderpayment.product;

import com.ujwal.orderpayment.product.ProductDtos.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

/** Public read endpoints. */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public Page<ProductResponse> search(@RequestParam(defaultValue = "") String q,
                                        @PageableDefault(size = 20, sort = "id") Pageable pageable) {
        return service.search(q, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
