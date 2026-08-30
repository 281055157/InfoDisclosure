alter table rule_governance_tool_call
    add column if not exists tool_index integer not null default 1;
alter table rule_governance_tool_call
    add column if not exists execution_mode varchar(16) not null default 'SERIAL';
alter table rule_governance_tool_call
    add column if not exists parallel_group varchar(96);

create index if not exists idx_governance_tool_call_batch
    on rule_governance_tool_call (governance_run_id, governance_group_id, iteration_number, tool_index);
