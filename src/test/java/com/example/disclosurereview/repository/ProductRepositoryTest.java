package com.example.disclosurereview.repository;

import com.example.disclosurereview.model.Product;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ProductRepositoryTest {

    private ProductRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ProductRepository(new ObjectMapper());
        repository.add(new Product("SGN22555",
                "示例理财丙宁欣天天鎏金现金管理类理财产品3号",
                List.of("宁欣天天鎏金3号", "宁欣天天鎏金现金管理3号"),
                "理财产品"));
        repository.add(new Product("ZYBSG0056D",
                "示例理财乙琮简宝石59号理财产品D份额",
                List.of("琮简宝石59号D份额"),
                "示例理财管理机构乙",
                "示例理财管理机构乙",
                "ZYBSG0056",
                List.of("ZYBSG0056A", "ZYBSG0056B", "ZYBSG0056D"),
                List.of(),
                List.of("示例理财乙琮简宝石59号"),
                List.of("示例机构", "示例机构股份有限公司"),
                "理财产品"));
    }

    @Test
    void findsByCode() {
        Optional<Product> p = repository.findByCode("SGN22555");
        assertThat(p).isPresent();
        assertThat(p.get().productName()).contains("宁欣天天鎏金");
    }

    @Test
    void findsByStandardName() {
        Optional<Product> p = repository.findByName("示例理财丙宁欣天天鎏金现金管理类理财产品3号");
        assertThat(p).isPresent();
        assertThat(p.get().productCode()).isEqualTo("SGN22555");
    }

    @Test
    void findsByAlias() {
        Optional<Product> p = repository.findByAlias("宁欣天天鎏金3号");
        assertThat(p).isPresent();
        assertThat(p.get().productCode()).isEqualTo("SGN22555");
    }

    @Test
    void findAnyMatchesInOrder() {
        assertThat(repository.findAny("SGN22555")).isPresent();
        assertThat(repository.findAny("示例理财丙宁欣天天鎏金现金管理类理财产品3号")).isPresent();
        assertThat(repository.findAny("宁欣天天鎏金现金管理3号")).isPresent();
        assertThat(repository.findAny("ZYBSG0056A")).isPresent();
        assertThat(repository.findAny("示例理财乙琮简宝石59号")).isPresent();
        assertThat(repository.findAny("不存在的产品")).isEmpty();
    }

    @Test
    void returnsEmptyForUnknownOrBlank() {
        assertThat(repository.findByCode("XXXXX")).isEmpty();
        assertThat(repository.findByCode(null)).isEmpty();
        assertThat(repository.findByCode("  ")).isEmpty();
    }
}
