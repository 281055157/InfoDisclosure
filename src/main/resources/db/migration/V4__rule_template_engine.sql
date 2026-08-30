alter table review_rule_definition add column if not exists rule_category varchar(64);
alter table review_rule_definition add column if not exists active_version_id bigint;
alter table review_rule_definition add column if not exists priority integer not null default 100;
alter table review_rule_definition add column if not exists created_by varchar(128);
alter table review_rule_definition add column if not exists updated_by varchar(128);

alter table review_rule_version add column if not exists rule_definition_id bigint;
alter table review_rule_version add column if not exists version_number integer;
alter table review_rule_version add column if not exists executor_type varchar(64);
alter table review_rule_version add column if not exists scope_json text;
alter table review_rule_version add column if not exists condition_json text;
alter table review_rule_version add column if not exists action_json text;
alter table review_rule_version add column if not exists prompt_json text;
alter table review_rule_version add column if not exists status varchar(64);
alter table review_rule_version add column if not exists change_summary text;
alter table review_rule_version add column if not exists published_at timestamp with time zone;
alter table review_rule_version add column if not exists updated_at timestamp with time zone;

alter table review_rule_execution add column if not exists rule_id bigint;
alter table review_rule_execution add column if not exists rule_version_id bigint;
alter table review_rule_execution add column if not exists input_snapshot_json text;
alter table review_rule_execution add column if not exists result_json text;
alter table review_rule_execution add column if not exists evidence_json text;

alter table review_issue add column if not exists rule_version_id bigint;
alter table review_issue add column if not exists rule_execution_id bigint;

create index if not exists idx_review_rule_definition_active_version on review_rule_definition (active_version_id);
create index if not exists idx_review_rule_definition_priority on review_rule_definition (priority);
create index if not exists idx_review_rule_version_rule_status on review_rule_version (rule_definition_id, status);
create index if not exists idx_review_rule_execution_version on review_rule_execution (rule_version_id);
create index if not exists idx_review_issue_rule_trace on review_issue (rule_code, rule_version_id, rule_execution_id);

insert into review_rule_version
    (version_code, description, active, created_at, updated_at, rule_definition_id, version_number,
     executor_type, scope_json, condition_json, action_json, prompt_json, status, change_summary, published_at)
select 'PRODUCT_CODE_EXTRACTION:v1', 'Migrated Java plugin rule', true, current_timestamp, current_timestamp,
       d.id, 1, 'JAVA_PLUGIN',
       '{"documentCategories":[],"documentTypes":[],"productCodes":[],"productTypes":[]}',
       '{"pluginCode":"PRODUCT_CODE_EXTRACTION"}',
       '{"source":"RULE"}',
       '{}',
       'PUBLISHED', 'Initial migration', current_timestamp
from review_rule_definition d
where d.rule_code = 'PRODUCT_CODE_EXTRACTION'
  and not exists (select 1 from review_rule_version v where v.version_code = 'PRODUCT_CODE_EXTRACTION:v1');

insert into review_rule_version
    (version_code, description, active, created_at, updated_at, rule_definition_id, version_number,
     executor_type, scope_json, condition_json, action_json, prompt_json, status, change_summary, published_at)
select 'PRODUCT_NAME_EXTRACTION:v1', 'Migrated Java plugin rule', true, current_timestamp, current_timestamp,
       d.id, 1, 'JAVA_PLUGIN',
       '{"documentCategories":[],"documentTypes":[],"productCodes":[],"productTypes":[]}',
       '{"pluginCode":"PRODUCT_NAME_EXTRACTION"}',
       '{"source":"RULE"}',
       '{}',
       'PUBLISHED', 'Initial migration', current_timestamp
from review_rule_definition d
where d.rule_code = 'PRODUCT_NAME_EXTRACTION'
  and not exists (select 1 from review_rule_version v where v.version_code = 'PRODUCT_NAME_EXTRACTION:v1');

insert into review_rule_version
    (version_code, description, active, created_at, updated_at, rule_definition_id, version_number,
     executor_type, scope_json, condition_json, action_json, prompt_json, status, change_summary, published_at)
select 'DECLARED_PRODUCT_NOT_FOUND:v1', 'Migrated Java plugin rule', true, current_timestamp, current_timestamp,
       d.id, 1, 'JAVA_PLUGIN',
       '{"documentCategories":[],"documentTypes":[],"productCodes":[],"productTypes":[]}',
       '{"pluginCode":"DECLARED_PRODUCT_NOT_FOUND"}',
       '{"issueType":"DECLARED_PRODUCT_NOT_FOUND","severity":"MEDIUM","confidence":1.0,"source":"RULE","explanationTemplate":"声明产品代码未在当前模拟产品库中找到，系统无法基于产品主数据确认目标产品。","suggestionTemplate":"请确认文件名或外部传入的产品代码是否正确，或先补充对应产品库记录。"}',
       '{}',
       'PUBLISHED', 'Initial migration', current_timestamp
from review_rule_definition d
where d.rule_code = 'DECLARED_PRODUCT_NOT_FOUND'
  and not exists (select 1 from review_rule_version v where v.version_code = 'DECLARED_PRODUCT_NOT_FOUND:v1');

insert into review_rule_version
    (version_code, description, active, created_at, updated_at, rule_definition_id, version_number,
     executor_type, scope_json, condition_json, action_json, prompt_json, status, change_summary, published_at)
select 'CONTENT_LOGIC_CONFLICT:v1', 'Risk level enum mapping rule', true, current_timestamp, current_timestamp,
       d.id, 1, 'ENUM_MAPPING',
       '{"documentCategories":[],"documentTypes":["CUSTOMER_RIGHTS_NOTICE","RISK_DISCLOSURE","PRODUCT_DESCRIPTION"],"productCodes":[],"productTypes":[]}',
       '{"dataSource":"DOCUMENT_TEXT","headerPattern":"(?:风险程度|风险等级)[^.;。；]{0,120}(?:从低到高|由低到高)(?:分为|包括)五级","entryPattern":"(低风险|中低风险|中风险|中高风险|高风险)产品?\\(R([1-5])\\)","labelGroup":1,"codeGroup":2,"expectedMapping":{"R1":"低风险","R2":"中低风险","R3":"中风险","R4":"中高风险","R5":"高风险"},"checkDuplicates":true,"checkMissing":true,"checkOrder":true}',
       '{"issueType":"CONTENT_LOGIC_CONFLICT","severity":"HIGH","confidence":1.0,"source":"RULE","explanationTemplate":"正文枚举编号与名称映射存在逻辑冲突：${detail}","suggestionTemplate":"请人工核对风险等级定义、编号映射及后续风险说明是否应同步修正。"}',
       '{}',
       'PUBLISHED', 'Initial migration', current_timestamp
from review_rule_definition d
where d.rule_code = 'CONTENT_LOGIC_CONFLICT'
  and not exists (select 1 from review_rule_version v where v.version_code = 'CONTENT_LOGIC_CONFLICT:v1');

insert into review_rule_version
    (version_code, description, active, created_at, updated_at, rule_definition_id, version_number,
     executor_type, scope_json, condition_json, action_json, prompt_json, status, change_summary, published_at)
select 'CONTENT_PRODUCT_CODE_CONFLICT:v1', 'Hybrid product code conflict review', true, current_timestamp, current_timestamp,
       d.id, 1, 'HYBRID',
       '{"documentCategories":[],"documentTypes":[],"productCodes":[],"productTypes":[]}',
       '{"locator":"CONTENT_PRODUCT_CODE_CONFLICT","minConfidence":0.6}',
       '{"issueType":"CONTENT_PRODUCT_CODE_CONFLICT","severity":"MEDIUM","confidence":0.6,"source":"HYBRID","explanationTemplate":"${llmExplanation}","suggestionTemplate":"请人工确认正文是否混用了其他产品的模板内容。"}',
       '{"reviewGoal":"判断候选段落中的多产品代码是否构成目标产品文件中的异常混用。","criteria":"仅当候选段落属于核心产品信息且确实指向非目标产品时判定违规。正常引用、示例、风险说明或多产品公告不得判定违规。","responseFormat":"JSON: {\"violated\":true|false,\"confidence\":0-1,\"pageNumber\":1,\"evidenceText\":\"原文证据\",\"explanation\":\"说明\",\"suggestion\":\"建议\"}"}',
       'PUBLISHED', 'Initial migration', current_timestamp
from review_rule_definition d
where d.rule_code = 'CONTENT_PRODUCT_CODE_CONFLICT'
  and not exists (select 1 from review_rule_version v where v.version_code = 'CONTENT_PRODUCT_CODE_CONFLICT:v1');

insert into review_rule_version
    (version_code, description, active, created_at, updated_at, rule_definition_id, version_number,
     executor_type, scope_json, condition_json, action_json, prompt_json, status, change_summary, published_at)
select 'POSSIBLE_TEMPLATE_RESIDUE:v1', 'Hybrid template residue review', true, current_timestamp, current_timestamp,
       d.id, 1, 'HYBRID',
       '{"documentCategories":[],"documentTypes":[],"productCodes":[],"productTypes":[]}',
       '{"locator":"POSSIBLE_TEMPLATE_RESIDUE","minConfidence":0.85}',
       '{"issueType":"POSSIBLE_TEMPLATE_RESIDUE","severity":"HIGH","confidence":0.86,"source":"HYBRID","explanationTemplate":"${llmExplanation}","suggestionTemplate":"建议人工核查该核心字段是否应替换为声明目标产品。"}',
       '{"reviewGoal":"判断候选核心字段是否属于模板残留。","criteria":"只有核心产品信息字段出现非目标产品代码且不是示例、引用、风险说明时才判定违规。","responseFormat":"JSON: {\"violated\":true|false,\"confidence\":0-1,\"pageNumber\":1,\"evidenceText\":\"原文证据\",\"explanation\":\"说明\",\"suggestion\":\"建议\"}"}',
       'PUBLISHED', 'Initial migration', current_timestamp
from review_rule_definition d
where d.rule_code = 'POSSIBLE_TEMPLATE_RESIDUE'
  and not exists (select 1 from review_rule_version v where v.version_code = 'POSSIBLE_TEMPLATE_RESIDUE:v1');

update review_rule_definition
set rule_category = 'JAVA_PLUGIN',
    priority = 100,
    active_version_id = (select v.id from review_rule_version v where v.version_code = 'PRODUCT_CODE_EXTRACTION:v1')
where rule_code = 'PRODUCT_CODE_EXTRACTION';

update review_rule_definition
set rule_category = 'JAVA_PLUGIN',
    priority = 90,
    active_version_id = (select v.id from review_rule_version v where v.version_code = 'PRODUCT_NAME_EXTRACTION:v1')
where rule_code = 'PRODUCT_NAME_EXTRACTION';

update review_rule_definition
set rule_category = 'JAVA_PLUGIN',
    priority = 80,
    active_version_id = (select v.id from review_rule_version v where v.version_code = 'DECLARED_PRODUCT_NOT_FOUND:v1')
where rule_code = 'DECLARED_PRODUCT_NOT_FOUND';

update review_rule_definition
set rule_category = 'HARD_CONFIG',
    priority = 70,
    active_version_id = (select v.id from review_rule_version v where v.version_code = 'CONTENT_LOGIC_CONFLICT:v1')
where rule_code = 'CONTENT_LOGIC_CONFLICT';

update review_rule_definition
set rule_category = 'HYBRID',
    priority = 60,
    active_version_id = (select v.id from review_rule_version v where v.version_code = 'CONTENT_PRODUCT_CODE_CONFLICT:v1')
where rule_code = 'CONTENT_PRODUCT_CODE_CONFLICT';

update review_rule_definition
set rule_category = 'HYBRID',
    priority = 50,
    active_version_id = (select v.id from review_rule_version v where v.version_code = 'POSSIBLE_TEMPLATE_RESIDUE:v1')
where rule_code = 'POSSIBLE_TEMPLATE_RESIDUE';
