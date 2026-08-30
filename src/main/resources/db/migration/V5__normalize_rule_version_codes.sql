alter table review_rule_version drop constraint if exists review_rule_version_version_code_key;

update review_rule_version
set version_code = 'v' || version_number
where rule_definition_id is not null
  and version_number is not null
  and version_code like '%:%'
  and not exists (
      select 1
      from review_rule_version existing
      where existing.id <> review_rule_version.id
        and existing.version_code = 'v' || review_rule_version.version_number
  );

update review_rule_definition
set version_code = (
    select 'v' || v.version_number
    from review_rule_version v
    where v.id = review_rule_definition.active_version_id
      and v.version_number is not null
)
where exists (
    select 1
    from review_rule_version v
    where v.id = review_rule_definition.active_version_id
      and v.version_number is not null
);

create unique index if not exists uk_review_rule_version_rule_version_code
    on review_rule_version (rule_definition_id, version_code);
