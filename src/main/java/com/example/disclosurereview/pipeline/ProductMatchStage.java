package com.example.disclosurereview.pipeline;

import com.example.disclosurereview.model.ExtractSource;
import com.example.disclosurereview.model.Product;
import com.example.disclosurereview.model.ReviewStage;
import com.example.disclosurereview.persistence.entity.ExtractedFieldEntity;
import com.example.disclosurereview.persistence.entity.ReviewTaskEntity;
import com.example.disclosurereview.repository.ProductRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProductMatchStage implements ReviewStageHandler {

    private final ReviewStageSupport support;
    private final ReviewTaskContextStore contextStore;
    private final ProductRepository productRepository;

    public ProductMatchStage(ReviewStageSupport support,
                             ReviewTaskContextStore contextStore,
                             ProductRepository productRepository) {
        this.support = support;
        this.contextStore = contextStore;
        this.productRepository = productRepository;
    }

    @Override
    public ReviewStage stage() {
        return ReviewStage.PRODUCT_MATCHING;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public StageResult handle(ReviewPipelineContext context) {
        ReviewTaskEntity task = support.getTask(context.getTaskId());
        support.updateStage(task.getId(), ReviewStage.PRODUCT_MATCHING);
        Product matched = StringUtils.hasText(task.getDeclaredProductCode())
                ? productRepository.findAny(task.getDeclaredProductCode()).orElse(null)
                : null;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("matched", matched != null);
        data.put("declaredProductCode", task.getDeclaredProductCode());
        data.put("product", matched);
        data.put("knownProductCodes", matched == null ? productRepository.allProductCodes() : null);
        contextStore.put(task.getId(), "productMatch", data);

        if (matched != null) {
            var fields = new ArrayList<ExtractedFieldEntity>();
            support.addExtractedField(fields, task, "PRODUCT_MASTER_CODE", matched.productCode(),
                    matched.productCode(), null, null, null, matched.productName(),
                    ExtractSource.PRODUCT_MASTER, 1.0, true);
            support.addExtractedField(fields, task, "PRODUCT_MASTER_NAME", matched.productName(),
                    matched.productName(), null, null, null, matched.productName(),
                    ExtractSource.PRODUCT_MASTER, 1.0, true);
            support.saveFields(fields);
        }
        support.recordAudit(task, "PRODUCT_MATCHED", matched == null
                ? "产品库未匹配到声明产品"
                : "产品库匹配完成", null, matched == null ? null : matched.productCode());
        return StageResult.completed(stage(), "Product matching completed");
    }
}
