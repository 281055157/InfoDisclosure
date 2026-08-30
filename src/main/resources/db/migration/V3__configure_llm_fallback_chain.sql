update llm_provider_config
set provider_type = 'OPENAI_COMPATIBLE',
    base_url = 'http://localhost:11434/v1',
    enabled = true,
    updated_at = current_timestamp
where provider_code = 'deepseek-default';

insert into llm_provider_config
    (provider_code, provider_type, base_url, enabled, created_at, updated_at)
select 'xiaomi-mimo', 'OPENAI_COMPATIBLE', 'http://localhost:11434/v1', true,
       current_timestamp, current_timestamp
where not exists (
    select 1 from llm_provider_config where provider_code = 'xiaomi-mimo'
);

update llm_provider_config
set provider_type = 'OPENAI_COMPATIBLE',
    base_url = 'http://localhost:11434/v1',
    enabled = true,
    updated_at = current_timestamp
where provider_code = 'xiaomi-mimo';

update llm_model_config
set provider_id = (select id from llm_provider_config where provider_code = 'deepseek-default'),
    model_name = 'deepseek-v4-flash',
    priority = 100,
    enabled = true,
    timeout_seconds = 120,
    max_retries = 1,
    temperature = 0.1,
    response_format = 'json_object',
    api_key_env = null,
    updated_at = current_timestamp
where model_code = 'deepseek-v4-flash-primary';

insert into llm_model_config
    (provider_id, model_code, model_name, priority, enabled, timeout_seconds, max_retries,
     temperature, response_format, api_key_env, created_at, updated_at)
select id, 'mimo-v2.5-fallback', 'mimo-v2.5', 90, true, 120, 1,
       0.1, 'json_object', null, current_timestamp, current_timestamp
from llm_provider_config
where provider_code = 'xiaomi-mimo'
  and not exists (
      select 1 from llm_model_config where model_code = 'mimo-v2.5-fallback'
  );

update llm_model_config
set provider_id = (select id from llm_provider_config where provider_code = 'xiaomi-mimo'),
    model_name = 'mimo-v2.5',
    priority = 90,
    enabled = true,
    timeout_seconds = 120,
    max_retries = 1,
    temperature = 0.1,
    response_format = 'json_object',
    api_key_env = null,
    updated_at = current_timestamp
where model_code = 'mimo-v2.5-fallback';
