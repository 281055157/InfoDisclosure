-- FALSE_NEGATIVE feedback represents a missing rule and therefore has no source rule/version.
alter table rule_feedback_governance_group
    alter column rule_code drop not null;
alter table rule_feedback_governance_group
    alter column rule_version_id drop not null;
alter table rule_feedback_governance_group
    alter column rule_version drop not null;

alter table rule_feedback_governance_group
    add column if not exists governance_intent varchar(32) not null default 'RULE_CORRECTION';

update rule_feedback_governance_group
set governance_intent = case
    when feedback_type = 'FALSE_NEGATIVE' then 'RULE_GAP'
    else 'RULE_CORRECTION'
end;
