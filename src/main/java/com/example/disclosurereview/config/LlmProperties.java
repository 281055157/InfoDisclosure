package com.example.disclosurereview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /** 是否启用 LLM 审核；未配置本地模型或外部模型服务时可关闭。 */
    private boolean enabled = true;

    /** OpenAI 兼容接口基础地址，例如 http://localhost:11434/v1 */
    private String baseUrl = "http://localhost:11434/v1";

    private String apiKey = "";

    private String model = "qwen3";

    private double temperature = 0.1;

    private Duration timeout = Duration.ofSeconds(120);

    /** 低于该字符数时全文一次调用 */
    private int maxInputChars = 30000;

    /** 分块时每块的目标字符数 */
    private int chunkChars = 12000;

    /**
     * 离线/内网部署启动配置。启用后会把 llm.base-url 和 llm.model 写入数据库模型链，
     * 避免 Flyway 中的演示公网模型覆盖环境变量。
     */
    private Bootstrap bootstrap = new Bootstrap();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeoutSeconds(long timeoutSeconds) {
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxInputChars() {
        return maxInputChars;
    }

    public void setMaxInputChars(int maxInputChars) {
        this.maxInputChars = maxInputChars;
    }

    public int getChunkChars() {
        return chunkChars;
    }

    public void setChunkChars(int chunkChars) {
        this.chunkChars = chunkChars;
    }

    public Bootstrap getBootstrap() {
        return bootstrap;
    }

    public void setBootstrap(Bootstrap bootstrap) {
        this.bootstrap = bootstrap == null ? new Bootstrap() : bootstrap;
    }

    public static class Bootstrap {
        private boolean enabled;
        private String providerCode = "intranet-default";
        private String providerType = "OPENAI_COMPATIBLE";
        private String modelCode = "intranet-primary";
        private int priority = 1000;
        private int maxRetries = 1;
        private String responseFormat = "json_object";
        /** 为空时不发送 Authorization 请求头。 */
        private String apiKeyEnv = "";
        private boolean disableOtherModels = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public String getProviderType() {
            return providerType;
        }

        public void setProviderType(String providerType) {
            this.providerType = providerType;
        }

        public String getModelCode() {
            return modelCode;
        }

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public String getResponseFormat() {
            return responseFormat;
        }

        public void setResponseFormat(String responseFormat) {
            this.responseFormat = responseFormat;
        }

        public String getApiKeyEnv() {
            return apiKeyEnv;
        }

        public void setApiKeyEnv(String apiKeyEnv) {
            this.apiKeyEnv = apiKeyEnv;
        }

        public boolean isDisableOtherModels() {
            return disableOtherModels;
        }

        public void setDisableOtherModels(boolean disableOtherModels) {
            this.disableOtherModels = disableOtherModels;
        }
    }
}
