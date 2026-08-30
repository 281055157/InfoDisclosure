package com.example.disclosurereview.repository;

import com.example.disclosurereview.model.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 本地模拟产品库，从 classpath:products.json 加载。
 * 支持按产品代码、标准名称、别名精确查询。一期不实现向量检索。
 */
@Repository
public class ProductRepository {

    private static final Logger log = LoggerFactory.getLogger(ProductRepository.class);

    private final ObjectMapper objectMapper;

    private final List<Product> products = new ArrayList<>();
    private final Map<String, Product> byCode = new LinkedHashMap<>();
    private final Map<String, Product> byName = new LinkedHashMap<>();
    private final Map<String, Product> byAlias = new LinkedHashMap<>();

    public ProductRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        try {
            ClassPathResource resource = new ClassPathResource("products.json");
            if (!resource.exists()) {
                log.warn("products.json 不存在，产品库为空");
                return;
            }
            try (InputStream in = resource.getInputStream()) {
                List<Product> loaded = objectMapper.readValue(in, new TypeReference<>() {
                });
                for (Product p : loaded) {
                    add(p);
                }
                log.info("产品库加载完成，共 {} 条", products.size());
            }
        } catch (Exception e) {
            log.error("products.json 加载失败，产品库为空: {}", e.getMessage());
        }
    }

    /** 测试或运行期手动注入产品 */
    public void add(Product product) {
        if (product == null) {
            return;
        }
        products.add(product);
        if (StringUtils.hasText(product.productCode())) {
            byCode.put(product.productCode(), product);
        }
        if (product.safeShareCodes() != null) {
            for (String shareCode : product.safeShareCodes()) {
                if (StringUtils.hasText(shareCode)) {
                    byCode.put(shareCode.strip(), product);
                }
            }
        }
        if (product.safeCodeAliases() != null) {
            for (String codeAlias : product.safeCodeAliases()) {
                if (StringUtils.hasText(codeAlias)) {
                    byCode.put(codeAlias.strip(), product);
                }
            }
        }
        if (StringUtils.hasText(product.productName())) {
            byName.put(product.productName(), product);
        }
        if (product.safeAliases() != null) {
            for (String alias : product.safeAliases()) {
                if (StringUtils.hasText(alias)) {
                    byAlias.put(alias, product);
                }
            }
        }
        if (product.safeSeriesNames() != null) {
            for (String seriesName : product.safeSeriesNames()) {
                if (StringUtils.hasText(seriesName)) {
                    byAlias.put(seriesName.strip(), product);
                }
            }
        }
    }

    public Optional<Product> findByCode(String productCode) {
        if (!StringUtils.hasText(productCode)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byCode.get(productCode.strip()));
    }

    public Optional<Product> findByName(String productName) {
        if (!StringUtils.hasText(productName)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byName.get(productName.strip()));
    }

    public Optional<Product> findByAlias(String alias) {
        if (!StringUtils.hasText(alias)) {
            return Optional.empty();
        }
        return Optional.ofNullable(byAlias.get(alias.strip()));
    }

    /** 依次按代码、标准名称、别名精确匹配 */
    public Optional<Product> findAny(String value) {
        Optional<Product> byC = findByCode(value);
        if (byC.isPresent()) {
            return byC;
        }
        Optional<Product> byN = findByName(value);
        if (byN.isPresent()) {
            return byN;
        }
        return findByAlias(value);
    }

    public List<Product> findAll() {
        return List.copyOf(products);
    }

    public List<String> allProductCodes() {
        return byCode.keySet().stream().toList();
    }

    public List<String> allProductNamesAndAliases() {
        List<String> names = new ArrayList<>();
        names.addAll(byName.keySet());
        names.addAll(byAlias.keySet());
        return names.stream().distinct().toList();
    }

    public int size() {
        return products.size();
    }
}
