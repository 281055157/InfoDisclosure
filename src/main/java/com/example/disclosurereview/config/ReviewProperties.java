package com.example.disclosurereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "review")
public class ReviewProperties {

    private final Excel excel = new Excel();
    private final Institution institution = new Institution();
    private final Storage storage = new Storage();
    private final Executor executor = new Executor();
    private final Retry retry = new Retry();
    private final Rabbitmq rabbitmq = new Rabbitmq();
    private final DocumentCategoryPolicy documentCategory = new DocumentCategoryPolicy();
    private final Sync sync = new Sync();

    /** 允许的候选文件类型列表，供大模型优先选择 */
    private List<String> allowedDocumentTypes = new ArrayList<>();

    /** 文件类型英文枚举到中文别名的可配置映射。 */
    private Map<String, List<String>> documentTypeAliases = new LinkedHashMap<>();

    /** 审核版本，用于幂等键；规则、Prompt 或模型策略变化时可调整。 */
    private String reviewVersion = "v1";

    public Excel getExcel() {
        return excel;
    }

    public Institution getInstitution() {
        return institution;
    }

    public Storage getStorage() {
        return storage;
    }

    public Executor getExecutor() {
        return executor;
    }

    public Retry getRetry() {
        return retry;
    }

    public Rabbitmq getRabbitmq() {
        return rabbitmq;
    }

    public DocumentCategoryPolicy getDocumentCategory() {
        return documentCategory;
    }

    public Sync getSync() {
        return sync;
    }

    public List<String> getAllowedDocumentTypes() {
        return allowedDocumentTypes;
    }

    public void setAllowedDocumentTypes(List<String> allowedDocumentTypes) {
        this.allowedDocumentTypes = allowedDocumentTypes;
    }

    public Map<String, List<String>> getDocumentTypeAliases() {
        return documentTypeAliases;
    }

    public void setDocumentTypeAliases(Map<String, List<String>> documentTypeAliases) {
        this.documentTypeAliases = documentTypeAliases;
    }

    public String getReviewVersion() {
        return reviewVersion;
    }

    public void setReviewVersion(String reviewVersion) {
        this.reviewVersion = reviewVersion;
    }

    public static class Excel {
        /** 读取 B9 时使用的工作表名称；为空则读取第一个工作表 */
        private String b9SheetName;

        public String getB9SheetName() {
            return b9SheetName;
        }

        public void setB9SheetName(String b9SheetName) {
            this.b9SheetName = b9SheetName;
        }
    }

    public static class Institution {
        /** 目标机构常见名称，用于代销协议代理销售方识别。 */
        private List<String> targetBankNames = new ArrayList<>(List.of("示例机构", "示例机构股份有限公司"));

        public List<String> getTargetBankNames() {
            return targetBankNames;
        }

        public void setTargetBankNames(List<String> targetBankNames) {
            this.targetBankNames = targetBankNames;
        }
    }

    public static class Storage {
        /** 本地文件持久化根目录。 */
        private String rootDirectory = "./data/review-files";

        public String getRootDirectory() {
            return rootDirectory;
        }

        public void setRootDirectory(String rootDirectory) {
            this.rootDirectory = rootDirectory;
        }
    }

    public static class Executor {
        private int corePoolSize = 2;
        private int maxPoolSize = 4;
        private int queueCapacity = 100;
        private String threadNamePrefix = "disclosure-review-";

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }

        public String getThreadNamePrefix() {
            return threadNamePrefix;
        }

        public void setThreadNamePrefix(String threadNamePrefix) {
            this.threadNamePrefix = threadNamePrefix;
        }
    }

    public static class Retry {
        private int maxAttempts = 3;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    public static class Rabbitmq {
        private String exchange = "review.task.exchange";
        private String stageQueue = "review.task.stage.queue";
        private String stageRoutingKey = "review.task.stage";
        private String deadLetterQueue = "review.task.stage.dlq";
        private String deadLetterExchange = "review.task.dlx";

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getStageQueue() {
            return stageQueue;
        }

        public void setStageQueue(String stageQueue) {
            this.stageQueue = stageQueue;
        }

        public String getStageRoutingKey() {
            return stageRoutingKey;
        }

        public void setStageRoutingKey(String stageRoutingKey) {
            this.stageRoutingKey = stageRoutingKey;
        }

        public String getDeadLetterQueue() {
            return deadLetterQueue;
        }

        public void setDeadLetterQueue(String deadLetterQueue) {
            this.deadLetterQueue = deadLetterQueue;
        }

        public String getDeadLetterExchange() {
            return deadLetterExchange;
        }

        public void setDeadLetterExchange(String deadLetterExchange) {
            this.deadLetterExchange = deadLetterExchange;
        }
    }

    public static class DocumentCategoryPolicy {
        private boolean autoPreferAnnouncementWhenB9Present = true;

        public boolean isAutoPreferAnnouncementWhenB9Present() {
            return autoPreferAnnouncementWhenB9Present;
        }

        public void setAutoPreferAnnouncementWhenB9Present(boolean autoPreferAnnouncementWhenB9Present) {
            this.autoPreferAnnouncementWhenB9Present = autoPreferAnnouncementWhenB9Present;
        }
    }

    public static class Sync {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
